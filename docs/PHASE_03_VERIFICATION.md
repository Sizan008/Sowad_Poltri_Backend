# Phase 3 verification

- Maven clean verify: passed, 18 tests total (17 PostgreSQL integration + 1 exception-handler test).
- The integration database is disposable embedded PostgreSQL 14.15. The supplied shop
  schema was exported from PostgreSQL 16.15; no connection to the user's live DB was made.
- Migration applied twice. 76 catalog entries, four categories, package definitions,
  all four product kinds, invalid payloads, immutable official identity, normalized SKU
  collisions, concurrent duplicate registration, soft archive, package conversion,
  duplicate normalized package sizes, deactivation, viewer/operator permissions,
  authentication regression and atomic catalog+audit rollback were tested.
- Angular: npm ci, npm run typecheck, npm run build passed. Existing Angular warning
  messages remain (unused imports / optional-chain diagnostics); no compilation errors.
- Local Java runtime lacks ct.sym, so verification used Java 21 source/target overrides.
  The delivered POM remains unchanged and expects a normal full JDK 21.
- No live browser end-to-end test on the user's PC has been claimed. Run the supplied
  catalog-check.py and the UI acceptance checks after applying SQL locally.

## UI acceptance
1. Login as the existing backend admin. Products shows 76 reference SKUs initially.
2. Register a unique Feed, liquid/powder Medicine, valid Chick breed+grade and Fish.
3. Edit a manual product. Official feed identity changes and duplicate SKU must fail.
4. Catalog & Packaging: select each category and successive dropdowns; the final
   selector contains only matching active products. Unregistered categories are empty.
5. Add 100 ml and 1 L definitions to liquid medicine. Attempt a kg definition: reject.
6. Archive a manual product: it disappears from active lists/cascades; ID remains in DB.
7. Viewer may read catalog; writes are denied by the API. Page refresh requires login.
8. JSON Server need not run for the completed catalog workspace.
