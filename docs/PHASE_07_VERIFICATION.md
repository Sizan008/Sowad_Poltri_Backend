# Phase 7 verification

2026-11-11: Maven clean verify succeeded with 79 tests: 78 PostgreSQL integration tests
and 1 exception-handler unit test, zero failures/errors/skips. Includes all 63 previous
checks plus 16 Phase 7 checks. Disposable PostgreSQL 14.15; no user database was accessed.

Checked dashboard metric changes after sale/collection, current low-stock alerts and due
reminders; inclusive Bangladesh midnight boundaries; zero-filled chart dates; report
summary totals before pagination and invoice-payment join multiplication; current and
movement-date stock reports; expense category totals and deleted-record exclusion.

Checked expense create/edit/delete history, idempotent creation, stale/concurrent edits,
immutable database history, direct changes without revision snapshots, and audit-failure
rollback for create/update/delete. Checked notebook serials, shared CRUD, Bangla/literal
search, pagination totals, CORS count header and concurrent serial uniqueness.

Checked customer/supplier summaries, admin-only activity filters/full-text/literal search,
role/validation boundaries, active-recipient filtering and absence of a Message Center
send endpoint. Phase migrations run twice verify rerun guards. All old purchase/sale,
stock/payment/customer-rate/account/authentication checks still pass.

Installer dry-run/apply/repeat/conflict tests compare installed bytes against tested
source; no frontend, POM, application.properties or credentials are included in changes.
Local Java 21 runtime uses source/target compiler flags because it lacks the full JDK
release-symbol archive. Delivered POM is unchanged: use full JDK 21 and mvn clean install.
