package com.sowad.nourish.common.crypto;

import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.util.*;

/** Offline maintenance entrypoint. No Spring web server or application bootstrap. */
public final class AccountProtectionCli {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static String env(String key, String fallback) {
    String s = System.getenv(key); return s == null || s.isEmpty() ? fallback : s;
  }
  public static void main(String[] args) {
    try {
      AccountProtection protection = new AccountProtection(AccountKeyLoader.load(env("ACCOUNT_DATA_KEY", ""), env("ACCOUNT_DATA_KEY_FILE", "")));
      if (args.length != 1 || !Set.of("migrate", "verify", "protect-json").contains(args[0]))
        throw new IllegalArgumentException("Use migrate, verify or protect-json.");
      if (args[0].equals("protect-json")) {
        System.out.println(protection.transform(JSON.readTree(System.in), "migration", true));
        return;
      }
      String name = env("DB_NAME", "nourish_poltri_shop");
      if (!name.equals("nourish_poltri_shop")) throw new IllegalStateException("Wrong database name.");
      String url = "jdbc:postgresql://" + env("DB_HOST", "127.0.0.1") + ":" + env("DB_PORT", "2001") + "/" + name;
      Properties properties = new Properties();
      properties.setProperty("user", env("MIGRATION_DB_USER", env("DB_USER", "myapp_user")));
      properties.setProperty("password", env("MIGRATION_DB_PASSWORD", env("DB_PASSWORD", "")));
      try (Connection c = DriverManager.getConnection(url, properties)) {
        c.setAutoCommit(false);
        try {
          execute(c, "SET LOCAL lock_timeout='15s'");
          execute(c, "SELECT pg_advisory_xact_lock(76201901)");
          boolean applied;
          try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT to_regclass('nourish.account_crypto_state') IS NOT NULL")) {
            rs.next(); applied = rs.getBoolean(1);
          }
          if (applied) {
            verify(c, protection); c.commit();
            System.out.println("PASS: original key verified; protected fields decrypt; no changes made."); return;
          }
          if (!args[0].equals("migrate")) throw new IllegalStateException("Migration not yet applied.");
          migrate(c, protection);
          verify(c, protection);
          c.commit();
          System.out.println("PASS: account encryption migration committed; history triggers restored; key and ciphertext verified.");
        } catch (Exception e) { c.rollback(); throw e; }
      }
    } catch (Exception e) {
      // SQLException text can contain row values; do not print it or a stack trace.
      System.err.println("FAILED: no migration changes committed. Check ACCOUNT_DATA_KEY, database credentials/connectivity, schema compatibility and table-owner privileges. Stop the backend before migration. Error type: " + e.getClass().getSimpleName());
      System.exit(1);
    }
  }
  private static void execute(Connection c, String sql) throws SQLException {
    try (Statement s = c.createStatement()) { s.execute(sql); }
  }
  public static void migrate(Connection c, AccountProtection p) throws Exception {
    execute(c, "LOCK TABLE nourish.receiving_account,nourish.payment,nourish.migration_batch,nourish.migration_record,nourish.migration_mapping IN ACCESS EXCLUSIVE MODE");
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT count(*) FROM nourish.schema_version WHERE version=8")) {
      rs.next(); if (rs.getInt(1) != 1) throw new IllegalStateException("Phase 8 required.");
    }
    // Abort on schema drift instead of dropping arbitrary constraints/triggers.
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT count(*) FROM pg_constraint WHERE conrelid='nourish.receiving_account'::regclass AND conname='receiving_account_check' AND contype='c'")) {
      rs.next(); if (rs.getInt(1) != 1) throw new IllegalStateException("Unexpected receiving account schema.");
    }
    execute(c, "ALTER TABLE nourish.receiving_account DROP CONSTRAINT receiving_account_check");
    execute(c, "ALTER TABLE nourish.receiving_account ALTER COLUMN wallet_number TYPE varchar(512), ALTER COLUMN account_number TYPE varchar(512), ADD COLUMN wallet_number_lookup varchar(64), ADD COLUMN account_number_lookup varchar(64)");
    for (String table : List.of("payment", "migration_batch", "migration_record", "migration_mapping"))
      execute(c, "ALTER TABLE nourish." + table + " DISABLE TRIGGER " + table + "_immutable");

    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT id,wallet_number,account_number FROM nourish.receiving_account")) {
      while (rs.next()) {
        String id = rs.getString(1), wallet = rs.getString(2), bank = rs.getString(3);
        if ((wallet != null && !wallet.matches("01[3-9][0-9]{8}")) || (bank != null && !bank.matches("[A-Z0-9]{4,40}")))
          throw new IllegalStateException("Unexpected legacy number format.");
        try (PreparedStatement q = c.prepareStatement("UPDATE nourish.receiving_account SET wallet_number=?,account_number=?,wallet_number_lookup=?,account_number_lookup=? WHERE id=?")) {
          String ew = p.encrypt(wallet, "receiving/" + id + "/wallet"), eb = p.encrypt(bank, "receiving/" + id + "/bank");
          if (!Objects.equals(wallet, p.decrypt(ew, "receiving/" + id + "/wallet")) || !Objects.equals(bank, p.decrypt(eb, "receiving/" + id + "/bank"))) throw new IllegalStateException();
          q.setString(1, ew);q.setString(2, eb);q.setString(3, p.lookup("wallet", wallet));q.setString(4, p.lookup("bank", bank));q.setString(5, id);q.executeUpdate();
        }
      }
    }
    jsonColumn(c, p, "payment", "account_snapshot", "id", true);
    jsonColumn(c, p, "migration_batch", "report", "source_sha256", true);
    jsonColumn(c, p, "migration_record", "payload", "source_sha256,collection,ordinal", true);
    jsonColumn(c, p, "migration_record", "issues", "source_sha256,collection,ordinal", true);
    jsonColumn(c, p, "migration_mapping", "approved_command", "source_sha256,collection,ordinal", true);
    for (String table : List.of("payment", "migration_batch", "migration_record", "migration_mapping"))
      execute(c, "ALTER TABLE nourish." + table + " ENABLE TRIGGER " + table + "_immutable");

    execute(c, "DROP INDEX nourish.receiving_account_mfs_unique");
    execute(c, "DROP INDEX nourish.receiving_account_bank_unique");
    execute(c, "CREATE UNIQUE INDEX receiving_account_mfs_unique ON nourish.receiving_account(provider,wallet_number_lookup) WHERE kind='mfs'");
    execute(c, "CREATE UNIQUE INDEX receiving_account_bank_unique ON nourish.receiving_account(lower(regexp_replace(btrim(bank_name),'[[:space:]]+',' ','g')),account_number_lookup) WHERE kind='bank'");
    execute(c, """
      ALTER TABLE nourish.receiving_account ADD CONSTRAINT receiving_account_check CHECK (
      (kind='mfs' AND provider IS NOT NULL AND provider IN ('bkash','nagad')
       AND wallet_number IS NOT NULL AND wallet_number LIKE 'enc:v1:%'
       AND wallet_number_lookup IS NOT NULL AND wallet_number_lookup ~ '^[0-9a-f]{64}$'
       AND bank_name IS NULL AND account_number IS NULL AND account_name IS NULL AND account_number_lookup IS NULL)
      OR (kind='bank' AND provider IS NULL AND wallet_number IS NULL AND wallet_number_lookup IS NULL
       AND bank_name IS NOT NULL AND length(btrim(bank_name))>=2
       AND account_name IS NOT NULL AND length(btrim(account_name))>=2
       AND account_number IS NOT NULL AND account_number LIKE 'enc:v1:%'
       AND account_number_lookup IS NOT NULL AND account_number_lookup ~ '^[0-9a-f]{64}$'))
      """);
    execute(c, """
      CREATE FUNCTION nourish.account_json_protected(v jsonb) RETURNS boolean LANGUAGE plpgsql IMMUTABLE AS $fn$
      DECLARE k text; child jsonb;
      BEGIN
       IF jsonb_typeof(v)='object' THEN
        FOR k,child IN SELECT key,value FROM jsonb_each(v) LOOP
         IF k IN ('walletNumber','accountNumber','wallet_number','account_number') AND child <> 'null'::jsonb AND child <> '\"\"'::jsonb THEN
          IF jsonb_typeof(child)<>'string' OR left(child #>> '{}',7)<>'enc:v1:' THEN RETURN false; END IF;
         ELSIF NOT nourish.account_json_protected(child) THEN RETURN false; END IF;
        END LOOP;
       ELSIF jsonb_typeof(v)='array' THEN
        FOR child IN SELECT value FROM jsonb_array_elements(v) LOOP
         IF NOT nourish.account_json_protected(child) THEN RETURN false; END IF;
        END LOOP;
       END IF;
       RETURN true;
      END $fn$
      """);
    for (String[] item : List.of(new String[]{"payment","account_snapshot"}, new String[]{"migration_batch","report"}, new String[]{"migration_record","payload"}, new String[]{"migration_record","issues"}, new String[]{"migration_mapping","approved_command"}))
      execute(c, "ALTER TABLE nourish." + item[0] + " ADD CONSTRAINT " + item[0] + "_" + item[1] + "_protected CHECK(nourish.account_json_protected(" + item[1] + "))");
    execute(c, "CREATE TABLE nourish.account_crypto_state(id integer PRIMARY KEY CHECK(id=1),key_check text NOT NULL,applied_at timestamptz NOT NULL DEFAULT now())");
    try (PreparedStatement q = c.prepareStatement("INSERT INTO nourish.account_crypto_state(id,key_check) VALUES(1,?)")) {
      q.setString(1, p.encrypt("nourish-account-key-v1", "key-check"));q.executeUpdate();
    }
    // Follow the existing schema owner policy; the app only needs SELECT on this new table.
    execute(c, "DO $$ BEGIN IF EXISTS(SELECT FROM pg_roles WHERE rolname='myapp_user') THEN GRANT SELECT ON nourish.account_crypto_state TO myapp_user; END IF; END $$");
  }

  private static void jsonColumn(Connection c, AccountProtection p, String table, String col, String keys, boolean write) throws Exception {
    String[] names = keys.split(",");
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT " + keys + "," + col + "::text AS protected_json FROM nourish." + table + " WHERE " + col + " IS NOT NULL")) {
      while (rs.next()) {
        String context = table.equals("payment") ? "payment/" + rs.getString("id") : "migration";
        JsonNode original = JSON.readTree(rs.getString("protected_json"));
        if (!write) { p.transform(original, context, false); continue; }
        JsonNode encrypted = p.transform(original, context, true);
        if (!original.equals(p.transform(encrypted, context, false))) throw new IllegalStateException("Round-trip verification failed.");
        String where = String.join(" AND ", Arrays.stream(names).map(n -> n + "=?").toList());
        try (PreparedStatement q = c.prepareStatement("UPDATE nourish." + table + " SET " + col + "=?::jsonb WHERE " + where)) {
          q.setString(1, encrypted.toString());
          for (int i=0;i<names.length;i++) q.setObject(i+2, rs.getObject(names[i]));
          q.executeUpdate();
        }
      }
    }
  }
  public static void verify(Connection c, AccountProtection p) throws Exception {
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT key_check FROM nourish.account_crypto_state WHERE id=1")) {
      if (!rs.next() || !"nourish-account-key-v1".equals(p.decrypt(rs.getString(1), "key-check"))) throw new IllegalStateException("Key mismatch.");
    }
    try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT id,wallet_number,account_number,wallet_number_lookup,account_number_lookup FROM nourish.receiving_account")) {
      while (rs.next()) {
        String id=rs.getString(1), wallet=p.decrypt(rs.getString(2),"receiving/"+id+"/wallet"), bank=p.decrypt(rs.getString(3),"receiving/"+id+"/bank");
        if (!Objects.equals(rs.getString(4),p.lookup("wallet",wallet)) || !Objects.equals(rs.getString(5),p.lookup("bank",bank))) throw new IllegalStateException("Lookup mismatch.");
      }
    }
    jsonColumn(c,p,"payment","account_snapshot","id",false);
    jsonColumn(c,p,"migration_batch","report","source_sha256",false);
    jsonColumn(c,p,"migration_record","payload","source_sha256,collection,ordinal",false);
    jsonColumn(c,p,"migration_record","issues","source_sha256,collection,ordinal",false);
    jsonColumn(c,p,"migration_mapping","approved_command","source_sha256,collection,ordinal",false);
  }
}
