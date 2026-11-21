package com.sowad.nourish;

import com.sowad.nourish.common.crypto.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AccountEncryptionMigrationTest {
  private final String key=Base64.getEncoder().encodeToString(new byte[32]);
  private void setup(Connection c) throws Exception {
    try (Statement s=c.createStatement()) {
      s.execute("CREATE SCHEMA nourish; CREATE TABLE nourish.schema_version(version int); INSERT INTO nourish.schema_version VALUES(8)");
      s.execute("CREATE FUNCTION nourish.immutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'immutable'; END $$");
      s.execute("""
       CREATE TABLE nourish.receiving_account(id text PRIMARY KEY,kind text,label text,active boolean,provider text,
        wallet_number varchar(11),bank_name text,account_name text,account_number varchar(40),
        CONSTRAINT receiving_account_check CHECK(kind IN ('mfs','bank')));
       CREATE UNIQUE INDEX receiving_account_mfs_unique ON nourish.receiving_account(provider,wallet_number) WHERE kind='mfs';
       CREATE UNIQUE INDEX receiving_account_bank_unique ON nourish.receiving_account(lower(bank_name),account_number) WHERE kind='bank';
       INSERT INTO nourish.receiving_account VALUES('wallet','mfs','Wallet',true,'bkash','01712345678',null,null,null),('bank','bank','Bank',true,null,null,'Example Bank','Shop','00123456');
       CREATE TABLE nourish.payment(id text PRIMARY KEY,amount numeric,account_snapshot jsonb);
       INSERT INTO nourish.payment VALUES('pay',125,'{"accountNumber":"00123456","bankName":"Example Bank"}');
       CREATE TABLE nourish.migration_batch(source_sha256 text PRIMARY KEY,report jsonb);
       INSERT INTO nourish.migration_batch VALUES('batch','{"accountNumber":"00123456"}');
       CREATE TABLE nourish.migration_record(source_sha256 text,collection text,ordinal int,payload jsonb,issues jsonb);
       INSERT INTO nourish.migration_record VALUES('batch','receivingAccounts',0,'{"walletNumber":"01712345678","archived":true}','[]');
       CREATE TABLE nourish.migration_mapping(source_sha256 text,collection text,ordinal int,approved_command jsonb);
       INSERT INTO nourish.migration_mapping VALUES('batch','receivingAccounts',0,'{"walletNumber":"01712345678"}');
       """);
      for(String t:List.of("payment","migration_batch","migration_record","migration_mapping"))
        s.execute("CREATE TRIGGER "+t+"_immutable BEFORE UPDATE OR DELETE ON nourish."+t+" FOR EACH ROW EXECUTE FUNCTION nourish.immutable()");
    }
  }
  @Test void migrationPreservesDataAndRestoresHistoryProtection() throws Exception {
    try(var pg=EmbeddedPostgres.builder().setPort(0).setServerConfig("unix_socket_directories", "").start();var c=pg.getPostgresDatabase().getConnection()) {
      setup(c);c.setAutoCommit(false);
      var p=new AccountProtection(key);AccountProtectionCli.migrate(c,p);AccountProtectionCli.verify(c,p);c.commit();
      try(var s=c.createStatement();var rs=s.executeQuery("SELECT account_snapshot->>'accountNumber',amount FROM nourish.payment WHERE id='pay'")) {
        assertTrue(rs.next());assertEquals("00123456",p.decrypt(rs.getString(1),"payment/pay/accountNumber"));assertEquals(125,rs.getInt(2));
      }
      byte[] other=new byte[32];other[0]=1;
      assertThrows(Exception.class,()->AccountProtectionCli.verify(c,new AccountProtection(Base64.getEncoder().encodeToString(other))));c.rollback();
      assertThrows(SQLException.class,()->{try(var s=c.createStatement()){s.executeUpdate("UPDATE nourish.payment SET amount=1 WHERE id='pay'");}});c.rollback();
      assertThrows(SQLException.class,()->{try(var s=c.createStatement()){s.executeUpdate("INSERT INTO nourish.payment VALUES('bad',1,'{\"accountNumber\":\"00123456\"}')");}});c.rollback();
      AccountProtectionCli.verify(c,p);
    }
  }
  @Test void failureRollsBackDdlDataAndTriggerState() throws Exception {
    try(var pg=EmbeddedPostgres.builder().setPort(0).setServerConfig("unix_socket_directories", "").start();var c=pg.getPostgresDatabase().getConnection()) {
      setup(c);
      try(var s=c.createStatement()){s.execute("CREATE TRIGGER force_failure BEFORE UPDATE ON nourish.receiving_account FOR EACH ROW EXECUTE FUNCTION nourish.immutable()");}
      c.setAutoCommit(false);assertThrows(Exception.class,()->AccountProtectionCli.migrate(c,new AccountProtection(key)));c.rollback();
      try(var s=c.createStatement();var rs=s.executeQuery("SELECT wallet_number FROM nourish.receiving_account WHERE id='wallet'")) {assertTrue(rs.next());assertEquals("01712345678",rs.getString(1));}
      try(var s=c.createStatement();var rs=s.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='nourish' AND table_name='receiving_account' AND column_name='wallet_number_lookup'")) {rs.next();assertEquals(0,rs.getInt(1));}
      try(var s=c.createStatement();var rs=s.executeQuery("SELECT tgenabled FROM pg_trigger WHERE tgname='payment_immutable'")) {rs.next();assertEquals("O",rs.getString(1));}
    }
  }
}
