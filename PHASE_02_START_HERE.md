# Phase 2 — install and run

1. Stop the running Phase 1 application with Ctrl+C.
2. From ~/Documents/Sowad_Poltri_Backend run:
   bash ~/Downloads/NOURISH_Backend_Phase_02.sh
3. DBeaver: current database nourish_poltri_shop, user myapp_user, port 2001.
   Run sql/02_identity_settings_audit.sql (also supplied in chat). It is one DO statement:
   select the WHOLE block from DO $phase2$ through $phase2$; and execute the selection.
   There is no separate BEGIN/COMMIT block to execute. Failure rolls the whole statement
   back. An existing version 2 is a no-op. Do not run Phase 1 SQL again.
4. Verify SELECT version,description FROM nourish.schema_version ORDER BY version;
   Expect versions 1 and 2. No user/password is seeded by SQL.
5. mvn -U clean install  (Maven Central settings remain as configured.)
6. In the same terminal:
   export DB_HOST=127.0.0.1 DB_PORT=2001 DB_NAME=nourish_poltri_shop DB_USER=myapp_user
   read -r -s -p 'PostgreSQL password: ' DB_PASSWORD; printf '\n'; export DB_PASSWORD
7. First setup only: bash scripts/create-admin.sh
   Enter YOUR new app username, display name, password and confirmation. This login
   password is separate from DB_PASSWORD. >=12 characters and <=72 UTF-8 bytes.
   Setup runs locally without an HTTP endpoint, refuses any preexisting user and
   hashes the password. No frontend demo credentials are used.
8. bash scripts/run.sh
9. In another terminal from project root:
   curl -i http://127.0.0.1:8080/health
   python3 scripts/auth-check.py
   Expected: phase=2, schemaVersion=2; auth-check reports login/me/settings/audit/logout.
   For admin, auth-check resubmits existing settings values and writes an audit event.
10. Optional: python3 scripts/create-user.py
    Authenticate as admin and create an operator/viewer. Run auth-check as the new user:
    settings read works, settings write/audit return 403, logout invalidates the token.

No existing file needs manual replacement. The installer backs up changed source files,
checks the Phase 1 prerequisite and refuses unexpectedly edited source files before writes.
It preserves local application.properties when modified and leaves Maven config/.idea alone.
If interrupted by a source conflict, send the exact message; do not force overwrite.
Database migration is manual. Do not downgrade to Phase 1 code after migration without
planning schema/code compatibility. Keep the backup until Phase 2 checks pass.

Read docs/PHASE_02_FRONTEND_INTEGRATION.md for the exact Angular integration plan.
A full Angular patch is not included; the existing demo frontend remains unchanged.
For IDE run configuration, set DB_HOST, DB_PORT=2001, DB_NAME, DB_USER, DB_PASSWORD;
run com.sowad.nourish.ProviderApplication normally without bootstrap flags.

Scope: user creation/listing, login/me/logout, fixed roles, settings, audit foundation.
No password reset/change, refresh token, role-edit UI or public registration endpoint yet.
No auto database migration. Retention/purge of expired sessions and audit history will
be planned before deployment; login throttling currently applies per existing account.
