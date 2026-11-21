# NOURISH Phase 6 — Sales invoices, payments and customer due

Baseline: supplied Sowad_Poltri_Backend(4).zip. Backend only; no frontend patch required.

1. Stop the backend and retain a database backup.
2. From ~/Documents/Sowad_Poltri_Backend:
   `bash ~/Downloads/NOURISH_Backend_Phase_06.sh`
3. DBeaver: existing myapp_user connection to nourish_poltri_shop, port 2001.
   Execute the WHOLE sql/06_sales_payments_due.sql DO block. Do not rerun older migrations.
   It is atomic and a repeat run is a version-guarded no-op.
4. Full JDK 21: `mvn clean install` as a normal Linux user.
5. Start ProviderApplication in IntelliJ with existing database variables, or use
   `bash scripts/run.sh` in your terminal with DB_PASSWORD already exported.
6. In another terminal: `python3 scripts/phase6-check.py`, using application credentials.
   Reads business data only; login/logout create normal audit entries.
7. docs/PHASE_06_API.md contains the full sales/payment contract and examples.

Installer validates all source hashes before changing anything, saves changed originals
under patch-backups, refuses conflicting local edits, supports --check and repeat runs.
It does not execute SQL or change credentials, Maven/POM or IntelliJ configuration.
The migration preserves Phase 1–5 data and extends stock history to accept sales.
New financial tables start empty; no demo purchases, sales or payments are imported.

```sql
SELECT version,description FROM nourish.schema_version ORDER BY version;
SELECT (SELECT count(*) FROM nourish.invoice) AS invoices,
       (SELECT count(*) FROM nourish.invoice_item) AS items,
       (SELECT count(*) FROM nourish.payment) AS payments;
SELECT * FROM nourish.customer_financial_summary ORDER BY customer_id;
-- Must return zero rows:
SELECT i.id,i.total_amount,sum(p.amount) AS paid
FROM nourish.invoice i JOIN nourish.payment p ON p.invoice_id=i.id
GROUP BY i.id,i.total_amount HAVING sum(p.amount)>i.total_amount;
```

Version 6 is expected. DO Updated Rows: 0 is normal; verify the version and API health.
Do not restore Phase 5 source alone after applying schema 6; health versions must match.
Invoice download returns JSON containing print-ready data/snapshots, not a rendered PDF.
