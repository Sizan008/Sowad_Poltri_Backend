package com.sowad.nourish.migration;

import com.sowad.nourish.common.crypto.AccountProtection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sowad.nourish.catalog.CatalogService;
import com.sowad.nourish.catalog.command.*;
import com.sowad.nourish.common.masterdata.*;
import com.sowad.nourish.common.security.CurrentUserProvider;
import com.sowad.nourish.contact.command.SaveContact;
import com.sowad.nourish.customer.CustomerService;
import com.sowad.nourish.receivingaccount.ReceivingAccountService;
import com.sowad.nourish.receivingaccount.command.SaveReceivingAccount;
import com.sowad.nourish.supplier.SupplierService;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@PreAuthorize("hasRole('admin')")
public class MigrationService {
  private final JdbcTemplate jdbc;
  private final AccountProtection protection;
  private final StrictCommandDecoder decoder;
  private final CustomerService customers;
  private final SupplierService suppliers;
  private final CatalogService catalog;
  private final ReceivingAccountService accounts;
  private final CurrentUserProvider users;
  private final ObjectMapper json;
  private final com.sowad.nourish.catalog.CatalogQueryService queries;

  public MigrationService(
      JdbcTemplate jdbc,
      StrictCommandDecoder decoder,
      CustomerService customers,
      SupplierService suppliers,
      CatalogService catalog,
      ReceivingAccountService accounts,
      CurrentUserProvider users,
      ObjectMapper json,
      com.sowad.nourish.catalog.CatalogQueryService queries, AccountProtection protection) {
    this.jdbc = jdbc;
    this.protection = protection;
    this.decoder = decoder;
    this.customers = customers;
    this.suppliers = suppliers;
    this.catalog = catalog;
    this.accounts = accounts;
    this.users = users;
    this.json = json;
    this.queries = queries;
  }

  @Transactional
  public Map<String, Object> promote(String batch, String collection, int ordinal, String body)
      throws Exception {
    MasterDataException.require(
        Set.of(
                "customers",
                "suppliers",
                "products",
                "archivedProducts",
                "units",
                "receivingAccounts")
            .contains(collection),
        "Only reviewed master records can be promoted; demo transactions are quarantined.");
    var rows =
        jdbc.queryForList(
            "SELECT payload::text AS payload,legacy_id FROM nourish.migration_record WHERE"
                + " source_sha256=? AND collection=? AND ordinal=? FOR UPDATE",
            batch,
            collection,
            ordinal);
    if (rows.isEmpty()) throw MasterDataException.missing("Staged record");
    var raw = protection.transform(json.readTree((String) rows.getFirst().get("payload")), "migration", false);
    MasterDataException.require(
        raw.isObject() && raw.hasNonNull("id"), "Source record must have an ID.");
    Long duplicates =
        jdbc.queryForObject(
            "SELECT count(*) FROM nourish.migration_record WHERE source_sha256=? AND collection=?"
                + " AND legacy_id=?",
            Long.class,
            batch,
            collection,
            rows.getFirst().get("legacy_id"));
    MasterDataException.require(
        duplicates == 1, "Duplicate source ID; resolve in a new validated source file.");
    // Decode before comparing replay, retaining duplicate-key and type validation.
    Object command =
        switch (collection) {
          case "customers", "suppliers" -> decoder.decode(body, SaveContact.class);
          case "products", "archivedProducts" -> decoder.decode(body, SaveProduct.class);
          case "units" -> decoder.decode(body, SaveUnit.class);
          default -> decoder.decode(body, SaveReceivingAccount.class);
        };
    String approved = json.writeValueAsString(command);
    var existing =
        jdbc.queryForList(
            "SELECT target_id AS id,approved_command::text AS command FROM"
                + " nourish.migration_mapping WHERE source_sha256=? AND collection=? AND ordinal=?",
            batch,
            collection,
            ordinal);
    if (!existing.isEmpty()) {
      if (!protection.transform(json.readTree((String) existing.getFirst().get("command")), "migration", false)
          .equals(json.readTree(approved)))
        throw new MasterDataException(
            409, "Record already imported with a different approved command.");
      return Map.of("id", existing.getFirst().get("id"), "replayed", true);
    }
    Map<String, Object> result =
        switch (collection) {
          case "customers" -> customers.create((SaveContact) command);
          case "suppliers" -> suppliers.create((SaveContact) command);
          case "products", "archivedProducts" -> catalog.createProduct((SaveProduct) command);
          case "units" -> catalog.createUnit((SaveUnit) command);
          default -> accounts.create((SaveReceivingAccount) command);
        };
    String id = (String) result.get("id");
    boolean archived =
        collection.equals("archivedProducts") || raw.path("archived").asBoolean(false);
    if (archived)
      switch (collection) {
        case "customers" -> customers.archive(id);
        case "suppliers" -> suppliers.archive(id);
        case "products", "archivedProducts" -> catalog.archiveProduct(id);
        case "receivingAccounts" -> accounts.deactivate(id);
        default ->
            throw new MasterDataException(
                400, "Archived units remain staged; cannot create an active replacement.");
      }
    jdbc.update(
        "INSERT INTO"
            + " nourish.migration_mapping(source_sha256,collection,ordinal,target_id,approved_command,actor)"
            + " VALUES(?,?,?,?,?::jsonb,?)",
        batch,
        collection,
        ordinal,
        id,
        protection.transform(json.readTree(approved), "migration", true).toString(),
        users.getCurrentUser().id());
    return Map.of("id", id, "replayed", false, "archived", archived);
  }

  public record Link(String targetId) {}

  @Transactional
  public Object link(String batch, String collection, int ordinal, String body) throws Exception {
    var request = decoder.decode(body, Link.class);
    MasterDataException.require(
        request.targetId() != null && request.targetId().length() <= 64, "Target ID required.");
    var staged =
        jdbc.queryForList(
            "SELECT payload::text AS payload,legacy_id FROM nourish.migration_record WHERE"
                + " source_sha256=? AND collection=? AND ordinal=? FOR UPDATE",
            batch,
            collection,
            ordinal);
    if (staged.isEmpty()) throw MasterDataException.missing("Staged record");
    var source = protection.transform(json.readTree((String) staged.getFirst().get("payload")), "migration", false);
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM nourish.migration_record WHERE source_sha256=? AND collection=?"
                + " AND legacy_id=?",
            Long.class,
            batch,
            collection,
            staged.getFirst().get("legacy_id"));
    MasterDataException.require(count == 1 && source.isObject(), "Unique source ID required.");
    List<Map<String, Object>> candidates;
    List<String> fields;
    switch (collection) {
      case "categories" -> {
        candidates = queries.categories();
        fields = List.of("id", "kind");
      }
      case "units" -> {
        candidates = queries.units();
        fields = List.of("code", "allowDecimal");
      }
      case "feedPriceCatalog" -> {
        candidates = queries.feedCatalog();
        fields =
            List.of(
                "productCode",
                "name",
                "group",
                "feedType",
                "bagSizeKg",
                "agentPricePerKg",
                "mrpPerKg",
                "mrpPerBag",
                "effectiveDate");
      }
      case "products" -> {
        MasterDataException.require(
            source.hasNonNull("feedCatalogId"),
            "Only existing official catalog products can be linked.");
        candidates = List.of(queries.product(request.targetId()));
        fields =
            List.of(
                "name",
                "sku",
                "categoryId",
                "kind",
                "unit",
                "feedCatalogId",
                "feedGroup",
                "feedType");
      }
      default ->
          throw new MasterDataException(
              400, "Only canonical catalog/master definitions can be linked.");
    }
    var target =
        candidates.stream()
            .filter(r -> request.targetId().equals(r.get("id")))
            .findFirst()
            .orElseThrow(() -> MasterDataException.missing("Target"));
    JsonNode actual = json.valueToTree(target);
    for (String field : fields) {
      JsonNode a = source.path(field), b = actual.path(field);
      boolean same =
          a.isNumber() && b.isNumber()
              ? a.decimalValue().compareTo(b.decimalValue()) == 0
              : a.equals(b);
      MasterDataException.require(same, "Catalog conflict in " + field + "; nothing overwritten.");
    }
    MasterDataException.require(
        source.path("archived").asBoolean(false) == actual.path("archived").asBoolean(false),
        "Archive status differs; preserve source in staging.");
    String command = json.writeValueAsString(Map.of("link", true, "targetId", request.targetId()));
    var existing =
        jdbc.queryForList(
            "SELECT target_id,approved_command::text AS command FROM nourish.migration_mapping"
                + " WHERE source_sha256=? AND collection=? AND ordinal=?",
            batch,
            collection,
            ordinal);
    if (!existing.isEmpty()) {
      if (!protection.transform(json.readTree((String) existing.getFirst().get("command")), "migration", false)
          .equals(json.readTree(command)))
        throw new MasterDataException(409, "Different mapping already exists.");
    } else
      jdbc.update(
          "INSERT INTO"
              + " nourish.migration_mapping(source_sha256,collection,ordinal,target_id,approved_command,actor)"
              + " VALUES(?,?,?,?,?::jsonb,?)",
          batch,
          collection,
          ordinal,
          request.targetId(),
          command,
          users.getCurrentUser().id());
    return Map.of("id", request.targetId(), "linked", true, "stockImported", false);
  }

  @Transactional(readOnly = true)
  public Object reconciliation() {
    return jdbc.queryForList(
        "SELECT * FROM nourish.reconciliation_issue ORDER BY check_name,entity_id");
  }
}
