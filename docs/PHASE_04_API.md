# Phase 4 API contract

Backend only. No Angular files changed and the Phase 3 frontend patch is NOT a prerequisite.
All routes require the existing Phase 2 Bearer token. Success uses direct objects/arrays;
failures retain code/message/fieldErrors/requestId/timestamp. Request bodies are strict:
unknown fields, IDs, computed balances and embedded rate edits are rejected.

| Operation | Endpoint | Access |
|---|---|---|
| Customer list/detail | GET /customers; GET /customers/{id} | admin/operator/viewer |
| Customer create/update | POST /catalog/customers; PUT /catalog/customers/{id} | admin/operator |
| Customer archive | DELETE /customers/{id} (204) | admin/operator |
| Customer details foundation | GET /customers/{id}/details | admin/operator/viewer |
| Rates read | GET /customers/{id}/rates | admin/operator/viewer |
| Rates replace | PUT /customers/{id}/rates | admin/operator |
| Supplier list/detail | GET /suppliers; GET /suppliers/{id} | admin/operator/viewer |
| Supplier create/update | POST /suppliers; PUT /suppliers/{id} | admin/operator |
| Supplier archive | DELETE /suppliers/{id} (204) | admin/operator |
| Supplier details foundation | GET /suppliers/{id}/details | admin/operator/viewer |
| Account list/detail | GET /receivingAccounts; GET /receivingAccounts/{id} | admin/operator/viewer |
| Account create/update | POST /transactions/receiving-accounts; PUT /transactions/receiving-accounts/{id} | admin |
| Account deactivate | DELETE /transactions/receiving-accounts/{id} (200 account object) | admin |

Receiving destinations are financial configuration, so operator/viewer cannot change
where money is received. Customer/supplier and rate operations remain admin/operator.

## Contact commands

POST and PUT use the same identity/contact fields:

```json
{
  "nameEn": "Sowad Store",
  "nameBn": "সওদা স্টোর",
  "addressEn": "Dhaka Road 12",
  "addressBn": "ঢাকা রোড ১২",
  "phone": "01712345678",
  "whatsappPhone": "01812345678",
  "status": "active",
  "note": "Regular customer"
}
```

English name (2–120) and address (2–240), mobile and active/inactive status are required.
English text must be printable ASCII with at least one English letter; Bangla fields are
optional. Notes are at most 500 characters. `lastNote` is accepted as a note alias; giving
both with different values is rejected. Omitted note preserves an existing note; explicit
empty string clears it. Other optional text fields follow PUT replacement semantics.
No automatic translation or phonetic conversion happens in the backend.

Phones accept local 01XXXXXXXXX, 8801XXXXXXXXX or +8801XXXXXXXXX, plus common spaces,
hyphens and parentheses. Stored/returned form is the 11-digit local number, English
digits only, prefix 013–019. WhatsApp is optional. Uniqueness is per customer table and
per supplier table, including inactive and archived records; a person can be both.

The server generates id/createdAt/updatedAt. Response `name` aliases nameEn and `area`
aliases addressEn. `note` and `lastNote` return the same saved text. Current Angular
commands that send client id/name/area/createdAt must be adapted during Phase 8; the
server does not trust those fields or import their IDs through ordinary creation.

`status=inactive` can be changed back to active through PUT. DELETE is a permanent soft
archive: sets archived=true,status=inactive, retains the row/rates/ID, and rejects later
edits. This phase has no restore endpoint. Repeated DELETE is harmless. No hard deletes.
Phase 6 must add any required unsettled-balance policy before enabling payment flows.

## Search and list shape

Customers/suppliers: `q`, `status=active|inactive`, `archived=true|false`, optional
`limit=1..200`, `offset>=0`. Default lists include non-archived active/inactive records.
`archived=true` returns archived records only. Individual GET still retrieves archives.
Search matches English/Bangla name/address, phone and WhatsApp; it is case-insensitive
and treats SQL wildcard characters literally. Search a canonical/local phone or a fragment.
Lists use stable name+ID ordering. Without limit they retain the full-array contract.
Rates are fetched in batches, not one additional query per customer.

Accounts: `q`, `kind=bank|mfs`, `active=true|false`, optional limit and offset. Default
includes both active/inactive accounts, sorted by label+ID. q searches label, bank/holder,
account/wallet number and provider. Indexes support uniqueness, joins, exact phone,
status/kind filters and ordering; arbitrary substring searches are not indexed full-text.

## Customer-specific rates

PUT is an **atomic full replacement** of this customer's current unit AND package rates.
`{"rates":[]}` clears both. At most 100 entries. The returned body is the customer.

```json
{
  "rates": [
    {"productId":"prod-nourish-3103","pricePerUnit":75.123456,"unit":"kg"},
    {"productId":"prod-nourish-3103","packagingId":"pack-3103-25","pricePerPackage":1800}
  ]
}
```

Unit rates: >0, <=1,000,000, at most 6 decimals. `unit`, when supplied, must match the
product BASE unit (kg/g/ml/piece); never silently treat per-litre as per-ml. Legacy
`pricePerKg` is accepted for Feed only; if also supplying pricePerUnit, values must agree.
Package rates: >0, <=1,000,000,000, at most 2 decimals; only pricePerPackage is accepted
for a packagingId. That definition must belong to the specified product and be active.
Duplicate product-unit entries or duplicate packaging entries are rejected. A unit and
multiple distinct package rates can coexist for the same product.

Customer must be active/non-archived and products non-archived at write time. Reads retain
old configurations after product/package deactivation and expose productArchived /
packagingActive flags. A deactivated reference is not usable for a new transaction.

Customer response: `customerRates` contains base-unit entries (legacy-compatible) and
`packageRates` contains the new explicit package entries. GET /customers/{id}/rates
returns `{customerId,rates:[...both kinds...]}`. Rate writes cannot alter product global
prices. Actual sale rate selection, invoice calculation and historical snapshots are
Phase 6 work. Intended selection: exact customer package rate, then customer base-unit
rate, then applicable global pricing; Phase 6 must validate/implement that rule.

Rates are configuration, not immutable transaction records. Replacement removes obsolete
configuration rows; future invoices must keep their own agreed-rate snapshot. The audit
records the authenticated actor and replacement action without storing contact/account
secrets in log text. Customer/product locks serialize replacement and archive races;
unique keys/FKs remain authoritative against concurrent duplicate submissions.

## Receiving accounts

```json
{"kind":"mfs","label":"Shop bKash","active":true,"provider":"bkash","walletNumber":"01712345678"}
```

```json
{"kind":"bank","label":"Shop Bank","active":true,"bankName":"Example Bank","accountNumber":"0012345678","accountName":"Sowad Store"}
```

MFS: bKash or Nagad; wallet normalized as mobile above. Bank: bankName and accountName
required, accountNumber 4–40 ASCII alphanumeric characters after removing separators
(space, hyphen, slash) and uppercasing. Leading zeros remain. Kind cannot change; other
valid account fields can be corrected. Deactivation retains ID; PUT active=true reactivates.
Duplicate provider+wallet or normalized bank+account number is rejected, including inactive
accounts. Bank and MFS fields cannot be mixed; passwords/PINs/tokens are never accepted.
Phase 6 must snapshot account details on payments so later corrections never rewrite history.

## Details foundation

`/customers/{id}/details` returns `{customer,transactionSummaryAvailable:false,transactionSummary:null}`.
Supplier equivalent uses `supplier`. No fabricated zero due, invoice count, ledger,
purchase totals or payment aggregates are returned; those require future transaction phases.
