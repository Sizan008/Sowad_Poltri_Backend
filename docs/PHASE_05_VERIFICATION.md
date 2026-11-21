# Phase 5 verification

Validated 2026-11-11 against the supplied Sowad_Poltri_Backend(3).zip baseline.

- Maven clean verify succeeded: 44 PostgreSQL integration tests plus 1 exception-handler
  unit test, zero failures/errors/skips (30 existing checks + 15 Phase 5 checks).
- Disposable embedded PostgreSQL 14.15; the user's live PostgreSQL was not accessed.
- Tested mixed feed sizes/direct and unit pricing precedence, medicine litre/ml conversion,
  chick/fish pieces, immutable snapshots, explicit global selling-rate updates and opt-out.
- Tested quantity/price-key/package mismatch, fractional counts, inactive supplier/package,
  foreign product packaging, low-stock threshold boundaries and access roles.
- Tested idempotent retries/key conflicts, simultaneous purchases, concurrent deductions,
  reversed multi-product lock order, stale recount revisions and full physical-count rules.
- Forced audit failures rolled back purchases/adjustments, stock, cost/global selling rate,
  movements and idempotency keys; retry subsequently succeeded.
- Direct invalid SQL balance/history edits were rejected. Migrations 1–5 were applied to
  disposable PostgreSQL; each versioned migration was executed twice to check rerun behavior.
- Installer dry-run/apply/repeat/conflict-rejection verified against an extracted baseline;
  installed files matched tested source. No frontend/configuration/POM files included.

Local tool environment used Java 21 with source/target 21 compiler flags because its JRE
lacks the full JDK release-symbol archive. The delivered POM is unchanged; use full JDK 21
and Maven for the normal mvn clean install command on your Linux PC.
