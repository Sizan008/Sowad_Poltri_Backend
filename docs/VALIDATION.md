# Verification record

Source: Sowad_Poltri_Backend(20261121-081052).zip. Tests use a separate embedded PostgreSQL instance and synthetic records, not the user's database.

A full Maven test run found one failure:
`FoundationIntegrationTest.phase6ValidationMissingRateInsufficientPackAndStalePrice`
expected HTTP 409 but received 201. Running that exact test against the unmodified source reproduced the same failure. This patch does not change sales-pricing behavior or silently rewrite that assertion.

For this encryption patch, build and run all remaining tests with:

```bash
mvn clean install -Dsurefire.failIfNoSpecifiedTests=false '-Dtest=*,!FoundationIntegrationTest#phase6ValidationMissingRateInsufficientPackAndStalePrice'
```

The exclusion is temporary and explicit. It does not mean the whole application has passed every original test. Resolve the pre-existing pricing/test mismatch separately before claiming full release verification.

Covered by added or extended tests:
- AES-GCM round trip, randomized ciphertext, wrong key/context/tamper/plaintext rejection.
- Stable distinct keyed lookup values.
- Existing bank/MFS account migration and historical payment snapshot preservation.
- Nested migration JSON protection and plaintext snapshot rejection.
- Immutable trigger restoration and rollback of schema/data after an injected migration failure.
- Account lifecycle, normalization, duplicate rejection, search and authorization.
- Original payment snapshot returned through the API after account changes.

One existing optional external-data integration test is skipped when its external source fixture is absent. Do not use a test key from source code for real data; use scripts/init-account-key.sh.

Final selected test run: 89 tests reported, 88 passed, 1 existing optional test skipped, zero failures/errors; the known baseline failing pricing test was explicitly excluded as shown above.
