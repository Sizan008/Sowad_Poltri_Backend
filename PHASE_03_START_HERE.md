# NOURISH Phase 3 — Catalog

Baseline: the supplied Sowad_Poltri_Backend(1).zip and Phase 2 schema export.

## Install
1. Stop the backend. From `~/Documents/Sowad_Poltri_Backend` run:
   `bash ~/Downloads/NOURISH_Backend_Phase_03.sh`
2. In DBeaver connect to **nourish_poltri_shop**, user **myapp_user**, port **2001**.
   Select and execute the entire `sql/03_catalog.sql` DO statement. It is atomic;
   a rerun is a no-op. Keep your normal database backup. Do not rerun a full schema dump.
3. `mvn clean install` with a full JDK 21 and Maven. Tests create a disposable PostgreSQL
   database on a random port, never the shop DB; run as your normal Linux user.
4. Configure DB_PASSWORD as usual and `bash scripts/run.sh`, or run ProviderApplication
   in IntelliJ with the existing DB environment variables. No second admin bootstrap.
5. `python3 scripts/catalog-check.py` prompts for your real application login.

The installer preflights source hashes, refuses conflicts, backs up changed files to
patch-backups, supports `--check`, and can be run twice safely. It changes no IDE,
Maven settings or application.properties files and never executes SQL.
A source backup alone is NOT a database downgrade. Phase 2 health expects version 2;
keep Phase 3 code with schema version 3.

## Delivered behavior
- Four fixed categories. Seven system units retain their IDs/codes; extra units can
  be created, renamed or deleted if unreferenced. Unit code/decimal behavior cannot
  change after registration. System units cannot be deleted.
- Product create/edit/soft archive, unique case-normalized SKU including archives,
  category/form/breed/grade validation, immutable category/base unit, no reactivation.
- Official feed names/SKUs/groups/types and catalog association cannot be rewritten
  by product payloads. Official products cannot be archived. A new unique feed SKU
  is allowed without an official catalog link.
- Product category-specific detail is stored in typed columns with category-dependent
  CHECK constraints in product, avoiding unconstrained product JSON blobs.
- 76 historical reference entries from the supplied JSON catalog; prices were not
  independently checked against original price-list images. Preserve reference dates.
  These are NOT live cost/selling rates. No customers, transaction data, demo stock,
  demo medicine/chick/fish SKUs or demo login credentials are imported.
- Product responses expose stockQuantity/costPrice/salePrice as zero compatibility
  fields in this phase. There is no inventory balance table or inventory write API.
- Packaging is a definition, not physical stock. Official feed receives definitions
  for 1/5/10/20/25/40/50 kg; custom positive sizes can be added through packaging API.
  Medicine supports g/kg and ml/L conversions; chicks/fish are one-piece definitions.
  Duplicate normalized sizes are rejected, even if inactive; inactive definitions
  are retained. This release has no packaging-reactivation endpoint.
- Product/packaging/unit mutations and authenticated audit events share a transaction.
  Viewer can read, admin/operator can write. Direct generic product writes are absent.
- Future Phase 5/6 must extend archive checks for stock and transaction history before
  enabling those operations. Current schema has no business transactions to check.

## Optional Angular integration
Apply NOURISH_Frontend_Phase_03.sh from `~/Documents/Sowad_poltri`, then
`npm ci`, `npm run typecheck`, `npm run build`, and `npm start`.
It targets the supplied Angular ZIP; conflicting edited files are not overwritten.
Login with the existing Spring Boot account, not a demo account. Tokens are memory-only;
refresh requires login. Failed server logout is reported on the login page.

The frontend becomes a catalog workspace: Products, Categories, Units, Catalog &
Packaging and admin Activity Log. Unfinished transaction/settings UI routes redirect
back to Products to avoid mixing PostgreSQL products with unrelated dummy stock/IDs.
The dummy source components remain in the project for later integration.
JSON Server is not needed for these completed screens. API URL is
`environment.catalogApiBaseUrl` (localhost:8080). Production deployment configuration
and HTTPS remain deployment tasks; the provided URLs are for your local PC.

Register a medicine/chick/fish product to populate its dependent dropdown. There are
no invented sample products for those categories. Catalog & Packaging exercises the
actual cascading API and provides package creation/deactivation.
