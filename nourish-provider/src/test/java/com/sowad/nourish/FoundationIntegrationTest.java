package com.sowad.nourish;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sowad.nourish.authentication.TokenCodec;
import com.sowad.nourish.health.repository.SchemaVersionRepository;
import com.sowad.nourish.user.UserServiceImpl;
import com.sowad.nourish.user.command.CreateUser;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FoundationIntegrationTest {
  private static EmbeddedPostgres postgres;
  private static final String PASSWORD = "Test-only long password 123!";

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) throws Exception {
    postgres =
        EmbeddedPostgres.builder()
            .setPort(0)
            .setServerConfig("unix_socket_directories", "")
            .start();
    try (var connection = postgres.getPostgresDatabase().getConnection();
        var statement = connection.createStatement()) {
      statement.execute("CREATE DATABASE nourish_poltri_shop");
    }
    String url =
        "jdbc:postgresql://localhost:"
            + postgres.getPort()
            + "/nourish_poltri_shop?currentSchema=nourish";
    try (var connection = DriverManager.getConnection(url, "postgres", "postgres");
        var statement = connection.createStatement()) {
      statement.execute(Files.readString(Path.of("../sql/01_foundation.sql")));
      String migration = Files.readString(Path.of("../sql/02_identity_settings_audit.sql"));
      statement.execute(migration);
      statement.execute(migration);
      String catalog = Files.readString(Path.of("../sql/03_catalog.sql"));
      statement.execute(catalog);
      statement.execute(catalog);
      String phase4 = Files.readString(Path.of("../sql/04_contacts_rates_accounts.sql"));
      statement.execute(phase4);
      statement.execute(phase4);
      String phase5 = Files.readString(Path.of("../sql/05_purchase_inventory.sql"));
      statement.execute(phase5);
      statement.execute(phase5);
      String phase6 = Files.readString(Path.of("../sql/06_sales_payments_due.sql"));
      statement.execute(phase6);
      statement.execute(phase6);
      String phase8 = Files.readString(Path.of("../sql/08_migration_verification.sql"));
      String phase7 = Files.readString(Path.of("../sql/07_reports_support.sql"));
      statement.execute(phase7);
      statement.execute(phase7);
      statement.execute(phase8);
      statement.execute(phase8);
      connection.setAutoCommit(false);
      com.sowad.nourish.common.crypto.AccountProtectionCli.migrate(connection,
          new com.sowad.nourish.common.crypto.AccountProtection("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="));
      connection.commit();
    }
    properties.add("nourish.account-data-key-file", () -> "");
    properties.add("nourish.account-data-key", () -> "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    properties.add("spring.datasource.url", () -> url);
    properties.add("spring.datasource.username", () -> "postgres");
    properties.add("spring.datasource.password", () -> "postgres");
  }

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired UserServiceImpl users;
  @Autowired TokenCodec tokens;
  @Autowired SchemaVersionRepository versions;

  @BeforeAll
  void setup() {
    users.bootstrap(new CreateUser("testadmin", PASSWORD, "Test Admin", "admin"));
  }

  @AfterAll
  void close() throws Exception {
    postgres.close();
  }

  private String login(String username) throws Exception {
    var response =
        mvc.perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of("username", username, "password", PASSWORD))))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            .andReturn();
    return json.readTree(response.getResponse().getContentAsString()).get("accessToken").asText();
  }

  private void create(String username, String role, String admin) throws Exception {
    mvc.perform(
            post("/users")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(new CreateUser(username, PASSWORD, username, role))))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.role").value(role));
  }

  private String settings(String name) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "businessName",
            name,
            "ownerName",
            "Sizan",
            "phone",
            "",
            "email",
            "",
            "address",
            "",
            "invoiceFooter",
            "Thank you",
            "lowStockDefault",
            5,
            "uiLanguage",
            "en"));
  }

  @Test
  void healthAndJpaMapping() throws Exception {
    assertThat(versions.findById(4)).isPresent();
    mvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phase").value(8))
        .andExpect(jsonPath("$.schemaVersion").value(8))
        .andExpect(header().exists("X-Request-Id"));
  }

  @Test
  void bootstrapCannotRepeat() {
    assertThatThrownBy(
            () -> users.bootstrap(new CreateUser("secondadmin", PASSWORD, "Second", "admin")))
        .hasMessageContaining("already exists");
  }

  @Test
  void hashesPasswordsAndSessionTokens() throws Exception {
    String token = login("testadmin");
    String stored =
        jdbc.queryForObject(
            "SELECT password_hash FROM nourish.app_user WHERE username='testadmin'", String.class);
    assertThat(stored).startsWith("$2a$12$").doesNotContain(PASSWORD);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.auth_session WHERE token_hash=?",
                Integer.class,
                tokens.hash(token)))
        .isEqualTo(1);
    mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("testadmin"));
  }

  @Test
  void rejectsMissingInvalidExpiredAndRevokedSessions() throws Exception {
    mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
    mvc.perform(get("/auth/me").header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    String token = login("testadmin");
    mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    String expired = login("testadmin");
    jdbc.update(
        "UPDATE nourish.auth_session SET created_at=now()-interval '10"
            + " hours',expires_at=now()-interval '1 hour' WHERE token_hash=?",
        tokens.hash(expired));
    mvc.perform(get("/auth/me").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rolePermissionsAndActorSpoofing() throws Exception {
    String admin = login("testadmin");
    for (String role : List.of("operator", "viewer")) {
      create("role-" + role, role, admin);
      String token = login("role-" + role);
      mvc.perform(get("/businessSettings/business").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk());
      mvc.perform(
              put("/transactions/settings")
                  .header("Authorization", "Bearer " + token)
                  .header("X-Actor-Role", "admin")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(settings("forbidden")))
          .andExpect(status().isForbidden());
      mvc.perform(get("/activityLogs").header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
      mvc.perform(get("/users").header("Authorization", "Bearer " + token))
          .andExpect(status().isForbidden());
      mvc.perform(
              post("/users")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      json.writeValueAsString(
                          new CreateUser("illegal-" + role, PASSWORD, "Illegal", "admin"))))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            put("/transactions/settings")
                .header("Authorization", "Bearer " + admin)
                .header("X-Actor-Id", "fake-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(settings("NOURISH Test")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.businessName").value("NOURISH Test"));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.activity_log WHERE" + " actor_id='fake-user'",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.activity_log WHERE action='UPDATE'"
                    + " AND actor_id=(SELECT id FROM nourish.app_user WHERE"
                    + " username='testadmin')",
                Integer.class))
        .isGreaterThan(0);
    mvc.perform(get("/activityLogs").header("Authorization", "Bearer " + admin))
        .andExpect(status().isOk());
  }

  @Test
  void failedLoginCounterAndLockoutPersist() throws Exception {
    String admin = login("testadmin");
    create("locked-user", "viewer", admin);
    for (int i = 0; i < 5; i++)
      mvc.perform(
              post("/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"username\":\"locked-user\",\"password\":\"wrong-password\"}"))
          .andExpect(status().isUnauthorized());
    assertThat(
            jdbc.queryForObject(
                "SELECT failed_attempts FROM nourish.app_user WHERE" + " username='locked-user'",
                Integer.class))
        .isEqualTo(5);
    mvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        Map.of("username", "locked-user", "password", PASSWORD))))
        .andExpect(status().isUnauthorized());
    jdbc.update(
        "UPDATE nourish.app_user SET locked_until=now()-interval '1 minute' WHERE"
            + " username='locked-user'");
    login("locked-user");
  }

  @Test
  void userDeactivationAndRoleChangesAffectExistingSessions() throws Exception {
    String admin = login("testadmin");
    create("deactivated", "viewer", admin);
    String token = login("deactivated");
    jdbc.update("UPDATE nourish.app_user SET active=false WHERE username='deactivated'");
    mvc.perform(get("/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isUnauthorized());
    create("role-change", "admin", admin);
    String changed = login("role-change");
    jdbc.update(
        "UPDATE nourish.app_user_role SET role_code='viewer' WHERE user_id=(SELECT id FROM"
            + " nourish.app_user WHERE username='role-change')");
    mvc.perform(get("/users").header("Authorization", "Bearer " + changed))
        .andExpect(status().isForbidden());
  }

  @Test
  void validatesSettingsUserPayloadAndPagination() throws Exception {
    String admin = login("testadmin");
    mvc.perform(
            put("/transactions/settings")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.businessName").exists());
    mvc.perform(get("/activityLogs?limit=999").header("Authorization", "Bearer " + admin))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/users")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new CreateUser("shortpass", "short", "Short", "viewer"))))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/users")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    json.writeValueAsString(
                        new CreateUser("byte-limit", "অ".repeat(30), "Bytes", "viewer"))))
        .andExpect(status().isBadRequest());
  }

  @Test
  void settingsAndAuditRollbackTogether() throws Exception {
    String admin = login("testadmin");
    String original =
        jdbc.queryForObject("SELECT business_name FROM nourish.business_settings", String.class);
    jdbc.execute(
        "ALTER TABLE nourish.activity_log ADD CONSTRAINT test_reject_update CHECK(action <>"
            + " 'UPDATE') NOT VALID");
    try {
      mvc.perform(
              put("/transactions/settings")
                  .header("Authorization", "Bearer " + admin)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(settings("must rollback")))
          .andExpect(status().isConflict());
      assertThat(
              jdbc.queryForObject(
                  "SELECT business_name FROM nourish.business_settings", String.class))
          .isEqualTo(original);
    } finally {
      jdbc.execute("ALTER TABLE nourish.activity_log DROP CONSTRAINT test_reject_update");
    }
  }

  @Test
  void corsAndUnknownRoutes() throws Exception {
    mvc.perform(
            options("/auth/login")
                .header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    mvc.perform(
            options("/auth/login")
                .header("Origin", "https://unlisted.example")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/does-not-exist").header("Authorization", "Bearer " + login("testadmin")))
        .andExpect(status().isNotFound());
  }

  private Map<String, Object> feedDraft(String sku) {
    return new HashMap<>(
        Map.of(
            "name",
            "Custom Feed",
            "sku",
            sku,
            "categoryId",
            "cat-feed",
            "feedGroup",
            "Custom Group",
            "feedType",
            "Pellet"));
  }

  private String saveProduct(Map<String, Object> body, String token) throws Exception {
    var result =
        mvc.perform(
                post("/catalog/products")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.stockQuantity").value(0))
            .andExpect(jsonPath("$.costPrice").value(0))
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  @Test
  void catalogSeedAndCascades() throws Exception {
    String token = login("testadmin");
    mvc.perform(get("/categories").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(4));
    mvc.perform(get("/feedPriceCatalog").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(76));
    mvc.perform(
            get("/catalog/cascade-options?category=feed&stage=groups")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.options").isNotEmpty());
    mvc.perform(
            get("/catalog/cascade-options?category=feed&stage=products&group=Broiler%20Feed&feedType=Crumble")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    mvc.perform(
            get("/catalog/cascade-options?category=feed&stage=breeds")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/catalogOptions/product-types").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.chickGrades[1]").value("B+"));
  }

  @Test
  void productLifecycleAndProtectedFields() throws Exception {
    String token = login("testadmin");
    var body = feedDraft("LIFECYCLE");
    String id = saveProduct(body, token);
    body.put("name", "Edited Feed");
    mvc.perform(
            put("/catalog/products/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Edited Feed"));
    for (String field :
        List.of(
            "stockQuantity",
            "costPrice",
            "salePrice",
            "feedCatalogId",
            "openingQuantity",
            "archived",
            "id")) {
      var invalid = feedDraft("FORBIDDEN");
      invalid.put(field, 1);
      mvc.perform(
              post("/catalog/products")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(invalid)))
          .andExpect(status().isBadRequest());
    }
    mvc.perform(delete("/catalog/products/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    mvc.perform(
            put("/catalog/products/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
        .andExpect(status().isConflict());
    mvc.perform(
            post("/catalog/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(feedDraft("lifecycle"))))
        .andExpect(status().isConflict());
    mvc.perform(
            delete("/catalog/products/prod-nourish-3103")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict());
    mvc.perform(get("/products/missing").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void allKindsAndPackageConversions() throws Exception {
    String token = login("testadmin");
    String medicine =
        saveProduct(
            Map.of(
                "name",
                "Test Medicine",
                "sku",
                "MED-TEST",
                "categoryId",
                "cat-medicine",
                "manufacturer",
                "Test Company",
                "medicineForm",
                "liquid",
                "subtype",
                "vitamin"),
            token);
    saveProduct(
        Map.of(
            "name",
            "Test Chick",
            "sku",
            "CHICK-TEST",
            "categoryId",
            "cat-chick",
            "subtype",
            "broiler",
            "grade",
            "B+"),
        token);
    saveProduct(
        Map.of(
            "name",
            "Test Fish",
            "sku",
            "FISH-TEST",
            "categoryId",
            "cat-fish",
            "subtype",
            "tilapia"),
        token);
    mvc.perform(
            get("/catalog/cascade-options")
                .param("category", "medicine")
                .param("stage", "products")
                .param("medicineName", "Test Medicine")
                .param("manufacturer", "Test Company")
                .param("medicineForm", "liquid")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.options[0].value").value(medicine));
    String url = "/catalog/products/" + medicine + "/packaging";
    mvc.perform(
            post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1,\"unitCode\":\"l\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.sizeBase").value(1000))
        .andExpect(jsonPath("$.packageType").value("bottle"));
    mvc.perform(
            post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1000,\"unitCode\":\"ml\"}"))
        .andExpect(status().isConflict());
    mvc.perform(
            post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":1,\"unitCode\":\"kg\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get("/catalog/products/prod-nourish-3103/packaging")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(7));
  }

  @Test
  void unitManagementAndRoles() throws Exception {
    String admin = login("testadmin");
    create("catalogviewer", "viewer", admin);
    create("catalogoperator", "operator", admin);
    String viewer = login("catalogviewer");
    String operator = login("catalogoperator");
    mvc.perform(get("/products").header("Authorization", "Bearer " + viewer))
        .andExpect(status().isOk());
    mvc.perform(
            post("/catalog/products")
                .header("Authorization", "Bearer " + viewer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(feedDraft("NO-VIEWER"))))
        .andExpect(status().isForbidden());
    saveProduct(feedDraft("OPERATOR-SKU"), operator);
    var result =
        mvc.perform(
                post("/units")
                    .header("Authorization", "Bearer " + operator)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"code\":\"crate\",\"name\":\"Crate\",\"symbol\":\"cr\",\"allowDecimal\":false}"))
            .andExpect(status().isCreated())
            .andReturn();
    String id = json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(delete("/units/" + id).header("Authorization", "Bearer " + operator))
        .andExpect(status().isNoContent());
    mvc.perform(delete("/units/unit-kg").header("Authorization", "Bearer " + admin))
        .andExpect(status().isConflict());
    mvc.perform(get("/products")).andExpect(status().isUnauthorized());
  }

  @Test
  void catalogAuditRollback() throws Exception {
    String token = login("testadmin");
    jdbc.execute(
        "ALTER TABLE nourish.activity_log ADD CONSTRAINT test_catalog_rollback CHECK(entity <>"
            + " 'product') NOT VALID");
    try {
      mvc.perform(
              post("/catalog/products")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(feedDraft("ROLLBACK-SKU"))))
          .andExpect(status().isConflict());
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM nourish.product WHERE sku='ROLLBACK-SKU'", Integer.class))
          .isZero();
    } finally {
      jdbc.execute("ALTER TABLE nourish.activity_log DROP CONSTRAINT test_catalog_rollback");
    }
  }

  @Test
  void concurrentSkuAndArchiveProtection() throws Exception {
    String token = login("testadmin");
    String body = json.writeValueAsString(feedDraft("RACE-SKU"));
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var gate = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<Integer> task =
          () -> {
            gate.await();
            return mvc.perform(
                    post("/catalog/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      gate.countDown();
      assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 409);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.product WHERE sku='RACE-SKU'", Integer.class))
        .isEqualTo(1);
    var official = feedDraft("NOUR-3103");
    official.put("name", "Changed Official Name");
    mvc.perform(
            put("/catalog/products/prod-nourish-3103")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(official)))
        .andExpect(status().isConflict());
  }

  @Test
  void invalidCategoriesFormsAndPackagingLifecycle() throws Exception {
    String token = login("testadmin");
    for (var body :
        List.of(
            Map.of(
                "name",
                "Wrong Chick",
                "sku",
                "BAD-CHICK",
                "categoryId",
                "cat-chick",
                "subtype",
                "broiler",
                "grade",
                "C"),
            Map.of(
                "name",
                "Wrong Medicine",
                "sku",
                "BAD-MED",
                "categoryId",
                "cat-medicine",
                "manufacturer",
                "Company",
                "medicineForm",
                "tablet"),
            Map.of("name", "Wrong Category", "sku", "BAD-CAT", "categoryId", "cat-machinery"))) {
      mvc.perform(
              post("/catalog/products")
                  .header("Authorization", "Bearer " + token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json.writeValueAsString(body)))
          .andExpect(status().isBadRequest());
    }
    String id = saveProduct(feedDraft("PACK-LIFECYCLE"), token);
    String url = "/catalog/products/" + id + "/packaging";
    var result =
        mvc.perform(
                post(url)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"amount\":7.5,\"unitCode\":\"kg\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    String pack = json.readTree(result.getResponse().getContentAsString()).get("id").asText();
    mvc.perform(delete(url + "/" + pack).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    mvc.perform(get(url).header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$[0].active").value(false));
    mvc.perform(delete("/catalog/products/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    mvc.perform(
            post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":9,\"unitCode\":\"kg\"}"))
        .andExpect(status().isConflict());
  }

  private final java.util.concurrent.atomic.AtomicInteger contactNumber =
      new java.util.concurrent.atomic.AtomicInteger(1000);

  private Map<String, Object> contactBody(String name) {
    return new HashMap<>(
        Map.of(
            "nameEn",
            name,
            "nameBn",
            "সওদা",
            "addressEn",
            "Dhaka Road 12",
            "addressBn",
            "ঢাকা",
            "phone",
            "017" + String.format("%08d", contactNumber.incrementAndGet()),
            "status",
            "active",
            "note",
            "পরিচিত ক্রেতা"));
  }

  private com.fasterxml.jackson.databind.JsonNode submit(
      String path, String token, Object body, int expected) throws Exception {
    var result =
        mvc.perform(
                post(path)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(status().is(expected))
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  private com.fasterxml.jackson.databind.JsonNode change(
      String path, String token, Object body, int expected) throws Exception {
    var result =
        mvc.perform(
                put(path)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(status().is(expected))
            .andReturn();
    return json.readTree(result.getResponse().getContentAsString());
  }

  @Test
  void customerBilingualLifecycleAndDetails() throws Exception {
    String token = login("testadmin");
    var draft = contactBody("Customer Alpha");
    draft.put("whatsappPhone", "+8801812345678");
    var result = submit("/catalog/customers", token, draft, 201);
    String id = result.get("id").asText();
    assertThat(result.get("name").asText()).isEqualTo("Customer Alpha");
    assertThat(result.get("nameBn").asText()).isEqualTo("সওদা");
    assertThat(result.get("whatsappPhone").asText()).isEqualTo("01812345678");
    assertThat(result.get("area").asText()).isEqualTo(draft.get("addressEn"));
    String created = result.get("createdAt").asText();
    java.time.Instant.parse(created);
    draft.put("nameEn", "Updated Customer");
    draft.remove("note");
    var updated = change("/catalog/customers/" + id, token, draft, 200);
    assertThat(updated.get("createdAt").asText()).isEqualTo(created);
    assertThat(updated.get("lastNote").asText()).isEqualTo("পরিচিত ক্রেতা");
    mvc.perform(get("/customers/" + id + "/details").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionSummaryAvailable").value(true))
        .andExpect(jsonPath("$.transactionSummary.dueAmount").value(0));
    mvc.perform(delete("/customers/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    mvc.perform(get("/customers/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true))
        .andExpect(jsonPath("$.status").value("inactive"));
    change("/catalog/customers/" + id, token, draft, 409);
    mvc.perform(
            get("/customers")
                .param("q", "Updated Customer")
                .header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(
            get("/customers")
                .param("q", "Updated Customer")
                .param("archived", "true")
                .header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$[0].id").value(id));
  }

  @Test
  void supplierBilingualUpdateArchiveAndSearch() throws Exception {
    String token = login("testadmin");
    var draft = contactBody("Supplier Beta");
    var result = submit("/suppliers", token, draft, 201);
    String id = result.get("id").asText();
    draft.put("status", "inactive");
    draft.put("note", "Updated supplier note");
    change("/suppliers/" + id, token, draft, 200);
    mvc.perform(
            get("/suppliers")
                .param("q", "Supplier Beta")
                .param("status", "inactive")
                .param("limit", "1")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id));
    mvc.perform(get("/suppliers/" + id + "/details").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionSummaryAvailable").value(true));
    draft.put("status", "active");
    change("/suppliers/" + id, token, draft, 200);
    mvc.perform(delete("/suppliers/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject("SELECT note FROM nourish.supplier WHERE id=?", String.class, id))
        .isEqualTo("Updated supplier note");
    change("/suppliers/" + id, token, draft, 409);
  }

  @Test
  void normalizedPhonesRemainUniqueAfterArchive() throws Exception {
    String token = login("testadmin");
    var draft = contactBody("Phone Unique");
    String local = draft.get("phone").toString();
    draft.put("phone", "+88" + local);
    String id = submit("/catalog/customers", token, draft, 201).get("id").asText();
    draft.put("phone", local);
    submit("/catalog/customers", token, draft, 409);
    mvc.perform(delete("/customers/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    draft.put("phone", "88" + local);
    submit("/catalog/customers", token, draft, 409);
    // A party may legitimately be both a customer and a supplier.
    submit("/suppliers", token, draft, 201);
    draft.put("phone", local);
    submit("/suppliers", token, draft, 409);
  }

  @Test
  void contactPayloadValidationAndLiteralSearch() throws Exception {
    String token = login("testadmin");
    for (var bad :
        List.of(
            Map.entry("nameEn", "বাংলা"),
            Map.entry("addressEn", ""),
            Map.entry("phone", "০১৭১২৩৪৫৬৭৮"),
            Map.entry("whatsappPhone", "1234"),
            Map.entry("status", "deleted"))) {
      var body = contactBody("Invalid Customer");
      body.put(bad.getKey(), bad.getValue());
      submit("/catalog/customers", token, body, 400);
    }
    var body = contactBody("Unknown Fields");
    body.put("customerRates", List.of());
    submit("/catalog/customers", token, body, 400);
    body = contactBody("Notes Conflict");
    body.put("lastNote", "different");
    submit("/catalog/customers", token, body, 400);
    body = contactBody("Percent% Shop");
    String id = submit("/catalog/customers", token, body, 201).get("id").asText();
    mvc.perform(get("/customers").param("q", "%").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(id));
    mvc.perform(
            get("/customers").param("q", "' OR 1=1 --").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.length()").value(0));
    for (String path :
        List.of(
            "/customers?limit=0",
            "/suppliers?offset=-1",
            "/customers?status=bad",
            "/receivingAccounts?kind=bad"))
      mvc.perform(get(path).header("Authorization", "Bearer " + token))
          .andExpect(status().isBadRequest());
    mvc.perform(get("/customers/missing").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void preciseUnitAndLegacyFeedRates() throws Exception {
    String token = login("testadmin");
    String id =
        submit("/catalog/customers", token, contactBody("Rate Unit"), 201).get("id").asText();
    String med =
        saveProduct(
            Map.of(
                "name",
                "Rate Medicine",
                "sku",
                "RATE-MED",
                "categoryId",
                "cat-medicine",
                "manufacturer",
                "Medicine Company",
                "medicineForm",
                "liquid"),
            token);
    var result =
        change(
            "/customers/" + id + "/rates",
            token,
            Map.of(
                "rates",
                List.of(
                    Map.of(
                        "productId",
                        "prod-nourish-3103",
                        "pricePerKg",
                        new java.math.BigDecimal("75.123456")),
                    Map.of(
                        "productId",
                        med,
                        "pricePerUnit",
                        new java.math.BigDecimal("0.000001"),
                        "unit",
                        "ml"))),
            200);
    assertThat(result.get("customerRates").size()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT price_per_unit FROM nourish.customer_product_rate WHERE customer_id=? AND"
                    + " product_id=?",
                java.math.BigDecimal.class,
                id,
                med))
        .isEqualByComparingTo("0.000001");
    change(
        "/customers/" + id + "/rates",
        token,
        Map.of("rates", List.of(Map.of("productId", med, "pricePerKg", 4))),
        400);
    change(
        "/customers/" + id + "/rates",
        token,
        Map.of("rates", List.of(Map.of("productId", med, "pricePerUnit", 4, "unit", "kg"))),
        400);
    change(
        "/customers/" + id + "/rates",
        token,
        Map.of(
            "rates",
            List.of(
                Map.of("productId", "prod-nourish-3103", "pricePerUnit", 75, "pricePerKg", 76))),
        400);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.customer_product_rate WHERE customer_id=?",
                Integer.class,
                id))
        .isEqualTo(2);
  }

  @Test
  void packageRatesOwnershipReplacementAndClear() throws Exception {
    String token = login("testadmin");
    String id =
        submit("/catalog/customers", token, contactBody("Rate Package"), 201).get("id").asText();
    String url = "/customers/" + id + "/rates";
    var unit = Map.of("productId", "prod-nourish-3103", "pricePerUnit", 75);
    var pack =
        Map.of(
            "productId",
            "prod-nourish-3103",
            "packagingId",
            "pack-3103-25",
            "pricePerPackage",
            1800);
    var result = change(url, token, Map.of("rates", List.of(unit, pack)), 200);
    assertThat(result.get("customerRates").size()).isEqualTo(1);
    assertThat(result.get("packageRates").size()).isEqualTo(1);
    mvc.perform(get(url).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rates.length()").value(2));
    change(
        url,
        token,
        Map.of(
            "rates",
            List.of(
                unit,
                Map.of(
                    "productId",
                    "prod-nourish-3102",
                    "packagingId",
                    "pack-3103-25",
                    "pricePerPackage",
                    1800))),
        400);
    change(url, token, Map.of("rates", List.of(unit, unit)), 400);
    change(url, token, Map.of("rates", List.of(pack, pack)), 400);
    change(
        url,
        token,
        Map.of("rates", List.of(Map.of("productId", "prod-nourish-3103", "pricePerUnit", 0))),
        400);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.customer_package_rate WHERE customer_id=?",
                Integer.class,
                id))
        .isEqualTo(1);
    change(url, token, Map.of("rates", List.of()), 200);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.customer_product_rate WHERE customer_id=?",
                Integer.class,
                id))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.customer_package_rate WHERE customer_id=?",
                Integer.class,
                id))
        .isZero();
  }

  @Test
  void inactivePartyProductAndPackageCannotReceiveNewRates() throws Exception {
    String token = login("testadmin");
    var draft = contactBody("Inactive Rate");
    String id = submit("/catalog/customers", token, draft, 201).get("id").asText();
    String url = "/customers/" + id + "/rates";
    String product = saveProduct(feedDraft("RATE-ARCHIVE"), token);
    var rate = Map.of("rates", List.of(Map.of("productId", product, "pricePerUnit", 45)));
    change(url, token, rate, 200);
    draft.put("status", "inactive");
    change("/catalog/customers/" + id, token, draft, 200);
    change(url, token, rate, 409);
    draft.put("status", "active");
    change("/catalog/customers/" + id, token, draft, 200);
    mvc.perform(delete("/catalog/products/" + product).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    change(url, token, rate, 400);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.customer_product_rate WHERE customer_id=?",
                Integer.class,
                id))
        .isEqualTo(1);
    // Deactivation preserves old rates, but selection cannot use an inactive package.
    String packProduct = saveProduct(feedDraft("RATE-PACK-INACTIVE"), token);
    String packId =
        submit(
                "/catalog/products/" + packProduct + "/packaging",
                token,
                Map.of("amount", 12, "unitCode", "kg"),
                201)
            .get("id")
            .asText();
    mvc.perform(
            delete("/catalog/products/" + packProduct + "/packaging/" + packId)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    change(
        url,
        token,
        Map.of(
            "rates",
            List.of(
                Map.of("productId", packProduct, "packagingId", packId, "pricePerPackage", 100))),
        400);
  }

  @Test
  void receivingAccountLifecycleAndNormalizedDuplicates() throws Exception {
    String token = login("testadmin");
    var body =
        new HashMap<String, Object>(
            Map.of(
                "kind",
                "mfs",
                "label",
                "Shop bKash",
                "active",
                true,
                "provider",
                " bkash ",
                "walletNumber",
                "+8801712345678"));
    String id = submit("/transactions/receiving-accounts", token, body, 201).get("id").asText();
    body.put("walletNumber", "01712345678");
    submit("/transactions/receiving-accounts", token, body, 409);
    mvc.perform(
            delete("/transactions/receiving-accounts/" + id)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    submit("/transactions/receiving-accounts", token, body, 409);
    body.put("label", "Renamed bKash");
    change("/transactions/receiving-accounts/" + id, token, body, 200);
    var bank =
        new HashMap<String, Object>(
            Map.of(
                "kind",
                "bank",
                "label",
                "Shop Bank",
                "active",
                true,
                "bankName",
                "Example  Bank",
                "accountNumber",
                "0012-3456",
                "accountName",
                "Sowad Shop"));
    var result = submit("/transactions/receiving-accounts", token, bank, 201);
    String bankId = result.get("id").asText();
    assertThat(result.get("accountNumber").asText()).isEqualTo("00123456");
    assertThat(jdbc.queryForObject("SELECT account_number FROM nourish.receiving_account WHERE id=?", String.class, bankId)).startsWith("enc:v1:").doesNotContain("00123456");
    mvc.perform(get("/receivingAccounts").param("kind", "bank").param("q", "123456").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk()).andExpect(jsonPath("$[0].accountNumber").value("00123456"));
    bank.put("bankName", "example bank");
    bank.put("accountNumber", "00123456");
    submit("/transactions/receiving-accounts", token, bank, 409);
    bank.put("accountNumber", "00987654");
    change("/transactions/receiving-accounts/" + bankId, token, bank, 200);
    change("/transactions/receiving-accounts/" + id, token, bank, 409);
    mvc.perform(
            get("/receivingAccounts")
                .param("kind", "bank")
                .param("active", "true")
                .param("q", "example")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(bankId));
  }

  @Test
  void receivingAccountValidationAndRoleBoundaries() throws Exception {
    String admin = login("testadmin");
    create("phase4operator", "operator", admin);
    create("phase4viewer", "viewer", admin);
    String operator = login("phase4operator"), viewer = login("phase4viewer");
    for (String token : List.of(operator, viewer))
      for (String url : List.of("/customers", "/suppliers", "/receivingAccounts"))
        mvc.perform(get(url).header("Authorization", "Bearer " + token)).andExpect(status().isOk());
    var contact = contactBody("Role Party");
    String id = submit("/catalog/customers", operator, contact, 201).get("id").asText();
    submit("/suppliers", operator, contact, 201);
    submit("/catalog/customers", viewer, contactBody("Viewer Forbidden"), 403);
    submit("/suppliers", viewer, contactBody("Viewer Supplier"), 403);
    change("/customers/" + id + "/rates", viewer, Map.of("rates", List.of()), 403);
    change("/customers/" + id + "/rates", operator, Map.of("rates", List.of()), 200);
    var account =
        new HashMap<String, Object>(
            Map.of(
                "kind",
                "mfs",
                "label",
                "Permission wallet",
                "active",
                true,
                "provider",
                "nagad",
                "walletNumber",
                "01812345678"));
    submit("/transactions/receiving-accounts", operator, account, 403);
    submit("/transactions/receiving-accounts", viewer, account, 403);
    account.put("pin", "1234");
    submit("/transactions/receiving-accounts", admin, account, 400);
    account.remove("pin");
    account.put("provider", "unsupported");
    submit("/transactions/receiving-accounts", admin, account, 400);
    account.put("provider", "nagad");
    account.put("bankName", "Mixed Bank");
    submit("/transactions/receiving-accounts", admin, account, 400);
    submit(
        "/transactions/receiving-accounts",
        admin,
        Map.of(
            "kind",
            "bank",
            "label",
            "No Holder",
            "active",
            true,
            "bankName",
            "Test Bank",
            "accountNumber",
            "12345"),
        400);
    mvc.perform(get("/customers")).andExpect(status().isUnauthorized());
  }

  @Test
  void phase4AuditFailuresRollbackContactsAccountsAndRateReplacement() throws Exception {
    String token = login("testadmin");
    String id =
        submit("/catalog/customers", token, contactBody("Rollback Existing"), 201)
            .get("id")
            .asText();
    change(
        "/customers/" + id + "/rates",
        token,
        Map.of("rates", List.of(Map.of("productId", "prod-nourish-3103", "pricePerUnit", 70))),
        200);
    jdbc.execute(
        "ALTER TABLE nourish.activity_log ADD CONSTRAINT test_phase4_audit CHECK(entity NOT IN"
            + " ('customer','supplier','customer-rate','receiving-account')) NOT VALID");
    try {
      var c = contactBody("Must Rollback");
      submit("/catalog/customers", token, c, 409);
      submit("/suppliers", token, c, 409);
      submit(
          "/transactions/receiving-accounts",
          token,
          Map.of(
              "kind",
              "mfs",
              "label",
              "Rollback Wallet",
              "active",
              true,
              "provider",
              "nagad",
              "walletNumber",
              "01912345678"),
          409);
      change(
          "/customers/" + id + "/rates",
          token,
          Map.of("rates", List.of(Map.of("productId", "prod-nourish-3102", "pricePerUnit", 90))),
          409);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM nourish.customer WHERE name_en='Must Rollback'",
                  Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM nourish.supplier WHERE name_en='Must Rollback'",
                  Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM nourish.receiving_account WHERE label='Rollback Wallet'",
                  Integer.class))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT price_per_unit FROM nourish.customer_product_rate WHERE customer_id=? AND"
                      + " product_id='prod-nourish-3103'",
                  java.math.BigDecimal.class,
                  id))
          .isEqualByComparingTo("70");
    } finally {
      jdbc.execute("ALTER TABLE nourish.activity_log DROP CONSTRAINT test_phase4_audit");
    }
  }

  @Test
  void concurrentPhoneCreationAndRateReplacementAreConsistent() throws Exception {
    String token = login("testadmin");
    String body = json.writeValueAsString(contactBody("Concurrent Phone"));
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var gate = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<Integer> task =
          () -> {
            gate.await();
            return mvc.perform(
                    post("/catalog/customers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn()
                .getResponse()
                .getStatus();
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      gate.countDown();
      assertThat(List.of(a.get(), b.get())).containsExactlyInAnyOrder(201, 409);
      String id =
          submit("/catalog/customers", token, contactBody("Concurrent Rates"), 201)
              .get("id")
              .asText();
      var ratesA =
          Map.of(
              "rates",
              List.of(
                  Map.of("productId", "prod-nourish-3103", "pricePerUnit", 10),
                  Map.of("productId", "prod-nourish-3102", "pricePerUnit", 20)));
      var ratesB =
          Map.of("rates", List.of(Map.of("productId", "prod-nourish-3108", "pricePerUnit", 30)));
      var rateGate = new java.util.concurrent.CountDownLatch(1);
      var first =
          pool.submit(
              () -> {
                rateGate.await();
                return change("/customers/" + id + "/rates", token, ratesA, 200);
              });
      var second =
          pool.submit(
              () -> {
                rateGate.await();
                return change("/customers/" + id + "/rates", token, ratesB, 200);
              });
      rateGate.countDown();
      first.get();
      second.get();
      var ids =
          jdbc.queryForList(
              "SELECT product_id FROM nourish.customer_product_rate WHERE customer_id=? ORDER BY"
                  + " product_id",
              String.class,
              id);
      assertThat(
              ids.equals(List.of("prod-nourish-3102", "prod-nourish-3103"))
                  || ids.equals(List.of("prod-nourish-3108")))
          .isTrue();
    }
  }

  @Test
  void phase4DatabaseRelationshipsRejectInvalidMappings() throws Exception {
    String token = login("testadmin");
    String id =
        submit("/catalog/customers", token, contactBody("Foreign Key"), 201).get("id").asText();
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO"
                        + " nourish.customer_package_rate(id,customer_id,product_id,packaging_id,price_per_package)"
                        + " VALUES('bad-map',?,'prod-nourish-3102','pack-3103-25',5)",
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO"
                        + " nourish.customer_product_rate(id,customer_id,product_id,price_per_unit)"
                        + " VALUES('bad-product',?,'missing',5)",
                    id))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.schema_version WHERE version=4", Integer.class))
        .isEqualTo(1);
  }

  private com.fasterxml.jackson.databind.JsonNode stockPost(
      String path, String token, String key, Object body, int statusCode) throws Exception {
    var response =
        mvc.perform(
                post(path)
                    .header("Authorization", "Bearer " + token)
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body)))
            .andExpect(status().is(statusCode))
            .andReturn();
    return json.readTree(response.getResponse().getContentAsString());
  }

  private String stockSupplier(String token) throws Exception {
    return submit("/suppliers", token, contactBody("Inventory Supplier"), 201).get("id").asText();
  }

  private String stockProduct(String token, String kind) throws Exception {
    String sku = "INV-" + UUID.randomUUID().toString().substring(0, 20);
    if (kind.equals("feed")) return saveProduct(feedDraft(sku), token);
    if (kind.equals("medicine"))
      return saveProduct(
          Map.of(
              "name",
              "Inventory Medicine",
              "sku",
              sku,
              "categoryId",
              "cat-medicine",
              "manufacturer",
              "Inventory Company",
              "medicineForm",
              "liquid"),
          token);
    return saveProduct(
        Map.of(
            "name",
            "Inventory Pieces",
            "sku",
            sku,
            "categoryId",
            kind.equals("fish") ? "cat-fish" : "cat-chick",
            "subtype",
            kind.equals("fish") ? "tilapia" : "broiler",
            "grade",
            "A"),
        token);
  }

  private String stockPack(String token, String product, int amount, String unit) throws Exception {
    return submit(
            "/catalog/products/" + product + "/packaging",
            token,
            Map.of("amount", amount, "unitCode", unit),
            201)
        .get("id")
        .asText();
  }

  private Map<String, Object> purchaseBody(String supplier, List<?> lines) {
    return Map.of(
        "supplierId",
        supplier,
        "purchasedAt",
        "2026-11-11T06:00:00Z",
        "reference",
        "INV-TEST",
        "notes",
        "test",
        "items",
        lines);
  }

  private Map<String, Object> feedLine(String id, int count) {
    return new HashMap<>(
        Map.of(
            "productId",
            id,
            "quantity",
            count * 25,
            "packages",
            List.of(Map.of("bagSizeKg", 25, "bagCount", count)),
            "purchasePricing",
            Map.of(
                "unitPurchaseRate",
                60,
                "setGlobalSellingRate",
                false,
                "packagePrices",
                List.of())));
  }

  private java.math.BigDecimal stockQuantity(String id) {
    return jdbc.queryForObject(
        "SELECT coalesce((SELECT quantity FROM nourish.inventory_balance WHERE product_id=?),0)",
        java.math.BigDecimal.class,
        id);
  }

  @Test
  void phase5PurchaseMixedPricingSnapshotAndGlobalRate() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        product = stockProduct(token, "feed");
    stockPack(token, product, 25, "kg");
    stockPack(token, product, 50, "kg");
    var line =
        new HashMap<String, Object>(
            Map.of(
                "productId",
                product,
                "quantity",
                100,
                "packages",
                List.of(
                    Map.of("bagSizeKg", 25, "bagCount", 2), Map.of("bagSizeKg", 50, "bagCount", 1)),
                "purchasePricing",
                Map.of(
                    "unitPurchaseRate",
                    60,
                    "unitSellingRate",
                    75,
                    "setGlobalSellingRate",
                    false,
                    "packagePrices",
                    List.of(
                        Map.of(
                            "key",
                            "bag:25",
                            "purchasePricePerPackage",
                            1400,
                            "purchaseRatePerUnit",
                            70,
                            "suggestedSalePricePerPackage",
                            1700)))));
    var saved =
        stockPost(
            "/transactions/purchases",
            token,
            UUID.randomUUID().toString(),
            purchaseBody(supplier, List.of(line)),
            201);
    assertThat(saved.get("totalAmount").decimalValue()).isEqualByComparingTo("5800");
    assertThat(stockQuantity(product)).isEqualByComparingTo("100");
    var snap = saved.at("/items/0/purchasePricingSnapshot/packages/0");
    assertThat(snap.get("source").asText()).isEqualTo("package");
    assertThat(snap.get("purchaseRatePerUnit").intValue()).isEqualTo(70);
    assertThat(
            jdbc.queryForObject(
                "SELECT sale_price FROM nourish.product_price WHERE product_id=?",
                java.math.BigDecimal.class,
                product))
        .isNull();
    line.put(
        "purchasePricing",
        Map.of(
            "unitPurchaseRate",
            65,
            "unitSellingRate",
            80,
            "setGlobalSellingRate",
            true,
            "packagePrices",
            List.of()));
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(line)),
        201);
    assertThat(
            jdbc.queryForObject(
                "SELECT sale_price FROM nourish.product_price WHERE product_id=?",
                java.math.BigDecimal.class,
                product))
        .isEqualByComparingTo("80");
    var after =
        json.readTree(
            mvc.perform(
                    get("/purchases/" + saved.get("id").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(after.get("items")).isEqualTo(saved.get("items"));
    mvc.perform(get("/products/" + product).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockQuantity").value(200))
        .andExpect(jsonPath("$.salePrice").value(80));
  }

  @Test
  void phase5MedicineConversionAndPiecePurchase() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        med = stockProduct(token, "medicine"),
        fish = stockProduct(token, "fish");
    stockPack(token, med, 250, "ml");
    var medicine =
        Map.of(
            "productId",
            med,
            "quantity",
            500,
            "medicinePacks",
            List.of(
                Map.of(
                    "amount", new java.math.BigDecimal("0.25"), "unitCode", "l", "packCount", 2)),
            "purchasePricing",
            Map.of(
                "packagePrices",
                List.of(
                    Map.of(
                        "key",
                        "medicine:250",
                        "purchaseRatePerUnit",
                        800,
                        "purchaseRateUnit",
                        "l",
                        "suggestedSaleRatePerUnit",
                        1000,
                        "suggestedSaleRateUnit",
                        "l"))));
    var saved =
        stockPost(
            "/transactions/purchases",
            token,
            UUID.randomUUID().toString(),
            purchaseBody(
                supplier,
                List.of(medicine, Map.of("productId", fish, "quantity", 10, "unitPrice", 5))),
            201);
    assertThat(saved.get("totalAmount").decimalValue()).isEqualByComparingTo("450");
    assertThat(stockQuantity(med)).isEqualByComparingTo("500");
    assertThat(stockQuantity(fish)).isEqualByComparingTo("10");
    assertThat(
            saved
                .at("/items/0/purchasePricingSnapshot/packages/0/purchasePerPackage")
                .decimalValue())
        .isEqualByComparingTo("200");
  }

  @Test
  void phase5PurchaseRejectsInvalidInputsWithoutSideEffects() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        product = stockProduct(token, "feed");
    String pack = stockPack(token, product, 25, "kg");
    var line = feedLine(product, 1);
    line.put("quantity", 24);
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(line)),
        400);
    line.put("quantity", 25);
    line.put("purchasePricing", Map.of("unitPurchaseRate", 60, "setGlobalSellingRate", true));
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(line)),
        400);
    line.put(
        "purchasePricing",
        Map.of(
            "unitPurchaseRate",
            60,
            "packagePrices",
            List.of(Map.of("key", "bag:50", "purchasePricePerPackage", 3000))));
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(line)),
        400);
    line = feedLine(product, 1);
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(line, line)),
        400);
    mvc.perform(
            delete("/catalog/products/" + product + "/packaging/" + pack)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(line)),
        400);
    assertThat(stockQuantity(product)).isEqualByComparingTo("0");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.purchase WHERE supplier_id=?",
                Integer.class,
                supplier))
        .isZero();
  }

  @Test
  void phase5IdempotencyReplayAndConflict() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        product = stockProduct(token, "feed");
    stockPack(token, product, 25, "kg");
    String key = UUID.randomUUID().toString();
    var body = purchaseBody(supplier, List.of(feedLine(product, 2)));
    var a = stockPost("/transactions/purchases", token, key, body, 201);
    var b = stockPost("/transactions/purchases", token, key, body, 201);
    assertThat(a).isEqualTo(b);
    assertThat(stockQuantity(product)).isEqualByComparingTo("50");
    stockPost(
        "/transactions/purchases",
        token,
        key,
        purchaseBody(supplier, List.of(feedLine(product, 3))),
        409);
  }

  @Test
  void phase5AdjustmentRecountAndArchivePolicies() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        product = stockProduct(token, "feed");
    String pack = stockPack(token, product, 25, "kg");
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(feedLine(product, 4))),
        201);
    mvc.perform(delete("/catalog/products/" + product).header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict());
    mvc.perform(
            delete("/catalog/products/" + product + "/packaging/" + pack)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict());
    var delta =
        Map.of(
            "productId",
            product,
            "mode",
            "delta",
            "reason",
            "Damaged bag",
            "packages",
            List.of(Map.of("packagingId", pack, "count", -1)));
    stockPost("/transactions/stock-adjustments", token, UUID.randomUUID().toString(), delta, 201);
    assertThat(stockQuantity(product)).isEqualByComparingTo("75");
    var recount =
        new HashMap<String, Object>(
            Map.of(
                "productId",
                product,
                "mode",
                "recount",
                "expectedRevision",
                1,
                "reason",
                "Physical count",
                "packages",
                List.of(Map.of("packagingId", pack, "count", 2))));
    stockPost("/transactions/stock-adjustments", token, UUID.randomUUID().toString(), recount, 409);
    recount.put("expectedRevision", 2);
    stockPost("/transactions/stock-adjustments", token, UUID.randomUUID().toString(), recount, 201);
    assertThat(stockQuantity(product)).isEqualByComparingTo("50");
    recount.put("expectedRevision", 3);
    recount.put("packages", List.of(Map.of("packagingId", pack, "count", 0)));
    stockPost("/transactions/stock-adjustments", token, UUID.randomUUID().toString(), recount, 201);
    mvc.perform(delete("/catalog/products/" + product).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(quantity_change) FROM nourish.stock_movement WHERE product_id=?",
                java.math.BigDecimal.class,
                product))
        .isEqualByComparingTo("0");
  }

  @Test
  void phase5NegativeStockMissingCountsAndForeignPackagesRejected() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        product = stockProduct(token, "feed"),
        other = stockProduct(token, "feed");
    String pack = stockPack(token, product, 25, "kg"),
        otherPack = stockPack(token, other, 25, "kg");
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(feedLine(product, 1))),
        201);
    for (String id : List.of(pack, otherPack))
      stockPost(
          "/transactions/stock-adjustments",
          token,
          UUID.randomUUID().toString(),
          Map.of(
              "productId",
              product,
              "mode",
              "delta",
              "reason",
              "Invalid count",
              "packages",
              List.of(Map.of("packagingId", id, "count", -2))),
          id.equals(pack) ? 409 : 404);
    String second = stockPack(token, product, 50, "kg");
    stockPost(
        "/transactions/stock-adjustments",
        token,
        UUID.randomUUID().toString(),
        Map.of(
            "productId",
            product,
            "mode",
            "recount",
            "expectedRevision",
            1,
            "reason",
            "Incomplete count",
            "packages",
            List.of(Map.of("packagingId", second, "count", 0))),
        400);
    assertThat(stockQuantity(product)).isEqualByComparingTo("25");
  }

  @Test
  void phase5AuditFailureRollsBackPurchasePricesStockAndRequest() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        product = stockProduct(token, "feed");
    stockPack(token, product, 25, "kg");
    String key = UUID.randomUUID().toString();
    var line = feedLine(product, 1);
    line.put(
        "purchasePricing",
        Map.of("unitPurchaseRate", 60, "unitSellingRate", 80, "setGlobalSellingRate", true));
    jdbc.execute(
        "ALTER TABLE nourish.activity_log ADD CONSTRAINT test_phase5_audit"
            + " CHECK(entity<>'purchase') NOT VALID");
    try {
      stockPost("/transactions/purchases", token, key, purchaseBody(supplier, List.of(line)), 409);
    } finally {
      jdbc.execute("ALTER TABLE nourish.activity_log DROP CONSTRAINT test_phase5_audit");
    }
    assertThat(stockQuantity(product)).isEqualByComparingTo("0");
    for (String table :
        List.of("purchase_item", "stock_movement", "product_price", "inventory_package_balance"))
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM nourish." + table + " WHERE product_id=?",
                  Integer.class,
                  product))
          .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.stock_request WHERE request_key=?",
                Integer.class,
                key))
        .isZero();
    stockPost("/transactions/purchases", token, key, purchaseBody(supplier, List.of(line)), 201);
  }

  @Test
  void phase5ConcurrentPurchasesAndSameKeyAreSafe() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        product = stockProduct(token, "feed");
    stockPack(token, product, 25, "kg");
    var body = purchaseBody(supplier, List.of(feedLine(product, 1)));
    var pool = java.util.concurrent.Executors.newFixedThreadPool(4);
    try {
      var start = new java.util.concurrent.CountDownLatch(1);
      List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < 4; i++)
        futures.add(
            pool.submit(
                () -> {
                  start.await();
                  stockPost(
                      "/transactions/purchases", token, UUID.randomUUID().toString(), body, 201);
                  return null;
                }));
      start.countDown();
      for (var f : futures) f.get(30, java.util.concurrent.TimeUnit.SECONDS);
      String key = UUID.randomUUID().toString();
      var a = pool.submit(() -> stockPost("/transactions/purchases", token, key, body, 201));
      var b = pool.submit(() -> stockPost("/transactions/purchases", token, key, body, 201));
      assertThat(a.get(30, java.util.concurrent.TimeUnit.SECONDS))
          .isEqualTo(b.get(30, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
    assertThat(stockQuantity(product)).isEqualByComparingTo("125");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.stock_movement WHERE product_id=?",
                Integer.class,
                product))
        .isEqualTo(5);
  }

  @Test
  void phase5RolesAndReadQueries() throws Exception {
    String admin = login("testadmin");
    create("phase5operator", "operator", admin);
    create("phase5viewer", "viewer", admin);
    String operator = login("phase5operator"),
        viewer = login("phase5viewer"),
        supplier = stockSupplier(admin),
        product = stockProduct(admin, "fish");
    var body =
        purchaseBody(
            supplier, List.of(Map.of("productId", product, "quantity", 5, "unitPrice", 10)));
    stockPost("/transactions/purchases", viewer, UUID.randomUUID().toString(), body, 403);
    stockPost("/transactions/purchases", operator, UUID.randomUUID().toString(), body, 201);
    stockPost(
        "/transactions/stock-adjustments",
        operator,
        UUID.randomUUID().toString(),
        Map.of("productId", product, "mode", "delta", "quantity", 1, "reason", "Count fix"),
        403);
    for (String route :
        List.of(
            "/inventory?lowStock=true",
            "/inventory/" + product,
            "/purchases",
            "/stockMovements?productId=" + product,
            "/packageStocks",
            "/medicinePacks"))
      mvc.perform(get(route).header("Authorization", "Bearer " + viewer))
          .andExpect(status().isOk());
    mvc.perform(get("/inventory?limit=201").header("Authorization", "Bearer " + viewer))
        .andExpect(status().isBadRequest());
  }

  @Test
  void phase5DatabaseProtectsBalancesAndImmutableHistory() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        product = stockProduct(token, "feed");
    String pack = stockPack(token, product, 25, "kg");
    var purchase =
        stockPost(
            "/transactions/purchases",
            token,
            UUID.randomUUID().toString(),
            purchaseBody(supplier, List.of(feedLine(product, 2))),
            201);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE nourish.inventory_balance SET quantity=51 WHERE product_id=?", product))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE nourish.inventory_package_balance SET package_count=3 WHERE"
                        + " packaging_id=?",
                    pack))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE nourish.purchase SET total_amount=1 WHERE id=?",
                    purchase.get("id").asText()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM nourish.stock_movement WHERE product_id=?", product))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(stockQuantity(product)).isEqualByComparingTo("50");
  }

  @Test
  void phase5ConcurrentDeductionsCannotOversell() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        product = stockProduct(token, "fish");
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(
            supplier, List.of(Map.of("productId", product, "quantity", 5, "unitPrice", 10))),
        201);
    var body =
        Map.of(
            "productId", product, "mode", "delta", "quantity", -4, "reason", "Damaged fish count");
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.Callable<Integer> task =
        () -> {
          start.await();
          return mvc.perform(
                  post("/transactions/stock-adjustments")
                      .header("Authorization", "Bearer " + token)
                      .header("Idempotency-Key", UUID.randomUUID().toString())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(json.writeValueAsString(body)))
              .andReturn()
              .getResponse()
              .getStatus();
        };
    try {
      var a = pool.submit(task);
      var b = pool.submit(task);
      start.countDown();
      assertThat(
              List.of(
                  a.get(30, java.util.concurrent.TimeUnit.SECONDS),
                  b.get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    } finally {
      pool.shutdownNow();
    }
    assertThat(stockQuantity(product)).isEqualByComparingTo("1");
  }

  @Test
  void phase5SortedLocksSupportOppositeProductOrders() throws Exception {
    String token = login("testadmin"),
        s1 = stockSupplier(token),
        s2 = stockSupplier(token),
        p1 = stockProduct(token, "fish"),
        p2 = stockProduct(token, "chick");
    var a = Map.of("productId", p1, "quantity", 1, "unitPrice", 10);
    var b = Map.of("productId", p2, "quantity", 1, "unitPrice", 20);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      var first =
          pool.submit(
              () -> {
                start.await();
                return stockPost(
                    "/transactions/purchases",
                    token,
                    UUID.randomUUID().toString(),
                    purchaseBody(s1, List.of(a, b)),
                    201);
              });
      var second =
          pool.submit(
              () -> {
                start.await();
                return stockPost(
                    "/transactions/purchases",
                    token,
                    UUID.randomUUID().toString(),
                    purchaseBody(s2, List.of(b, a)),
                    201);
              });
      start.countDown();
      first.get(30, java.util.concurrent.TimeUnit.SECONDS);
      second.get(30, java.util.concurrent.TimeUnit.SECONDS);
    } finally {
      pool.shutdownNow();
    }
    assertThat(stockQuantity(p1)).isEqualByComparingTo("2");
    assertThat(stockQuantity(p2)).isEqualByComparingTo("2");
  }

  @Test
  void phase5AdjustmentAuditRollbackAndRetry() throws Exception {
    String token = login("testadmin"),
        product = stockProduct(token, "fish"),
        key = UUID.randomUUID().toString();
    var body =
        Map.of(
            "productId",
            product,
            "mode",
            "delta",
            "quantity",
            3,
            "reason",
            "Opening physical count");
    jdbc.execute(
        "ALTER TABLE nourish.activity_log ADD CONSTRAINT test_phase5_adjustment"
            + " CHECK(entity<>'stock_adjustment') NOT VALID");
    try {
      stockPost("/transactions/stock-adjustments", token, key, body, 409);
    } finally {
      jdbc.execute("ALTER TABLE nourish.activity_log DROP CONSTRAINT test_phase5_adjustment");
    }
    assertThat(stockQuantity(product)).isEqualByComparingTo("0");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.stock_adjustment WHERE product_id=?",
                Integer.class,
                product))
        .isZero();
    var a = stockPost("/transactions/stock-adjustments", token, key, body, 201);
    assertThat(stockPost("/transactions/stock-adjustments", token, key, body, 201)).isEqualTo(a);
    assertThat(stockQuantity(product)).isEqualByComparingTo("3");
  }

  @Test
  void phase5LowStockAndGlobalRateOptOut() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        product = stockProduct(token, "fish");
    var line =
        new HashMap<String, Object>(
            Map.of(
                "productId",
                product,
                "quantity",
                1,
                "purchasePricing",
                Map.of(
                    "unitPurchaseRate", 10, "unitSellingRate", 20, "setGlobalSellingRate", true)));
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(line)),
        201);
    line.put(
        "purchasePricing",
        Map.of("unitPurchaseRate", 11, "unitSellingRate", 30, "setGlobalSellingRate", false));
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(line)),
        201);
    assertThat(
            jdbc.queryForObject(
                "SELECT sale_price FROM nourish.product_price WHERE product_id=?",
                java.math.BigDecimal.class,
                product))
        .isEqualByComparingTo("20");
    jdbc.update("UPDATE nourish.product SET low_stock_threshold=2 WHERE id=?", product);
    mvc.perform(
            get("/inventory")
                .param("productId", product)
                .param("lowStock", "true")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].stockQuantity").value(2));
    jdbc.update("UPDATE nourish.product SET low_stock_threshold=1 WHERE id=?", product);
    mvc.perform(
            get("/inventory")
                .param("productId", product)
                .param("lowStock", "true")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void phase5InvalidMedicineUnitsFractionalCountsAndInactiveSupplier() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        product = stockProduct(token, "medicine");
    stockPack(token, product, 100, "ml");
    var line =
        new HashMap<String, Object>(
            Map.of(
                "productId",
                product,
                "quantity",
                100,
                "medicinePacks",
                List.of(Map.of("amount", 100, "unitCode", "ml", "packCount", 1)),
                "purchasePricing",
                Map.of(
                    "packagePrices",
                    List.of(
                        Map.of(
                            "key",
                            "medicine:100",
                            "purchaseRatePerUnit",
                            100,
                            "purchaseRateUnit",
                            "kg")))));
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(line)),
        400);
    line.put("purchasePricing", Map.of("unitPurchaseRate", 1));
    line.put("medicinePacks", List.of(Map.of("amount", 100, "unitCode", "ml", "packCount", 1.5)));
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(line)),
        400);
    line.put("medicinePacks", List.of(Map.of("amount", 100, "unitCode", "ml", "packCount", 1)));
    jdbc.update("UPDATE nourish.supplier SET status='inactive' WHERE id=?", supplier);
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(line)),
        409);
    assertThat(stockQuantity(product)).isEqualByComparingTo("0");
  }

  private Map<String, String> saleFixture(String token, String kind, int count) throws Exception {
    String
        customer =
            submit("/catalog/customers", token, contactBody("Invoice Customer"), 201)
                .get("id")
                .asText(),
        product = stockProduct(token, kind),
        supplier = stockSupplier(token);
    String pack = "";
    Map<String, Object> line = new HashMap<>();
    line.put("productId", product);
    if (kind.equals("feed")) {
      pack = stockPack(token, product, 25, "kg");
      line.put("quantity", count * 25);
      line.put("packages", List.of(Map.of("bagSizeKg", 25, "bagCount", count)));
    } else if (kind.equals("medicine")) {
      pack = stockPack(token, product, 250, "ml");
      line.put("quantity", count * 250);
      line.put(
          "medicinePacks", List.of(Map.of("amount", 250, "unitCode", "ml", "packCount", count)));
    } else line.put("quantity", count);
    line.put(
        "purchasePricing",
        Map.of("unitPurchaseRate", 10, "unitSellingRate", 20, "setGlobalSellingRate", true));
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(supplier, List.of(line)),
        201);
    return Map.of("customer", customer, "product", product, "pack", pack);
  }

  private Map<String, Object> saleLine(Map<String, String> f, String kind, int count) {
    var line = new HashMap<String, Object>();
    line.put("productId", f.get("product"));
    if (kind.equals("feed"))
      line.put("packages", List.of(Map.of("bagSizeKg", 25, "bagCount", count)));
    else if (kind.equals("medicine"))
      line.put(
          "medicinePacks", List.of(Map.of("amount", 250, "unitCode", "ml", "packCount", count)));
    else line.put("quantity", count);
    return line;
  }

  private Map<String, Object> saleBody(Map<String, String> f, String kind, int count) {
    return new HashMap<>(
        Map.of(
            "customerId",
            f.get("customer"),
            "issuedAt",
            "2026-11-11T07:00:00Z",
            "dueDate",
            "2026-11-20T07:00:00Z",
            "items",
            List.of(saleLine(f, kind, count))));
  }

  private Map<String, Object> payBody(String invoice, int amount) {
    return new HashMap<>(
        Map.of(
            "invoiceId",
            invoice,
            "amount",
            amount,
            "paidAt",
            "2026-11-11T08:00:00Z",
            "method",
            "cash",
            "note",
            "Invoice collection"));
  }

  private com.fasterxml.jackson.databind.JsonNode readApi(String path, String token)
      throws Exception {
    return json.readTree(
        mvc.perform(get(path).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  @Test
  void phase6FullPurchaseSalePartialFinalCollection() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "feed", 4);
    var body = saleBody(f, "feed", 2);
    body.put("discountAmount", 100);
    body.put("initialPaid", 200);
    body.put("paymentMethod", "cash");
    var invoice = stockPost("/transactions/sales", token, UUID.randomUUID().toString(), body, 201);
    String id = invoice.get("id").asText();
    assertThat(invoice.get("subtotal").decimalValue()).isEqualByComparingTo("1000");
    assertThat(invoice.get("totalAmount").decimalValue()).isEqualByComparingTo("900");
    assertThat(invoice.get("dueAmount").decimalValue()).isEqualByComparingTo("700");
    assertThat(stockQuantity(f.get("product"))).isEqualByComparingTo("50");
    assertThat(invoice.get("invoiceNumber").asText()).startsWith("INV-2026-");
    stockPost("/transactions/payments", token, UUID.randomUUID().toString(), payBody(id, 300), 201);
    assertThat(readApi("/invoices/" + id, token).get("dueAmount").decimalValue())
        .isEqualByComparingTo("400");
    stockPost("/transactions/payments", token, UUID.randomUUID().toString(), payBody(id, 400), 201);
    assertThat(readApi("/invoices/" + id, token).get("status").asText()).isEqualTo("paid");
    var due = readApi("/customers/" + f.get("customer") + "/due", token);
    assertThat(due.get("billCategory").asText()).isEqualTo("paid");
    assertThat(due.get("buyingCategoryIds").get(0).asText()).isEqualTo("cat-feed");
    var ledger = readApi("/customers/" + f.get("customer") + "/ledger", token);
    assertThat(ledger.get("entries").size()).isEqualTo(4);
    assertThat(ledger.at("/entries/3/runningDue").decimalValue()).isEqualByComparingTo("0");
    mvc.perform(get("/invoices/" + id + "/download").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(
            header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".json")));
  }

  @Test
  void phase6CustomerPackageRatePrecedesUnitAndGlobal() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "feed", 5);
    change(
        "/customers/" + f.get("customer") + "/rates",
        token,
        Map.of(
            "rates",
            List.of(
                Map.of("productId", f.get("product"), "pricePerUnit", 30),
                Map.of(
                    "productId",
                    f.get("product"),
                    "packagingId",
                    f.get("pack"),
                    "pricePerPackage",
                    600))),
        200);
    var invoice =
        stockPost(
            "/transactions/sales",
            token,
            UUID.randomUUID().toString(),
            saleBody(f, "feed", 1),
            201);
    assertThat(invoice.get("totalAmount").decimalValue()).isEqualByComparingTo("600");
    assertThat(invoice.at("/items/0/rateSnapshot/0/source").asText()).isEqualTo("customer-package");
    change(
        "/customers/" + f.get("customer") + "/rates",
        token,
        Map.of("rates", List.of(Map.of("productId", f.get("product"), "pricePerUnit", 30))),
        200);
    var next =
        stockPost(
            "/transactions/sales",
            token,
            UUID.randomUUID().toString(),
            saleBody(f, "feed", 1),
            201);
    assertThat(next.get("totalAmount").decimalValue()).isEqualByComparingTo("750");
    assertThat(next.at("/items/0/rateSnapshot/0/source").asText()).isEqualTo("customer-unit");
    assertThat(readApi("/invoices/" + invoice.get("id").asText(), token).get("items"))
        .isEqualTo(invoice.get("items"));
  }

  @Test
  void phase6MedicineAndPieceStockDeduction() throws Exception {
    String token = login("testadmin");
    for (String kind : List.of("medicine", "chick", "fish")) {
      var f = saleFixture(token, kind, 3);
      stockPost(
          "/transactions/sales", token, UUID.randomUUID().toString(), saleBody(f, kind, 2), 201);
      assertThat(stockQuantity(f.get("product")))
          .isEqualByComparingTo(kind.equals("medicine") ? "250" : "1");
    }
  }

  @Test
  void phase6BankAndMfsSnapshotsSurviveAccountChanges() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 100);
    String invoice =
        stockPost(
                "/transactions/sales",
                token,
                UUID.randomUUID().toString(),
                saleBody(f, "fish", 10),
                201)
            .get("id")
            .asText();
    var bankBody =
        new HashMap<String, Object>(
            Map.of(
                "kind",
                "bank",
                "label",
                "Invoice Bank",
                "bankName",
                "Invoice Bank",
                "accountName",
                "Shop",
                "accountNumber",
                "001234567",
                "active",
                true));
    String bank =
        submit("/transactions/receiving-accounts", token, bankBody, 201).get("id").asText();
    var payment = payBody(invoice, 50);
    payment.put("method", "bank");
    payment.put("receivingAccountId", bank);
    payment.put("transactionRef", "BANK-" + UUID.randomUUID());
    var saved =
        stockPost("/transactions/payments", token, UUID.randomUUID().toString(), payment, 201);
    assertThat(saved.at("/receivingAccountSnapshot/accountNumber").asText()).isEqualTo("001234567");
    assertThat(jdbc.queryForObject("SELECT account_snapshot->>'accountNumber' FROM nourish.payment WHERE id=?", String.class, saved.get("id").asText())).startsWith("enc:v1:").doesNotContain("001234567");
    bankBody.put("label", "Renamed Bank");
    change("/transactions/receiving-accounts/" + bank, token, bankBody, 200);
    mvc.perform(
            delete("/transactions/receiving-accounts/" + bank)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    assertThat(
            readApi("/payments/" + saved.get("id").asText(), token).get("receivingAccountSnapshot"))
        .isEqualTo(saved.get("receivingAccountSnapshot"));
    stockPost("/transactions/payments", token, UUID.randomUUID().toString(), payment, 400);
    String wallet =
        submit(
                "/transactions/receiving-accounts",
                token,
                Map.of(
                    "kind",
                    "mfs",
                    "label",
                    "Invoice Wallet",
                    "provider",
                    "nagad",
                    "walletNumber",
                    "019" + String.format("%08d", contactNumber.incrementAndGet()),
                    "active",
                    true),
                201)
            .get("id")
            .asText();
    payment.put("method", "mobile-banking");
    payment.put("receivingAccountId", wallet);
    payment.put("paymentProvider", "nagad");
    payment.put("senderNumber", "+8801712345678");
    payment.put("transactionRef", "MFS-" + UUID.randomUUID());
    var mfs =
        stockPost("/transactions/payments", token, UUID.randomUUID().toString(), payment, 201);
    assertThat(mfs.at("/receivingAccountSnapshot/provider").asText()).isEqualTo("nagad");
    assertThat(mfs.get("senderNumber").asText()).isEqualTo("01712345678");
    stockPost("/transactions/payments", token, UUID.randomUUID().toString(), payment, 409);
  }

  @Test
  void phase6DuplicatesAndOverpaymentRejected() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 10);
    String key = UUID.randomUUID().toString();
    var body = saleBody(f, "fish", 2);
    var a = stockPost("/transactions/sales", token, key, body, 201);
    assertThat(stockPost("/transactions/sales", token, key, body, 201)).isEqualTo(a);
    assertThat(stockQuantity(f.get("product"))).isEqualByComparingTo("8");
    stockPost("/transactions/sales", token, key, saleBody(f, "fish", 3), 409);
    String payKey = UUID.randomUUID().toString(), id = a.get("id").asText();
    var payment = payBody(id, 30);
    var p1 = stockPost("/transactions/payments", token, payKey, payment, 201);
    assertThat(stockPost("/transactions/payments", token, payKey, payment, 201)).isEqualTo(p1);
    stockPost("/transactions/payments", token, UUID.randomUUID().toString(), payBody(id, 11), 409);
    assertThat(readApi("/invoices/" + id, token).get("dueAmount").decimalValue())
        .isEqualByComparingTo("10");
  }

  @Test
  void phase6ConcurrentPaymentsCannotOverpay() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 5);
    String id =
        stockPost(
                "/transactions/sales",
                token,
                UUID.randomUUID().toString(),
                saleBody(f, "fish", 5),
                201)
            .get("id")
            .asText();
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.Callable<Integer> task =
        () -> {
          start.await();
          return mvc.perform(
                  post("/transactions/payments")
                      .header("Authorization", "Bearer " + token)
                      .header("Idempotency-Key", UUID.randomUUID().toString())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(json.writeValueAsString(payBody(id, 60))))
              .andReturn()
              .getResponse()
              .getStatus();
        };
    try {
      var a = pool.submit(task);
      var b = pool.submit(task);
      start.countDown();
      assertThat(
              List.of(
                  a.get(30, java.util.concurrent.TimeUnit.SECONDS),
                  b.get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    } finally {
      pool.shutdownNow();
    }
    assertThat(readApi("/invoices/" + id, token).get("dueAmount").decimalValue())
        .isEqualByComparingTo("40");
  }

  @Test
  void phase6ConcurrentSalesCannotOversell() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "feed", 1);
    String second =
        submit("/catalog/customers", token, contactBody("Other Buyer"), 201).get("id").asText();
    var first = saleBody(f, "feed", 1);
    var other = saleBody(f, "feed", 1);
    other.put("customerId", second);
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      List<java.util.concurrent.Future<Integer>> jobs = new ArrayList<>();
      for (var body : List.of(first, other))
        jobs.add(
            pool.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          post("/transactions/sales")
                              .header("Authorization", "Bearer " + token)
                              .header("Idempotency-Key", UUID.randomUUID().toString())
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(json.writeValueAsString(body)))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      start.countDown();
      assertThat(
              List.of(
                  jobs.get(0).get(30, java.util.concurrent.TimeUnit.SECONDS),
                  jobs.get(1).get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(201, 409);
    } finally {
      pool.shutdownNow();
    }
    assertThat(stockQuantity(f.get("product"))).isEqualByComparingTo("0");
  }

  @Test
  void phase6InvoiceAuditFailureRollsBackInitialPaymentAndStock() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "feed", 2);
    String key = UUID.randomUUID().toString();
    var body = saleBody(f, "feed", 1);
    body.put("initialPaid", 100);
    body.put("paymentMethod", "cash");
    jdbc.execute(
        "ALTER TABLE nourish.activity_log ADD CONSTRAINT test_phase6_invoice"
            + " CHECK(entity<>'invoice') NOT VALID");
    try {
      stockPost("/transactions/sales", token, key, body, 409);
    } finally {
      jdbc.execute("ALTER TABLE nourish.activity_log DROP CONSTRAINT test_phase6_invoice");
    }
    assertThat(stockQuantity(f.get("product"))).isEqualByComparingTo("50");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.invoice WHERE customer_id=?",
                Integer.class,
                f.get("customer")))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.payment WHERE customer_id=?",
                Integer.class,
                f.get("customer")))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.stock_request WHERE request_key=?",
                Integer.class,
                key))
        .isZero();
    stockPost("/transactions/sales", token, key, body, 201);
  }

  @Test
  void phase6PaymentAuditFailureRollsBackDueAndRequest() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 5);
    String
        id =
            stockPost(
                    "/transactions/sales",
                    token,
                    UUID.randomUUID().toString(),
                    saleBody(f, "fish", 2),
                    201)
                .get("id")
                .asText(),
        key = UUID.randomUUID().toString();
    jdbc.execute(
        "ALTER TABLE nourish.activity_log ADD CONSTRAINT test_phase6_payment"
            + " CHECK(entity<>'payment') NOT VALID");
    try {
      stockPost("/transactions/payments", token, key, payBody(id, 10), 409);
    } finally {
      jdbc.execute("ALTER TABLE nourish.activity_log DROP CONSTRAINT test_phase6_payment");
    }
    assertThat(readApi("/invoices/" + id, token).get("dueAmount").decimalValue())
        .isEqualByComparingTo("40");
    stockPost("/transactions/payments", token, key, payBody(id, 10), 201);
  }

  @Test
  void phase6ValidationMissingRateInsufficientPackAndStalePrice() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "feed", 2);
    var body = saleBody(f, "feed", 3);
    stockPost("/transactions/sales", token, UUID.randomUUID().toString(), body, 409);
    body = saleBody(f, "feed", 1);
    var line = saleLine(f, "feed", 1);
    line.put("unitPrice", 19);
    body.put("items", List.of(line));
    stockPost("/transactions/sales", token, UUID.randomUUID().toString(), body, 409);
    jdbc.update(
        "UPDATE nourish.product_price SET sale_price=NULL WHERE product_id=?", f.get("product"));
    var invoice = stockPost(
        "/transactions/sales", token, UUID.randomUUID().toString(), saleBody(f, "feed", 1), 201);
    assertThat(invoice.get("totalAmount").decimalValue()).isEqualByComparingTo("500");
    assertThat(stockQuantity(f.get("product"))).isEqualByComparingTo("25");

    // A cost-only feed purchase supplies no selling rate.
    String product = stockProduct(token, "feed");
    stockPack(token, product, 25, "kg");
    var missingRate = Map.of("customer", f.get("customer"), "product", product);
    var costOnly = saleLine(missingRate, "feed", 1);
    costOnly.put("purchasePricing", Map.of("unitPurchaseRate", 10));
    stockPost("/transactions/purchases", token, UUID.randomUUID().toString(),
        purchaseBody(stockSupplier(token), List.of(costOnly)), 201);
    jdbc.update("UPDATE nourish.product_price SET sale_price=NULL WHERE product_id=?", product);
    stockPost("/transactions/sales", token, UUID.randomUUID().toString(),
        saleBody(missingRate, "feed", 1), 409);
    assertThat(stockQuantity(product)).isEqualByComparingTo("25");
  }

  @Test
  void phase6ArchivedCustomerCanPayExistingDueButCannotBuy() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 5);
    String id =
        stockPost(
                "/transactions/sales",
                token,
                UUID.randomUUID().toString(),
                saleBody(f, "fish", 1),
                201)
            .get("id")
            .asText();
    mvc.perform(
            delete("/customers/" + f.get("customer")).header("Authorization", "Bearer " + token))
        .andExpect(status().isNoContent());
    stockPost(
        "/transactions/sales", token, UUID.randomUUID().toString(), saleBody(f, "fish", 1), 409);
    stockPost("/transactions/payments", token, UUID.randomUUID().toString(), payBody(id, 20), 201);
    assertThat(
            readApi("/customers/" + f.get("customer") + "/details", token)
                .at("/transactionSummary/billCategory")
                .asText())
        .isEqualTo("paid");
  }

  @Test
  void phase6RolesReadFiltersAndNoInvoiceCategory() throws Exception {
    String admin = login("testadmin");
    create("phase6viewer", "viewer", admin);
    create("phase6operator", "operator", admin);
    String viewer = login("phase6viewer"), operator = login("phase6operator");
    var f = saleFixture(admin, "fish", 5);
    assertThat(
            readApi("/customers/" + f.get("customer") + "/due", viewer)
                .get("billCategory")
                .asText())
        .isEqualTo("no-invoices");
    stockPost(
        "/transactions/sales", viewer, UUID.randomUUID().toString(), saleBody(f, "fish", 1), 403);
    var invoice =
        stockPost(
            "/transactions/sales",
            operator,
            UUID.randomUUID().toString(),
            saleBody(f, "fish", 1),
            201);
    stockPost(
        "/transactions/payments",
        viewer,
        UUID.randomUUID().toString(),
        payBody(invoice.get("id").asText(), 10),
        403);
    for (String path :
        List.of(
            "/invoices?customerId=" + f.get("customer") + "&status=unpaid",
            "/customerInsights?billCategory=due&buyingCategoryId=cat-fish",
            "/payments?customerId=" + f.get("customer"),
            "/stockMovements?type=sale")) readApi(path, viewer);
  }

  @Test
  void phase6DatabaseProtectsFinancialHistoryAndOverpayment() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 2);
    String id =
        stockPost(
                "/transactions/sales",
                token,
                UUID.randomUUID().toString(),
                saleBody(f, "fish", 1),
                201)
            .get("id")
            .asText();
    var payment =
        stockPost(
            "/transactions/payments", token, UUID.randomUUID().toString(), payBody(id, 20), 201);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE nourish.invoice SET notes='changed' WHERE id=?", id))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM nourish.payment WHERE id=?", payment.get("id").asText()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO"
                        + " nourish.payment(id,invoice_id,customer_id,amount,paid_at,method,actor)"
                        + " SELECT ?,invoice_id,customer_id,1,paid_at,'cash',actor FROM"
                        + " nourish.payment WHERE id=?",
                    UUID.randomUUID().toString(),
                    payment.get("id").asText()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void phase6InvalidInitialPaymentAccountRollsBackInvoiceStock() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "feed", 2);
    var body = saleBody(f, "feed", 1);
    body.put("initialPaid", 100);
    body.put("paymentMethod", "bank");
    body.put("receivingAccountId", "missing-account");
    String key = UUID.randomUUID().toString();
    stockPost("/transactions/sales", token, key, body, 400);
    assertThat(stockQuantity(f.get("product"))).isEqualByComparingTo("50");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.invoice WHERE customer_id=?",
                Integer.class,
                f.get("customer")))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.stock_request WHERE request_key=?",
                Integer.class,
                key))
        .isZero();
  }

  @Test
  void phase6RejectsDiscountDatesCashMetadataAndFractionalPieces() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 5);
    var body = saleBody(f, "fish", 1);
    body.put("discountAmount", 20);
    stockPost("/transactions/sales", token, UUID.randomUUID().toString(), body, 400);
    body.remove("discountAmount");
    body.put("initialPaid", 21);
    stockPost("/transactions/sales", token, UUID.randomUUID().toString(), body, 400);
    body.remove("initialPaid");
    body.put("dueDate", "2026-11-10T07:00:00Z");
    stockPost("/transactions/sales", token, UUID.randomUUID().toString(), body, 400);
    var bad = saleBody(f, "fish", 1);
    bad.put("items", List.of(Map.of("productId", f.get("product"), "quantity", 1.5)));
    stockPost("/transactions/sales", token, UUID.randomUUID().toString(), bad, 400);
    String id =
        stockPost(
                "/transactions/sales",
                token,
                UUID.randomUUID().toString(),
                saleBody(f, "fish", 1),
                201)
            .get("id")
            .asText();
    var payment = payBody(id, 10);
    payment.put("paidAt", "2026-11-10T07:00:00Z");
    stockPost("/transactions/payments", token, UUID.randomUUID().toString(), payment, 400);
    payment = payBody(id, 10);
    payment.put("receivingAccountId", "some-bank");
    stockPost("/transactions/payments", token, UUID.randomUUID().toString(), payment, 400);
    assertThat(readApi("/invoices/" + id, token).get("dueAmount").decimalValue())
        .isEqualByComparingTo("20");
  }

  @Test
  void phase6CustomerSnapshotAndAccountKindValidation() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 5);
    var invoice =
        stockPost(
            "/transactions/sales",
            token,
            UUID.randomUUID().toString(),
            saleBody(f, "fish", 2),
            201);
    String id = invoice.get("id").asText();
    jdbc.update(
        "UPDATE nourish.customer SET name_en='New Buyer Name',address_en='New Address' WHERE id=?",
        f.get("customer"));
    assertThat(readApi("/invoices/" + id, token).get("customerSnapshot"))
        .isEqualTo(invoice.get("customerSnapshot"));
    String wallet =
        submit(
                "/transactions/receiving-accounts",
                token,
                Map.of(
                    "kind",
                    "mfs",
                    "label",
                    "Validation Wallet",
                    "provider",
                    "bkash",
                    "walletNumber",
                    "019" + String.format("%08d", contactNumber.incrementAndGet()),
                    "active",
                    true),
                201)
            .get("id")
            .asText();
    var payment = payBody(id, 10);
    payment.put("method", "bank");
    payment.put("receivingAccountId", wallet);
    stockPost("/transactions/payments", token, UUID.randomUUID().toString(), payment, 400);
    payment.put("method", "mobile-banking");
    payment.put("paymentProvider", "nagad");
    stockPost("/transactions/payments", token, UUID.randomUUID().toString(), payment, 400);
  }

  @Test
  void phase6SpecificPackageShortageEvenWhenTotalQuantityIsEnough() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "feed", 2);
    stockPack(token, f.get("product"), 50, "kg");
    var body = saleBody(f, "feed", 1);
    body.put(
        "items",
        List.of(
            Map.of(
                "productId",
                f.get("product"),
                "packages",
                List.of(Map.of("bagSizeKg", 50, "bagCount", 1)))));
    stockPost("/transactions/sales", token, UUID.randomUUID().toString(), body, 409);
    assertThat(stockQuantity(f.get("product"))).isEqualByComparingTo("50");
  }

  @Test
  void phase6ConcurrentIdenticalPaymentKeyOnlyCollectsOnce() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 5);
    String
        id =
            stockPost(
                    "/transactions/sales",
                    token,
                    UUID.randomUUID().toString(),
                    saleBody(f, "fish", 2),
                    201)
                .get("id")
                .asText(),
        key = UUID.randomUUID().toString();
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      java.util.concurrent.Callable<com.fasterxml.jackson.databind.JsonNode> task =
          () -> {
            start.await();
            return stockPost("/transactions/payments", token, key, payBody(id, 10), 201);
          };
      var a = pool.submit(task);
      var b = pool.submit(task);
      start.countDown();
      assertThat(a.get(30, java.util.concurrent.TimeUnit.SECONDS))
          .isEqualTo(b.get(30, java.util.concurrent.TimeUnit.SECONDS));
    } finally {
      pool.shutdownNow();
    }
    assertThat(readApi("/invoices/" + id, token).get("dueAmount").decimalValue())
        .isEqualByComparingTo("30");
  }

  private Map<String, Object> expenseBody(String title, int amount) {
    return new HashMap<>(
        Map.of(
            "title",
            title,
            "category",
            "transport",
            "method",
            "cash",
            "amount",
            amount,
            "spentAt",
            "2026-11-11T07:00:00Z",
            "reference",
            "Expense reference",
            "note",
            "দোকানের খরচ"));
  }

  @Test
  void phase7ExpenseCrudHistoryAndReportExclusion() throws Exception {
    String token = login("testadmin"), key = UUID.randomUUID().toString();
    var body = expenseBody("Transport " + UUID.randomUUID(), 125);
    var saved = stockPost("/transactions/expenses", token, key, body, 201);
    String id = saved.get("id").asText();
    assertThat(stockPost("/transactions/expenses", token, key, body, 201).get("id"))
        .isEqualTo(saved.get("id"));
    body.put("amount", 150);
    stockPost("/transactions/expenses", token, key, body, 409);
    body.put("expectedRevision", 0);
    var changed = change("/transactions/expenses/" + id, token, body, 200);
    assertThat(changed.get("revision").asInt()).isEqualTo(1);
    change("/transactions/expenses/" + id, token, body, 409);
    var history = readApi("/expenses/" + id + "/history", token);
    assertThat(history.size()).isEqualTo(2);
    assertThat(history.at("/0/snapshot/amount").decimalValue()).isEqualByComparingTo("125");
    assertThat(history.at("/1/snapshot/amount").decimalValue()).isEqualByComparingTo("150");
    mvc.perform(
            delete("/transactions/expenses/" + id)
                .param("expectedRevision", "1")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(true));
    mvc.perform(get("/expenses/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    assertThat(readApi("/expenses/" + id + "/history", token).size()).isEqualTo(3);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE nourish.expense_history SET action='UPDATE' WHERE expense_id=?", id))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void phase7NotebookPaginationSerialAndLiteralSearch() throws Exception {
    String token = login("testadmin"), tag = "Notebook " + UUID.randomUUID();
    List<Long> serials = new ArrayList<>();
    String id = "";
    for (int i = 0; i < 3; i++) {
      var note =
          submit(
              "/transactions/notes",
              token,
              Map.of("title", tag + " " + i, "content", "বাংলা note 100%_ content"),
              201);
      id = note.get("id").asText();
      serials.add(note.get("serial").asLong());
      assertThat(note.get("authorId").asText()).isNotBlank();
    }
    assertThat(serials.get(1)).isGreaterThan(serials.get(0));
    assertThat(serials.get(2)).isGreaterThan(serials.get(1));
    mvc.perform(
            get("/notes")
                .param("q", tag)
                .param("_page", "2")
                .param("_limit", "2")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "3"))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].serial").value(serials.get(0)));
    mvc.perform(get("/notes").param("q", "100%_").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Total-Count", "3"));
    var changed =
        change(
            "/transactions/notes/" + id,
            token,
            Map.of("title", tag + " edited", "content", "Updated content", "expectedRevision", 0),
            200);
    assertThat(changed.get("serial").asLong()).isEqualTo(serials.get(2));
    change(
        "/transactions/notes/" + id,
        token,
        Map.of("title", tag, "content", "Stale", "expectedRevision", 0),
        409);
    mvc.perform(
            delete("/transactions/notes/" + id)
                .param("expectedRevision", "1")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deleted").value(true));
    mvc.perform(get("/notes/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void phase7SalesDueReportsAvoidPaymentJoinMultiplication() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 20);
    String
        first =
            stockPost(
                    "/transactions/sales",
                    token,
                    UUID.randomUUID().toString(),
                    saleBody(f, "fish", 5),
                    201)
                .get("id")
                .asText(),
        second =
            stockPost(
                    "/transactions/sales",
                    token,
                    UUID.randomUUID().toString(),
                    saleBody(f, "fish", 2),
                    201)
                .get("id")
                .asText();
    stockPost(
        "/transactions/payments", token, UUID.randomUUID().toString(), payBody(first, 10), 201);
    stockPost(
        "/transactions/payments", token, UUID.randomUUID().toString(), payBody(first, 20), 201);
    stockPost(
        "/transactions/payments", token, UUID.randomUUID().toString(), payBody(second, 40), 201);
    String filters = "?from=2026-11-11&to=2026-11-11&customerId=" + f.get("customer") + "&limit=1";
    var sales = readApi("/reports/sales" + filters, token);
    assertThat(sales.get("total").asInt()).isEqualTo(2);
    assertThat(sales.get("items").size()).isEqualTo(1);
    assertThat(sales.at("/summary/invoiceAmount").decimalValue()).isEqualByComparingTo("140");
    assertThat(sales.at("/summary/paidAmount").decimalValue()).isEqualByComparingTo("70");
    assertThat(sales.at("/summary/dueAmount").decimalValue()).isEqualByComparingTo("70");
    var due = readApi("/reports/dues" + filters, token);
    assertThat(due.get("total").asInt()).isEqualTo(1);
    assertThat(due.at("/items/0/invoiceCount").asInt()).isEqualTo(2);
    assertThat(due.at("/items/0/dueAmount").decimalValue()).isEqualByComparingTo("70");
  }

  @Test
  void phase7BangladeshDateBoundariesAndZeroFilledChart() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 20);
    for (String time :
        List.of("2026-11-10T18:00:00Z", "2026-11-11T17:59:59Z", "2026-11-11T18:00:00Z")) {
      var body = saleBody(f, "fish", 1);
      body.put("issuedAt", time);
      stockPost("/transactions/sales", token, UUID.randomUUID().toString(), body, 201);
    }
    var report =
        readApi(
            "/reports/sales?from=2026-11-11&to=2026-11-11&customerId=" + f.get("customer"), token);
    assertThat(report.get("total").asInt()).isEqualTo(2);
    assertThat(report.at("/summary/invoiceAmount").decimalValue()).isEqualByComparingTo("40");
    var chart = readApi("/dashboard/chart?from=2001-01-01&to=2001-01-03", token);
    assertThat(chart.size()).isEqualTo(3);
    assertThat(chart.at("/0/date").asText()).isEqualTo("2001-01-01");
    assertThat(chart.at("/2/date").asText()).isEqualTo("2001-01-03");
    assertThat(chart.at("/1/sales").decimalValue()).isEqualByComparingTo("0");
  }

  @Test
  void phase7StockReportCurrentAndPeriodReconciliation() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 10);
    stockPost(
        "/transactions/sales", token, UUID.randomUUID().toString(), saleBody(f, "fish", 2), 201);
    var current = readApi("/reports/stock?productId=" + f.get("product"), token);
    assertThat(current.at("/items/0/quantity").decimalValue()).isEqualByComparingTo("8");
    assertThat(current.at("/items/0/stockValue").decimalValue()).isEqualByComparingTo("80");
    var historic =
        readApi(
            "/reports/stock?from=2026-11-11&to=2026-11-11&productId=" + f.get("product"), token);
    assertThat(historic.at("/items/0/openingQuantity").decimalValue()).isEqualByComparingTo("0");
    assertThat(historic.at("/items/0/quantityChange").decimalValue()).isEqualByComparingTo("8");
    assertThat(historic.at("/items/0/quantity").decimalValue()).isEqualByComparingTo("8");
    assertThat(historic.at("/quantitiesByUnit/0/unit").asText()).isEqualTo("piece");
  }

  @Test
  void phase7ExpenseReportDateTotalsAndSoftDelete() throws Exception {
    String token = login("testadmin");
    var a = expenseBody("Rent report A", 100);
    a.put("category", "rent");
    a.put("spentAt", "2002-01-02T01:00:00Z");
    String id =
        stockPost("/transactions/expenses", token, UUID.randomUUID().toString(), a, 201)
            .get("id")
            .asText();
    var b = expenseBody("Rent report B", 200);
    b.put("category", "rent");
    b.put("spentAt", "2002-01-02T02:00:00Z");
    stockPost("/transactions/expenses", token, UUID.randomUUID().toString(), b, 201);
    var report =
        readApi("/reports/expenses?from=2002-01-02&to=2002-01-02&category=rent&limit=1", token);
    assertThat(report.get("total").asInt()).isEqualTo(2);
    assertThat(report.at("/summary/amount").decimalValue()).isEqualByComparingTo("300");
    assertThat(report.at("/byCategory/0/amount").decimalValue()).isEqualByComparingTo("300");
    mvc.perform(delete("/transactions/expenses/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
    assertThat(
            readApi("/reports/expenses?from=2002-01-02&to=2002-01-02&category=rent", token)
                .at("/summary/amount")
                .decimalValue())
        .isEqualByComparingTo("200");
  }

  @Test
  void phase7SupplierAndCustomerDetailsSummary() throws Exception {
    String token = login("testadmin"),
        supplier = stockSupplier(token),
        product = stockProduct(token, "fish");
    stockPost(
        "/transactions/purchases",
        token,
        UUID.randomUUID().toString(),
        purchaseBody(
            supplier, List.of(Map.of("productId", product, "quantity", 3, "unitPrice", 10))),
        201);
    var details = readApi("/suppliers/" + supplier + "/details", token);
    assertThat(details.at("/transactionSummary/purchaseCount").asInt()).isEqualTo(1);
    assertThat(details.at("/transactionSummary/purchaseAmount").decimalValue())
        .isEqualByComparingTo("30");
    assertThat(details.at("/transactionSummary/paymentSummaryAvailable").asBoolean()).isFalse();
    var report =
        readApi("/reports/purchases?from=2026-11-11&to=2026-11-11&supplierId=" + supplier, token);
    assertThat(report.at("/summary/totalAmount").decimalValue()).isEqualByComparingTo("30");
    var f = saleFixture(token, "fish", 5);
    var sale = saleBody(f, "fish", 1);
    sale.put("initialPaid", 5);
    sale.put("paymentMethod", "cash");
    stockPost("/transactions/sales", token, UUID.randomUUID().toString(), sale, 201);
    var customer = readApi("/customers/" + f.get("customer") + "/details", token);
    assertThat(customer.get("recentInvoices").size()).isEqualTo(1);
    assertThat(customer.get("recentPayments").size()).isEqualTo(1);
    assertThat(customer.at("/transactionSummary/dueAmount").decimalValue())
        .isEqualByComparingTo("15");
  }

  @Test
  void phase7ActivityFiltersAndFullTextAreAdminOnly() throws Exception {
    String token = login("testadmin");
    var expense =
        stockPost(
            "/transactions/expenses",
            token,
            UUID.randomUUID().toString(),
            expenseBody("Activity query expense", 10),
            201);
    String id = expense.get("id").asText();
    var logs =
        readApi(
            "/activityLogs?entity=expense&action=CREATE&entityId=" + id + "&text=expense", token);
    assertThat(logs.size()).isEqualTo(1);
    assertThat(logs.get(0).get("entityId").asText()).isEqualTo(id);
    mvc.perform(
            get("/activityLogs")
                .param("q", "%' OR 1=1 --")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    create("phase7logviewer", "viewer", token);
    mvc.perform(
            get("/activityLogs")
                .param("text", "expense")
                .header("Authorization", "Bearer " + login("phase7logviewer")))
        .andExpect(status().isForbidden());
  }

  @Test
  void phase7AuditFailureRollsBackExpenseAndNotebook() throws Exception {
    String token = login("testadmin"),
        key = UUID.randomUUID().toString(),
        title = "Rollback " + UUID.randomUUID();
    jdbc.execute(
        "ALTER TABLE nourish.activity_log ADD CONSTRAINT test_phase7_audit CHECK(entity NOT IN"
            + " ('expense','notebook')) NOT VALID");
    try {
      stockPost("/transactions/expenses", token, key, expenseBody(title, 10), 409);
      submit("/transactions/notes", token, Map.of("title", title, "content", "Rollback note"), 409);
    } finally {
      jdbc.execute("ALTER TABLE nourish.activity_log DROP CONSTRAINT test_phase7_audit");
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.expense WHERE title=?", Integer.class, title))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.notebook WHERE title=?", Integer.class, title))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.stock_request WHERE request_key=?",
                Integer.class,
                key))
        .isZero();
    stockPost("/transactions/expenses", token, key, expenseBody(title, 10), 201);
  }

  @Test
  void phase7MessageCenterSelectionWithoutSending() throws Exception {
    String token = login("testadmin"), tag = "Message " + UUID.randomUUID();
    var body = contactBody(tag);
    String id = submit("/catalog/customers", token, body, 201).get("id").asText();
    assertThat(readApi("/messageCenter", token).get("sendingEnabled").asBoolean()).isFalse();
    mvc.perform(
            get("/messageCenter/recipients")
                .param("q", tag)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id));
    body.put("status", "inactive");
    change("/catalog/customers/" + id, token, body, 200);
    mvc.perform(
            get("/messageCenter/recipients")
                .param("q", tag)
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(post("/messageCenter/send").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void phase7ValidationAndRoleBoundaries() throws Exception {
    String admin = login("testadmin");
    create("phase7operator", "operator", admin);
    create("phase7viewer", "viewer", admin);
    String operator = login("phase7operator"), viewer = login("phase7viewer");
    stockPost(
        "/transactions/expenses",
        viewer,
        UUID.randomUUID().toString(),
        expenseBody("Viewer attempt", 10),
        403);
    submit(
        "/transactions/notes", viewer, Map.of("title", "Viewer note", "content", "No write"), 403);
    stockPost(
        "/transactions/expenses",
        operator,
        UUID.randomUUID().toString(),
        expenseBody("Operator expense", 10),
        201);
    submit(
        "/transactions/notes",
        operator,
        Map.of("title", "Operator note", "content", "Shared note"),
        201);
    var bad = expenseBody("Bad amount", 10);
    bad.put("amount", 1.001);
    stockPost("/transactions/expenses", admin, UUID.randomUUID().toString(), bad, 400);
    submit("/transactions/notes", admin, Map.of("title", "a", "content", ""), 400);
    for (String path :
        List.of(
            "/reports/sales?from=2026-11-11",
            "/reports/purchases?from=2026-11-12&to=2026-11-11",
            "/reports/stock?customerId=bad",
            "/dashboard/chart?from=2020-01-01&to=2025-01-01",
            "/notes?_page=0",
            "/notes?_sort=title",
            "/expenses?limit=201"))
      mvc.perform(get(path).header("Authorization", "Bearer " + admin))
          .andExpect(status().isBadRequest());
    for (String path :
        List.of(
            "/reports/sales",
            "/reports/purchases",
            "/reports/stock",
            "/reports/dues",
            "/reports/expenses",
            "/dashboard",
            "/expenses",
            "/notes")) readApi(path, viewer);
  }

  @Test
  void phase7ConcurrentNotebookSerialsAndExpenseIdempotency() throws Exception {
    String token = login("testadmin"),
        key = UUID.randomUUID().toString(),
        tag = "Concurrent " + UUID.randomUUID();
    var pool = java.util.concurrent.Executors.newFixedThreadPool(3);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      List<java.util.concurrent.Future<com.fasterxml.jackson.databind.JsonNode>> notes =
          new ArrayList<>();
      for (int i = 0; i < 3; i++)
        notes.add(
            pool.submit(
                () -> {
                  start.await();
                  return submit(
                      "/transactions/notes",
                      token,
                      Map.of("title", tag, "content", "Concurrent note"),
                      201);
                }));
      start.countDown();
      Set<Long> serials = new HashSet<>();
      for (var f : notes)
        serials.add(f.get(30, java.util.concurrent.TimeUnit.SECONDS).get("serial").asLong());
      assertThat(serials).hasSize(3);
      var a =
          pool.submit(
              () -> stockPost("/transactions/expenses", token, key, expenseBody(tag, 10), 201));
      var b =
          pool.submit(
              () -> stockPost("/transactions/expenses", token, key, expenseBody(tag, 10), 201));
      assertThat(a.get(30, java.util.concurrent.TimeUnit.SECONDS).get("id"))
          .isEqualTo(b.get(30, java.util.concurrent.TimeUnit.SECONDS).get("id"));
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void phase7DashboardSummaryReflectsSaleCollectionAndLowStock() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "fish", 2);
    var before = readApi("/dashboard?date=2026-11-11", token);
    var body = saleBody(f, "fish", 2);
    body.put("initialPaid", 10);
    body.put("paymentMethod", "cash");
    stockPost("/transactions/sales", token, UUID.randomUUID().toString(), body, 201);
    var after = readApi("/dashboard?date=2026-11-11", token);
    assertThat(
            after
                .get("todaySales")
                .decimalValue()
                .subtract(before.get("todaySales").decimalValue()))
        .isEqualByComparingTo("40");
    assertThat(after.get("totalDue").decimalValue().subtract(before.get("totalDue").decimalValue()))
        .isEqualByComparingTo("30");
    assertThat(
            after
                .get("stockValue")
                .decimalValue()
                .subtract(before.get("stockValue").decimalValue()))
        .isEqualByComparingTo("-20");
    assertThat(after.get("stockAlertCount").asInt())
        .isEqualTo(before.get("stockAlertCount").asInt() + 1);
    assertThat(after.get("chartPoints").size()).isEqualTo(7);
    var reminders = readApi("/dashboard/reminders?limit=200", token);
    boolean found = false;
    for (var row : reminders)
      if (row.get("customerId").asText().equals(f.get("customer"))) {
        found = true;
        assertThat(row.get("amount").decimalValue()).isEqualByComparingTo("30");
      }
    assertThat(found).isTrue();
  }

  @Test
  void phase7ExpenseUpdateAndDeleteAuditFailurePreservesOriginal() throws Exception {
    String token = login("testadmin");
    var body = expenseBody("Protected expense", 100);
    String id =
        stockPost("/transactions/expenses", token, UUID.randomUUID().toString(), body, 201)
            .get("id")
            .asText();
    body.put("amount", 200);
    body.put("expectedRevision", 0);
    jdbc.execute(
        "ALTER TABLE nourish.activity_log ADD CONSTRAINT test_phase7_change"
            + " CHECK(entity<>'expense') NOT VALID");
    try {
      change("/transactions/expenses/" + id, token, body, 409);
      mvc.perform(delete("/transactions/expenses/" + id).header("Authorization", "Bearer " + token))
          .andExpect(status().isConflict());
    } finally {
      jdbc.execute("ALTER TABLE nourish.activity_log DROP CONSTRAINT test_phase7_change");
    }
    var current = readApi("/expenses/" + id, token);
    assertThat(current.get("amount").decimalValue()).isEqualByComparingTo("100");
    assertThat(current.get("revision").asInt()).isZero();
    assertThat(readApi("/expenses/" + id + "/history", token).size()).isEqualTo(1);
  }

  @Test
  void phase7ConcurrentExpenseEditsDetectStaleRevision() throws Exception {
    String token = login("testadmin");
    String id =
        stockPost(
                "/transactions/expenses",
                token,
                UUID.randomUUID().toString(),
                expenseBody("Concurrent edit", 100),
                201)
            .get("id")
            .asText();
    var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    var start = new java.util.concurrent.CountDownLatch(1);
    try {
      List<java.util.concurrent.Future<Integer>> tasks = new ArrayList<>();
      for (int amount : List.of(200, 300)) {
        var body = expenseBody("Concurrent edit", amount);
        body.put("expectedRevision", 0);
        tasks.add(
            pool.submit(
                () -> {
                  start.await();
                  return mvc.perform(
                          put("/transactions/expenses/" + id)
                              .header("Authorization", "Bearer " + token)
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(json.writeValueAsString(body)))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      start.countDown();
      assertThat(
              List.of(
                  tasks.get(0).get(30, java.util.concurrent.TimeUnit.SECONDS),
                  tasks.get(1).get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    } finally {
      pool.shutdownNow();
    }
    assertThat(readApi("/expenses/" + id + "/history", token).size()).isEqualTo(2);
  }

  @Test
  void phase7EmptyReportsAndNotebookCorsHeader() throws Exception {
    String token = login("testadmin");
    for (String type : List.of("sales", "purchases", "expenses")) {
      var report = readApi("/reports/" + type + "?from=2001-01-01&to=2001-01-01", token);
      assertThat(report.get("total").asInt()).isZero();
      assertThat(report.get("items").size()).isZero();
    }
    mvc.perform(
            get("/notes")
                .header("Origin", "http://localhost:4200")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(
            header()
                .string(
                    "Access-Control-Expose-Headers",
                    org.hamcrest.Matchers.containsString("X-Total-Count")));
    String protectedId =
        stockPost(
                "/transactions/expenses",
                token,
                UUID.randomUUID().toString(),
                expenseBody("SQL protected", 10),
                201)
            .get("id")
            .asText();
    assertThatThrownBy(
            () -> jdbc.update("UPDATE nourish.expense SET amount=amount+1 WHERE id=?", protectedId))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void phase8MasterPromotionIsAtomicReplayableAndAdminOnly() throws Exception {
    String admin = login("testadmin");
    create("migrationviewer", "viewer", admin);
    String viewer = login("migrationviewer");
    String batch = "a".repeat(64);
    jdbc.update(
        "INSERT INTO nourish.migration_batch(source_sha256,source_name,report) VALUES"
            + " (?,'test.json','{}')",
        batch);
    jdbc.update(
        "INSERT INTO nourish.migration_record VALUES"
            + " (?,'customers',0,'legacy-one','{\"id\":\"legacy-one\",\"archived\":true,\"archivedReason\":\"legacy"
            + " history\"}','[]')",
        batch);
    String path = "/migration/" + batch + "/customers/0/promote";
    var command = contactBody("Migration Buyer");
    submit(path, viewer, command, 403);
    var bad = new HashMap<>(command);
    bad.put("nameEn", "");
    submit(path, admin, bad, 400);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.migration_mapping WHERE source_sha256=?",
                Long.class,
                batch))
        .isZero();
    var first = submit(path, admin, command, 200);
    var again = submit(path, admin, command, 200);
    assertThat(first.get("id")).isEqualTo(again.get("id"));
    assertThat(again.get("replayed").asBoolean()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT archived FROM nourish.customer WHERE id=?",
                Boolean.class,
                first.get("id").asText()))
        .isTrue();
    command.put("nameEn", "Changed Buyer");
    submit(path, admin, command, 409);
    assertThatThrownBy(
            () -> jdbc.update("DELETE FROM nourish.migration_mapping WHERE source_sha256=?", batch))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    submit("/migration/" + batch + "/invoices/0/promote", admin, Map.of(), 400);
    mvc.perform(get("/verification/reconciliation").header("Authorization", "Bearer " + viewer))
        .andExpect(status().isForbidden());
    assertThat(readApi("/verification/reconciliation", admin).size()).isZero();
  }

  @Test
  void phase8PromotionRollsBackWhenMappingCannotBeRecorded() throws Exception {
    String admin = login("testadmin"), batch = "b".repeat(64);
    jdbc.update(
        "INSERT INTO nourish.migration_batch(source_sha256,source_name,report) VALUES"
            + " (?,'rollback.json','{}')",
        batch);
    jdbc.update(
        "INSERT INTO nourish.migration_record VALUES"
            + " (?,'suppliers',0,'rollback-one','{\"id\":\"rollback-one\"}','[]')",
        batch);
    var command = contactBody("Migration Rollback Supplier");
    jdbc.execute(
        "ALTER TABLE nourish.migration_mapping ADD CONSTRAINT migration_test_block"
            + " CHECK(collection<>'suppliers')");
    try {
      submit("/migration/" + batch + "/suppliers/0/promote", admin, command, 409);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM nourish.supplier WHERE phone=?",
                  Long.class,
                  command.get("phone")))
          .isZero();
    } finally {
      jdbc.execute("ALTER TABLE nourish.migration_mapping DROP CONSTRAINT migration_test_block");
    }
  }

  @Test
  void phase8DuplicateSourceIdCannotBePromoted() throws Exception {
    String admin = login("testadmin"), batch = "c".repeat(64);
    jdbc.update(
        "INSERT INTO nourish.migration_batch(source_sha256,source_name,report) VALUES"
            + " (?,'duplicates.json','{}')",
        batch);
    for (int i = 0; i < 2; i++)
      jdbc.update(
          "INSERT INTO nourish.migration_record VALUES"
              + " (?,'customers',?,'same','{\"id\":\"same\"}','[]')",
          batch,
          i);
    submit(
        "/migration/" + batch + "/customers/0/promote",
        admin,
        contactBody("Duplicate Source"),
        400);
  }

  @Test
  void phase8QuoteDoesNotReserveStockAndMatchesInvoice() throws Exception {
    String token = login("testadmin");
    var f = saleFixture(token, "feed", 4);
    var body = saleBody(f, "feed", 2);
    var before = stockQuantity(f.get("product"));
    var quote =
        submit(
            "/sales/quote",
            token,
            Map.of("customerId", f.get("customer"), "items", body.get("items")),
            200);
    assertThat(stockQuantity(f.get("product"))).isEqualByComparingTo(before);
    var invoice = stockPost("/transactions/sales", token, UUID.randomUUID().toString(), body, 201);
    assertThat(invoice.get("subtotal").decimalValue())
        .isEqualByComparingTo(quote.get("subtotal").decimalValue());
    assertThat(stockQuantity(f.get("product"))).isLessThan(before);
    assertThat(readApi("/verification/reconciliation", token).size()).isZero();
  }

  @Test
  void phase8CatalogLinkChecksIdentityWithoutImportingDemoStock() throws Exception {
    String admin = login("testadmin"), batch = "d".repeat(64);
    jdbc.update("INSERT INTO nourish.migration_batch VALUES (?,'catalog.json','{}',now())", batch);
    var source = readApi("/products/prod-nourish-3103", admin);
    ((com.fasterxml.jackson.databind.node.ObjectNode) source).put("stockQuantity", 99999);
    jdbc.update(
        "INSERT INTO nourish.migration_record VALUES"
            + " (?,'products',0,'old-catalog-product',?::jsonb,'[]')",
        batch,
        json.writeValueAsString(source));
    var before = stockQuantity("prod-nourish-3103");
    var link =
        submit(
            "/migration/" + batch + "/products/0/link",
            admin,
            Map.of("targetId", "prod-nourish-3103"),
            200);
    assertThat(link.get("stockImported").asBoolean()).isFalse();
    assertThat(stockQuantity("prod-nourish-3103")).isEqualByComparingTo(before);
    submit(
        "/migration/" + batch + "/products/0/link",
        admin,
        Map.of("targetId", "prod-nourish-3103"),
        200);
    submit(
        "/migration/" + batch + "/products/0/link",
        admin,
        Map.of("targetId", "not-an-existing-product"),
        404);
  }

  @Test
  @org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
      named = "NOURISH_LEGACY_VALIDATION",
      matches = ".+")
  void phase8SuppliedLegacyStagingAndMasterPlanVerifyInDisposableDatabase() throws Exception {
    var dir = Path.of(System.getenv("NOURISH_LEGACY_VALIDATION"));
    String sql = Files.readString(dir.resolve("legacy-staging.sql"));
    jdbc.execute(sql);
    jdbc.execute(sql);
    var plan = json.readTree(Files.readString(dir.resolve("review-master-plan.json")));
    String admin = login("testadmin"), batch = plan.get("sourceSha256").asText();
    for (var entry : plan.get("records")) {
      String path =
          "/migration/"
              + batch
              + "/"
              + entry.get("collection").asText()
              + "/"
              + entry.get("ordinal").asInt()
              + "/"
              + entry.get("action").asText();
      submit(path, admin, entry.get("command"), 200);
      submit(path, admin, entry.get("command"), 200);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM nourish.migration_mapping WHERE source_sha256=?",
                Long.class,
                batch))
        .isEqualTo((long) plan.get("records").size());
    assertThat(readApi("/verification/reconciliation", admin).size()).isZero();
  }

  @Autowired org.springframework.core.env.Environment environment;

  @Test
  void swaggerToggleSecurityAndCommandSchemas() throws Exception {
    String admin = login("testadmin");
    if (!environment.getProperty("springdoc.api-docs.enabled", Boolean.class, false)) {
      mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + admin))
          .andExpect(status().isForbidden());
      mvc.perform(get("/swagger-ui/index.html").header("Authorization", "Bearer " + admin))
          .andExpect(status().isForbidden());
      return;
    }
    mvc.perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/swagger-ui.html"));
    mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
    var response = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
    var doc = json.readTree(response.getResponse().getContentAsString());
    assertThat(doc.at("/components/securitySchemes/bearerAuth/scheme").asText())
        .isEqualTo("bearer");
    assertThat(doc.path("paths").path("/auth/login").path("post").path("security").size()).isZero();
    assertThat(doc.path("security").size()).isEqualTo(1);
    assertThat(doc.path("paths").path("/auth/login").path("post").has("security")).isTrue();
    java.nio.file.Files.writeString(java.nio.file.Path.of("target/swagger-test-openapi.json"),doc.toPrettyString());

    for (String endpoint :
        List.of(
            "/transactions/sales",
            "/transactions/purchases",
            "/transactions/payments",
            "/catalog/products",
            "/catalog/customers",
            "/sales/quote")) {
      var schema =
          doc.path("paths")
              .path(endpoint)
              .path("post")
              .path("requestBody")
              .path("content")
              .path("application/json")
              .path("schema");
      assertThat(schema.path("$ref").asText()).startsWith("#/components/schemas/");
      String name = schema.path("$ref").asText().substring("#/components/schemas/".length());
      assertThat(doc.path("components").path("schemas").path(name).path("properties").size())
          .isGreaterThan(0);
    }
    // All local references resolve, including both distinct nested purchase/sale Line records.
    for (var ref : doc.findValuesAsText("$ref"))
      if (ref.startsWith("#/"))
        assertThat(doc.at(ref.substring(1)).isMissingNode()).as(ref).isFalse();
    mvc.perform(get("/customers")).andExpect(status().isUnauthorized());
    var sale = doc.path("paths").path("/transactions/sales").path("post");
    assertThat(sale.path("parameters").toString()).contains("Idempotency-Key");
  }
}
