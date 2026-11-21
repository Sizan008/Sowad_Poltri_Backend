# Receiving-account encryption — installation guide

Based on Sowad_Poltri_Backend(20261121-081052).zip. This patch protects bKash/Nagad receiving-wallet numbers and bank receiving-account numbers using AES-256-GCM. Authorized API responses still contain the original numbers. Passwords remain BCrypt(12); BCrypt has no public/private key. DB_PASSWORD is the database connection password.

## What changes

- Encrypt receiving_account.wallet_number/account_number and corresponding payment.account_snapshot fields. Historical snapshots retain their original values, independent of later account edits.
- Encrypt known accountNumber/walletNumber (and snake_case equivalents) in migration JSON, including future generated staging SQL.
- Keyed lookup values preserve duplicate-account protection. Account search decrypts the small receiving-account list before substring filtering and pagination.
- A single offline transaction converts existing data, adjusts constraints/indexes, and restores immutable triggers. Any failure rolls back the whole transaction.
- The application refuses startup if the key is missing/wrong or migration has not run. The migration is repeatable with the original key.
- schema_version remains 8; account_crypto_state tracks this separate change.
- No frontend patch is required. Existing API authorization remains unchanged.

Not covered: payment.sender_number, customer/supplier phone numbers, names, labels, free-text notes, logs, original JSON files or previous backups. Do not put account numbers into free-text fields. This does not protect against a compromised application with access to its key. Keep HTTPS and existing authorization in production.

## 1. Stop the backend and back up

Stop the IntelliJ Run process or terminal backend. Do not run another backend during migration.

```bash
cd ~/Documents/Sowad_Poltri_Backend
pg_dump -h 127.0.0.1 -p 2001 -U postgres -W \
  -d nourish_poltri_shop -Fc \
  -f "$HOME/Downloads/nourish-before-account-encryption.dump"
```

This backup contains the old plaintext numbers. Protect the backup. Use your actual database owner if it is not postgres.

## 2. Apply the downloaded shell patch

```bash
cd ~/Documents/Sowad_Poltri_Backend
bash ~/Downloads/NOURISH_Backend_Account_Encryption.sh --check
bash ~/Downloads/NOURISH_Backend_Account_Encryption.sh
```

If you downloaded ZIP instead, extract it in Downloads first. The installer checks source hashes before writing, backs up changed files under patch-backups, and refuses conflicting changes. Do not force it over a different backend version.

Build instructions and test status are in VALIDATION.md shipped with this patch.

## 3. Create the encryption key ONCE

```bash
bash scripts/init-account-key.sh
export ACCOUNT_DATA_KEY_FILE="$HOME/.config/nourish/account-data.key"
```

The generated key is random and owner-readable only. Back up this exact file securely, separately from the database backup. Never replace/regenerate it after migrating: old ciphertext requires the original key. Key rotation requires a separate migration; it is not implemented here.

Exactly one of ACCOUNT_DATA_KEY_FILE or ACCOUNT_DATA_KEY may be configured. The recommended key-file option avoids copying a raw key into IntelliJ settings. application.properties contains placeholders, not the key itself. Do not commit the key or put it in Angular.

## 4. Run the offline database migration

Build the jar before this step. The database owner must be allowed to ALTER the affected tables. The normal application credentials may not have that permission.

```bash
export DB_HOST=127.0.0.1
export DB_PORT=2001
export DB_NAME=nourish_poltri_shop
export MIGRATION_DB_USER=postgres
read -r -s -p 'PostgreSQL owner password: ' MIGRATION_DB_PASSWORD
printf '\n'
export MIGRATION_DB_PASSWORD
bash scripts/account-encryption.sh migrate
bash scripts/account-encryption.sh verify
unset MIGRATION_DB_USER MIGRATION_DB_PASSWORD
```

Proceed only after PASS. A failure rolls back DDL and data, including trigger changes. Do not manually disable triggers. The migration performs database alterations and encryption together; no additional modifying SQL should be run in DBeaver. This keeps the encryption key out of SQL text/history.

## 5. Run the backend

For IntelliJ: Run → Edit Configurations → select the existing backend application → Environment variables → add:

```
ACCOUNT_DATA_KEY_FILE=/home/sizan/.config/nourish/account-data.key
```

Retain DB_HOST, DB_PORT, DB_NAME, DB_USER and DB_PASSWORD in the same run configuration. Do not enable sharing of secret-containing run configurations. Apply → Run.

For terminal startup (variables must be present in this terminal):

```bash
export ACCOUNT_DATA_KEY_FILE="$HOME/.config/nourish/account-data.key"
export DB_HOST=127.0.0.1 DB_PORT=2001 DB_NAME=nourish_poltri_shop DB_USER=myapp_user
read -r -s -p 'PostgreSQL app password: ' DB_PASSWORD
printf '\n'
export DB_PASSWORD
bash scripts/run.sh
```

The existing nourish-provider Maven startup can also be used with those environment variables. IntelliJ variables are not automatically exported to its terminal.

## 6. Verify

Run ACCOUNT_ENCRYPTION_VERIFY.sql in DBeaver after the migration. Encrypted flags should be true for the applicable number, unprotected snapshots should be zero, history triggers should be enabled, and reconciliation should return no issues. The verify command additionally authenticates and decrypts ciphertext without printing numbers.

Check Receiving Accounts in the UI and a previous bank/MFS payment invoice. They should still show original numbers. Test account create/edit, duplicate rejection and search. The screenshot supplied for this task showed two accounts and one historical snapshot; this patch has not been run against your local database.

## Legacy imports / recovery / hosting

- Build the patched jar and supply the same key before running scripts/validate-legacy.py. Its generated staging SQL now contains encrypted structured account fields. Regenerate old staging SQL rather than importing plaintext.
- Existing source JSON and local validation reports remain sensitive files; protect or remove them according to your retention needs.
- If migration fails, keep the original key and resolve the reported prerequisites; do not invent a replacement key.
- After a successful migration, restoring only old application code is unsafe. For rollback, stop the backend and restore the pre-migration database into a separate/recreated database with its matching pre-patch code. Verify before switching traffic. Do not overwrite a live database without a recovery plan.
- For hosting, mount the same key as an owner-readable secret file and set ACCOUNT_DATA_KEY_FILE to its server path, or supply ACCOUNT_DATA_KEY through the hosting secret manager. Keep the key outside source control and database backups. Securely retain it for database restores.
