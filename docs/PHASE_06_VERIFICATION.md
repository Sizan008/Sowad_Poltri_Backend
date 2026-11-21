# Phase 6 verification

2026-11-11: Maven clean verify succeeded with 63 tests (62 PostgreSQL integration tests
and 1 exception-handler unit test), zero failures/errors/skips. Includes all 45 previous
checks plus 18 new Phase 6 tests. Disposable PostgreSQL 14.15; no user database accessed.

Validated purchase → exact package stock → customer-rate invoice → discount/initial
payment → partial collection → remaining due → final collection and paid category.
Validated feed/medicine/chick/fish stock deduction, package/customer-unit/global-rate
precedence, missing/stale rates, exact package shortages even with enough total stock,
quantity/date/discount/payment validation, customer/account state and permissions.

Validated immutable customer/rate/account snapshots, account edits/deactivation, bank/MFS
matching and sender normalization, duplicate keys and transfer references, concurrent
sales/overpayments/identical payment retries. Forced audit/account failures rolled back
invoices/payments/stock and request keys. Direct financial-history edits and overpayment
inserts were rejected by PostgreSQL. Ledger, billing/buying categories and JSON attachment
were exercised. Migrations were applied twice to verify version guards.

Installer dry-run/apply/repeat/conflict tests compare exact installed bytes with tested
source. No frontend, POM, application.properties or credentials are changed.

The local Java 21 runtime required Maven source/target 21 flags because it lacks the
full JDK release-symbol archive. Delivered POM remains unchanged; use a full JDK 21 and
the normal mvn clean install command on your Linux PC.
