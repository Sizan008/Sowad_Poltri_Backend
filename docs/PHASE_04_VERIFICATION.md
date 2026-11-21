# Phase 4 verification

Baseline: Sowad_Poltri_Backend(2).zip; its source matched the delivered Phase 3 backend.
No Angular, dummy JSON, application.properties, Maven settings or IDE files are changed.

Maven clean verify passed: 30 tests total (29 real-PostgreSQL integration tests and
1 exception-handler unit test), zero failures/errors/skips. The test database is a
separate disposable embedded PostgreSQL 14.15, not the user's PostgreSQL 16.15 database.
Local runtime lacks ct.sym; verification used Java 21 source/target overrides. The
unchanged delivered POM expects a normal full JDK 21. No dependencies were added.

Coverage:
- Phase 1–3 health/auth/roles/catalog regression; Phase 4 migration applied twice.
- Customer/supplier bilingual fields, canonical phone uniqueness including archives,
  immutable IDs/creation time, update, inactivity, soft archive and details foundations.
- Search/filter, literal wildcard handling, parameterized queries and pagination validation.
- Unit-rate precision down to 0.000001, legacy feed rates, rate/unit mismatch rejection,
  product/package ownership, duplicate entries, atomic replacement and explicit clearing.
- Inactive customers, archived products and deactivated packages cannot receive new rates;
  old configuration remains available for review with reference-state flags.
- MFS and bank validation, destination uniqueness, leading zero preservation, update,
  deactivation/reactivation and account-kind immutability.
- Viewer/operator/admin permissions; strict rejection of unknown or secret fields.
- Forced audit failure rolls back customer/supplier/account creation and rate replacement.
- Concurrent normalized-phone creation yields one success/one conflict. Concurrent
  rate replacement yields one complete configuration, not interleaved partial rows.
- Database foreign keys reject orphan and mismatched product/package rate mappings.

Installation instructions, endpoint contracts and request examples are included.
The smoke-check script authenticates and reads only; it does not seed real business data.
Application deployment and verification against the user's live database remain local steps.
