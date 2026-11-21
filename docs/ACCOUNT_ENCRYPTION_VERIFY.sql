-- Read-only checks AFTER scripts/account-encryption.sh migrate succeeds.
SELECT id, kind,
       CASE WHEN wallet_number IS NOT NULL
            THEN wallet_number LIKE 'enc:v1:%' END AS wallet_encrypted,
       CASE WHEN account_number IS NOT NULL
            THEN account_number LIKE 'enc:v1:%' END AS bank_number_encrypted
FROM nourish.receiving_account
ORDER BY id;

SELECT count(*) AS unprotected_payment_snapshots
FROM nourish.payment
WHERE account_snapshot IS NOT NULL
  AND NOT nourish.account_json_protected(account_snapshot);

SELECT id, applied_at FROM nourish.account_crypto_state;

SELECT tgrelid::regclass AS table_name, tgname, tgenabled
FROM pg_trigger
WHERE NOT tgisinternal
  AND tgrelid IN ('nourish.payment'::regclass,
                 'nourish.migration_batch'::regclass,
                 'nourish.migration_record'::regclass,
                 'nourish.migration_mapping'::regclass)
ORDER BY table_name, tgname;
-- Expected tgenabled='O' (enabled for ordinary sessions).

SELECT * FROM nourish.reconciliation_issue ORDER BY check_name, entity_id;
-- Expected zero reconciliation issues; investigate existing issues separately.
