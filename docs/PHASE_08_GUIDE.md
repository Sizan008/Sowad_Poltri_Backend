# Phase 8 — migration, Angular integration and verification

## Scope and rollout

Backend baseline: supplied `Sowad_Poltri_Backend(6).zip` (Phases 1–7).
Angular baseline: original `Sowad_poltri_without_node_modules.zip`. The earlier Phase 3 Angular patch is **not** a prerequisite.
Install the separate patches, execute `sql/08_migration_verification.sql` in DBeaver, build, restart, then run the read-only smoke check. Do not run the old Phase 7 health check against Phase 8.
No frontend credentials, demo login, or JSON Server headers authenticate a request. Existing backend users/passwords remain in place.

Backend:
```bash
cd ~/Documents/Sowad_Poltri_Backend
bash ~/Downloads/NOURISH_Backend_Phase_08.sh --check
bash ~/Downloads/NOURISH_Backend_Phase_08.sh
# Execute sql/08_migration_verification.sql in DBeaver before starting the new backend.
mvn clean install
export DB_HOST=127.0.0.1 DB_PORT=2001 DB_NAME=nourish_poltri_shop DB_USER=myapp_user
read -r -s -p 'PostgreSQL password: ' DB_PASSWORD
printf '\n'
export DB_PASSWORD
bash scripts/run.sh
```
In a second terminal:
```bash
cd ~/Documents/Sowad_Poltri_Backend
python3 scripts/phase8-check.py
```
Use an existing admin app login for reconciliation. App login is different from the PostgreSQL username/password.

Frontend (run in the folder containing the original Angular `package.json`):
```bash
bash ~/Downloads/NOURISH_Frontend_Phase_08.sh --check
bash ~/Downloads/NOURISH_Frontend_Phase_08.sh
npm ci
npm run typecheck
node tests/contracts.mjs
npm run build:prod
npm start
```
Open `http://127.0.0.1:4200`. Local API is `http://127.0.0.1:8080`. Requires JDK 21, Maven 3.9+, Python 3 and a Node version allowed by package.json (Node 24 used for verification).

Production builds use `/api`. Configure a same-origin HTTPS reverse proxy which strips `/api/` before forwarding to Spring Boot. Example nginx location:
```nginx
location /api/ {
    proxy_pass http://127.0.0.1:8080/;
    proxy_set_header Host $host;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```
Serve the Angular SPA with fallback to index.html. If using separate origins, change environment.production.ts and explicitly set CORS_ALLOWED_ORIGINS on the backend. Never point a publicly hosted browser app at the visitor's localhost. Tokens are in tab sessionStorage, checked through `/auth/me` on reload; expiration/401 requires login again. No passwords are persisted by the Angular app.

## Frontend changes

- Real asynchronous login, token interceptor restricted to the configured origin/path, logout revocation, initial session validation, 401 redirect. Demo account buttons removed.
- Sales call `/transactions/sales`; quotes call `/sales/quote`. Every valid selection refreshes the server price preview. Save waits for the preview; a concurrent price change still returns 409 instead of silently changing the price. Quotes reserve no stock.
- Purchase/sale/payment/expense/adjustment POSTs send Idempotency-Key. A pending identical command reuses its key after network/5xx failure. Successful or definitive 4xx requests clear it. There are no automatic write retries. Do not clear tab storage or change a command after an uncertain failure until checking the recorded transaction.
- Customer/supplier writes send command fields only, excluding generated IDs, aliases and timestamps. Unit-rate edits preserve existing package-rate rows.
- Bounded server pagination is followed for transaction lists, package balances and movement history; no first-50-row totals. Existing UI list views still paginate the complete fetched result. Very large datasets may need future UI virtual scrolling/server-side list pagination.
- Dashboard and report calculations use JDBC API results. Blank report dates mean last 30 days for sales/purchases, current stock and all current dues. Report payment totals are current collections against the selected invoices, not historical as-of due. Stock valuation uses latest purchase cost, not FIFO.
- Customer ledger uses server running balances; due listing includes archived customers with debt.
- Product Edit contains packaging-definition management. Register the product, then edit it to add the exact bag/medicine pack sizes before purchasing. Definitions never create stock or set prices. Purchase sets global selling rate only when explicitly requested.
- Stock adjustment translates selected sizes into registered packaging IDs, signs counts and supplies the current revision. Only admins can open the adjustment route. Adjustment time is server current time. Recount remains the existing documented backend command, requiring the revision and all nonzero packages.
- Item detail calls include parent IDs; invoice/purchase item responses contain package counts. Printable invoice uses the stored customer snapshot. HTML print/Save as PDF is the UI export; backend `/download` remains a JSON attachment.
- Message Center remains recipient selection only. No SMS sending or gateway was added.

## JSON validation and migration

The supplied JSON contains **38 invoice headers / 4 invoice items**, **8 purchase headers / 2 purchase items**, and **41 transaction headers with no items**. The attached report has record-level findings. It also flags missing English fields, account snapshots, unverified stock and package-total differences.

**No demo transaction, opening stock, price, payment, due, notebook or expense is automatically made live.** Archived categories/products, original IDs, references, archive reasons, original notes and timestamps are retained as raw JSON staging records. `users` is excluded so no legacy identity or password is imported. No missing item, English translation, historical payment account or opening balance is guessed.

Generate the report again for a revised export:
```bash
python3 scripts/validate-legacy.py /path/to/db.json --out /path/to/validation-output
python3 scripts/test-legacy-validation.py
```
The validator rejects duplicate JSON keys and validates IDs, references, contact requirements, numeric types/counts, item/header totals, overpayment and packaged stock totals. It is a preflight report, **not automatic approval**; the live service revalidates every master command with current domain rules and database constraints.

The optional `NOURISH_Phase_08_Migration.zip` contains the supplied-data report, `legacy-staging.sql`, and `review-master-plan.json`. In DBeaver execute `legacy-staging.sql` only after Phase 8. It is transactional and rerunnable, and inserts solely into migration tables. It does not change operational stock or balances.

Review the plan before applying it. It has 159 candidates: 3 customers with complete required fields, 4 category links, 76 catalog links and 76 official product links. Existing database uniqueness/conflicts can still reject a candidate. Category/unit display labels can differ by language; identity uses category ID/kind and unit code/decimal rules. Official feed pricing links compare the numerical catalog fields and effective date. Product links compare official identity fields. Source labels and reference prices remain available in staging; live records are never overwritten by linking.

```bash
python3 scripts/import-reviewed.py /path/to/review-master-plan.json
# After reviewing the report/plan and executing legacy-staging.sql:
python3 scripts/import-reviewed.py /path/to/review-master-plan.json --apply
```
The script prompts for an admin app account. Each record is atomic and replay-safe; a stopped batch retains prior successful records. Identical reruns return the original mapped ID. Changed commands against an already imported source record fail with 409. All rejected records remain staged. Mapping insertion failure rolls back the newly created master and its audit changes.

Correct incomplete master commands explicitly using the real data, then add those records to a separate reviewed plan. Do not edit the immutable original payload. Promotion accepts:
- customers/suppliers: Phase 4 SaveContact fields (nameEn/addressEn/phone/status required).
- products/archivedProducts: Phase 3 SaveProduct identity fields; no stock/prices.
- units: SaveUnit.
- receivingAccounts: SaveReceivingAccount.
Archived supported masters are archived/deactivated in the same transaction. Unsupported historical categories stay staged. Business rules can reject a source record even after textual fields are corrected.

Links: POST `/migration/{sha256}/{collection}/{ordinal}/link`, body `{"targetId":"..."}`; collections categories, units, feedPriceCatalog and official products. Promotions: POST the ordinary approved create-command body to `/migration/{sha256}/{collection}/{ordinal}/promote`. All migration operations are admin-only. Ordinal is zero-based in the original collection; duplicate legacy IDs cannot be promoted/linked.

After mapping customer/product/packaging IDs, manually review customer-specific rates against the current product base unit and package definition, then save using `PUT /customers/{new-id}/rates`. That endpoint is full replacement; submit the complete intended unit and package rate set. Original embedded rates remain in the staged customer payload. Do not substitute a missing product or package.

For historic transactions, obtain missing item rows, exact package counts, approved opening inventory, actual rate/account snapshots, dates and totals. Re-run validation and reconcile in a separate database before designing a historical import. The ordinary purchase/sale endpoints use current rates and dates and **must not** be used to reconstruct incomplete historical demo transactions. Existing live Phases 5–7 data is already canonical and is preserved.

Inspect preserved IDs and unresolved records:
```sql
SELECT r.collection,r.ordinal,r.legacy_id,m.target_id,m.imported_at
FROM nourish.migration_record r
LEFT JOIN nourish.migration_mapping m USING(source_sha256,collection,ordinal)
ORDER BY r.collection,r.ordinal;
SELECT collection,count(*) FROM nourish.migration_record GROUP BY collection;
SELECT * FROM nourish.reconciliation_issue;
```
Expected reconciliation result: zero rows. The view flags stock-versus-ledger, stock-versus-packages, invoice subtotal/total, purchase total, overpayment, payment customer and customer-ledger due discrepancies. Existing deferred constraints continue to protect package detail totals, movement chains, immutable snapshots and concurrent balances. This view detects issues; it does not repair history.

## API map

All business endpoints require Bearer authentication. Full request examples for stable endpoints remain in PHASE_03_API.md through PHASE_07_API.md in this project.

| Area | Reads | Commands / notes |
|---|---|---|
| Auth | GET /auth/me | POST /auth/login, /auth/logout |
| Catalog | /categories, /units, /products, /feedPriceCatalog, /catalogOptions/product-types, /catalog/cascade-options | POST/PUT /catalog/products; DELETE archive; /catalog/products/{id}/packaging; units CRUD |
| Contacts | /customers, /suppliers, /{id}/details | POST/PUT /catalog/customers; POST/PUT /suppliers; DELETE contact archive |
| Rates | /customers/{id}/rates | PUT full replacement |
| Receiving accounts | /receivingAccounts | POST/PUT/DELETE /transactions/receiving-accounts; admin |
| Purchase | /purchases, /purchaseItems?purchaseId=... | POST /transactions/purchases; Idempotency-Key |
| Stock | /inventory, /packageStocks, /medicinePacks, /stockMovements | POST /transactions/stock-adjustments; admin + key |
| Quote | POST /sales/quote | {customerId,items}; read-only, no stock reservation |
| Sale | /invoices, /invoiceItems?invoiceId=..., /invoices/{id}/download | POST /transactions/sales; key |
| Collection | /payments, /customers/{id}/due, /customers/{id}/ledger, /customerInsights | POST /transactions/payments; key |
| Dashboard | /dashboard, /dashboard/chart, /dashboard/reminders, /dashboard/low-stock | Server aggregations |
| Reports | /reports/sales, purchases, stock, dues, expenses | from/to inclusive Asia/Dhaka; limit/offset |
| Expense | /expenses | POST/PUT/DELETE /transactions/expenses; POST key |
| Notebook | /notes?_page=1&_limit=20 | POST/PUT/DELETE /transactions/notes |
| Activity | /activityLogs?limit=...&offset=... | Admin read; server writes audit |
| Settings | /businessSettings/business | PUT /transactions/settings; admin |
| Messages | /messageCenter, /messageCenter/recipients | No send endpoint |
| Migration | SQL migration_record inspection | Admin POST /migration/{sha}/{collection}/{ordinal}/promote or /link |
| Reconciliation | GET /verification/reconciliation | Admin, read-only |

## Backup and restore drill

Before migration, stop application writes and save a custom-format backup. Use a matching/newer PostgreSQL client major version than the server. Password prompts do not expose passwords in command arguments.
```bash
umask 077
pg_dump -h 127.0.0.1 -p 2001 -U myapp_user -W \
  -d nourish_poltri_shop -Fc \
  -f "$HOME/Downloads/nourish_before_phase8.dump"
pg_restore --list "$HOME/Downloads/nourish_before_phase8.dump" > "$HOME/Downloads/nourish_backup_contents.txt"
```
Test restore to a **new** database; do not drop the working database:
```bash
createdb -h 127.0.0.1 -p 2001 -U postgres -W -T template0 \
  -O myapp_user nourish_restore_check
pg_restore -h 127.0.0.1 -p 2001 -U myapp_user -W \
  --no-owner --no-privileges --single-transaction --exit-on-error \
  -d nourish_restore_check "$HOME/Downloads/nourish_before_phase8.dump"
```
In the restored DB inspect schema_version, transaction counts and totals. A pre-Phase-8 backup will not contain reconciliation_issue; run the equivalent SELECT bodies from Phase 8, without executing its database-name-guarded migration there. Only a separately reviewed cutover should restore over a live database. Backups include confidential business/auth data; keep them private.

Patch installers preflight every changed source file and back up old files under patch-backups. They reject unrecognized local changes and symlinks, do not alter credentials, and never execute SQL. A re-run is a no-op. Rollback uses the patch backup plus its PATCH_NEW_FILES.json list; restore old backend and database **together** during maintenance. Do not downgrade application code against schema 8 without a coordinated restore.

## Verification and remaining cutover checks

Automated backend verification uses a disposable PostgreSQL database; it does not connect to your local business DB. Includes migration rerun, reviewed master import/replay/archive/rollback, catalog identity mapping, server quote versus invoice, purchase → stock → sale → partial → final collection, concurrent sale/payment, duplicate request, insufficient stock, overpayment and role checks. Optional supplied-data staging/plan test:
```bash
NOURISH_LEGACY_VALIDATION=/absolute/path/to/extracted/migration-folder mvn clean verify
```
That folder must contain legacy-staging.sql and review-master-plan.json. Without the environment variable only this optional supplied-file test is skipped.

Frontend checks: TypeScript typecheck, Angular production compilation, and executable auth/interceptor/pagination/quote contract tests. Full interactive browser acceptance on your running database is still required:
1. Login as admin/operator/viewer; check route and API write permissions; reload and logout.
2. Register a test supplier/customer/product, define the package, purchase, then sell using the server preview.
3. Collect partially and finally; inspect stock movement, invoice snapshot and customer ledger.
4. Check dashboard and date-filtered reports, notebook/expense changes and activity history.
5. Run phase8-check.py and the reconciliation query; expected no discrepancy rows.
6. Verify a backup restore before final cutover; retain the untouched source JSON and validation report.

No claim is made that the user's live database, deployed browser, or backup restore has already been tested. Invalid source transactions remain a migration blocker, not fabricated completed imports.
