# NOURISH Phase 4 — Customers, suppliers, rates and receiving accounts

Baseline: the supplied Sowad_Poltri_Backend(2).zip. Backend only; Angular and dummy
JSON files are neither changed nor needed. The Phase 3 frontend patch is not required.

1. Stop the backend.
2. From `~/Documents/Sowad_Poltri_Backend` run:
   `bash ~/Downloads/NOURISH_Backend_Phase_04.sh`
3. DBeaver: connect to `nourish_poltri_shop` as `myapp_user`, port `2001`.
   Select the WHOLE `sql/04_contacts_rates_accounts.sql` DO statement and execute.
   It is atomic and version-guarded; rerunning it is a no-op. Do not run the full
   database schema dump or repeat earlier phase migrations. No business data is seeded.
4. `mvn clean install` using full JDK 21 and Maven. Run as a normal Linux user.
5. Start ProviderApplication in IntelliJ with the existing DB environment variables,
   or export DB_PASSWORD and use `bash scripts/run.sh`. Existing admin login remains.
6. `python3 scripts/phase4-check.py` performs authenticated read-only smoke checks.
   Login/logout create normal audit events; no customer/account/rate samples are inserted.

The installer validates all changed source hashes before writing, backs up changed
files, supports `--check`, refuses conflicting local edits and is repeatable. It leaves
application.properties, Maven settings, .idea and database credentials untouched.
No SQL is run by the installer. Keep a database backup alongside source backups.
A source rollback to Phase 3 alone is not a database downgrade: health versions must match.

Expected new tables: customer, supplier, customer_product_rate, customer_package_rate,
receiving_account. Existing catalog/packaging/user/settings/audit data is preserved.
A composite unique key on existing product_packaging supports rate ownership integrity.
The five new tables start empty. JSON data import belongs to Phase 8.

Read docs/PHASE_04_API.md for request examples, roles, archive semantics, rate replacement
and upcoming invoice/payment responsibilities. No supplier payments or customer dues are
calculated in this phase. No frontend patch is included.
