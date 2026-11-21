# NOURISH Phase 7 — Dashboard, reports and supporting features

Baseline: Sowad_Poltri_Backend(5).zip. Backend only; no frontend patch required.

1. Stop the backend and retain a database backup.
2. From ~/Documents/Sowad_Poltri_Backend:
   `bash ~/Downloads/NOURISH_Backend_Phase_07.sh`
3. DBeaver, existing myapp_user connection to nourish_poltri_shop (port 2001): execute
   the WHOLE sql/07_reports_support.sql DO block. Do not repeat earlier migrations.
4. Full JDK 21: `mvn clean install` as a normal Linux user.
5. Start IntelliJ ProviderApplication with existing database variables, or run
   `bash scripts/run.sh` in the terminal where DB_PASSWORD is already exported.
6. Another terminal: `python3 scripts/phase7-check.py`; use application credentials.
   It reads business data only. Login/logout create normal audit records.
7. Read docs/PHASE_07_API.md for endpoints, pagination, dates, report basis and roles.

The installer preflights changed-file hashes, refuses local conflicts, backs up originals
under patch-backups, supports --check and repeat application. It executes no SQL and does
not change credentials, application.properties, POMs or IntelliJ configuration.
The migration is atomic and version-guarded. Existing Phase 1–6 business data stays intact.
New expense/notebook tables start empty; report views use existing transactions.

```sql
SELECT version,description FROM nourish.schema_version ORDER BY version;
SELECT (SELECT count(*) FROM nourish.expense WHERE NOT deleted) AS expenses,
       (SELECT count(*) FROM nourish.notebook WHERE NOT deleted) AS notes,
       (SELECT count(*) FROM nourish.report_invoice_balance) AS reported_invoices;
-- Must return no rows (one report row per invoice):
SELECT id,count(*) FROM nourish.report_invoice_balance GROUP BY id HAVING count(*)<>1;
```

Expect schema version 7. DO Updated Rows: 0 is normal. Start the backend only after SQL.
Frontend connections are not changed by this phase. No SMS sending/gateway is included.
Do not restore Phase 6 source alone after migration: health/backend schema versions must match.
