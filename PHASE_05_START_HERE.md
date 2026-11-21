# NOURISH Phase 5 — Purchase and inventory

Backend only, based on Sowad_Poltri_Backend(3).zip. No frontend patch required.

1. Stop the backend and keep a database backup.
2. From `~/Documents/Sowad_Poltri_Backend` run:
   `bash ~/Downloads/NOURISH_Backend_Phase_05.sh`
3. DBeaver: connect to `nourish_poltri_shop`, existing myapp_user, port 2001.
   Execute the WHOLE `sql/05_purchase_inventory.sql` DO block. No previous SQL must be
   rerun. The migration is atomic and repeat execution is a version-guarded no-op.
4. Use full JDK 21; run `mvn clean install` (normal Linux user).
5. Start IntelliJ ProviderApplication with existing DB variables or run
   `bash scripts/run.sh` in the terminal with DB_PASSWORD already exported.
6. In another terminal run `python3 scripts/phase5-check.py`, using your app login.
   This only reads business data; login/logout create normal audit records.
7. Follow docs/PHASE_05_API.md for posting purchases/adjustments and their request keys.

Installer preflights all source hashes, refuses conflicting local changes, backs up
changed files under patch-backups, and supports --check. It never executes SQL and does
not change Maven settings, credentials, application.properties or IntelliJ configuration.
Do not restore Phase 4 source alone after Phase 5 migration: health expects schema 5.

Verification:

```sql
SELECT version,description FROM nourish.schema_version ORDER BY version;
SELECT (SELECT count(*) FROM nourish.purchase) AS purchases,
       (SELECT count(*) FROM nourish.purchase_item) AS items,
       (SELECT count(*) FROM nourish.inventory_balance) AS balances,
       (SELECT count(*) FROM nourish.stock_movement) AS movements;
-- Expected zero rows for mismatched balances:
SELECT b.product_id,b.quantity,coalesce(sum(m.quantity_change),0) AS ledger_quantity
FROM nourish.inventory_balance b LEFT JOIN nourish.stock_movement m ON m.product_id=b.product_id
GROUP BY b.product_id,b.quantity HAVING b.quantity<>coalesce(sum(m.quantity_change),0);
```

First install has zero purchase/stock rows; no demo data imported. Existing Phase 1–4
users/catalog/customers/suppliers/rates/accounts remain. DO Updated Rows: 0 is normal;
check schema_version contains 5 and run the smoke script after starting the backend.
