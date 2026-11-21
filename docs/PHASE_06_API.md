# Phase 6 — Sales invoices, payments and customer due

Based on supplied Sowad_Poltri_Backend(4).zip. Backend only; no frontend patch.
Requires database phases 1–5, JDK 21 and existing bearer-token authentication.

## Routes

| Method / endpoint | Purpose | Roles |
|---|---|---|
| POST /transactions/sales | Create invoice, deduct exact stock, optionally collect initial payment | admin/operator |
| POST /transactions/payments | Collect a payment against one invoice | admin/operator |
| GET /invoices?customerId=&status=&limit=50&offset=0 | Invoice list; status unpaid/partial/paid | admin/operator/viewer |
| GET /invoices/{id}, /invoices/{id}/details | Header, items, snapshots, payments and current due | all three |
| GET /invoices/{id}/download | Same complete data as downloadable JSON attachment | all three |
| GET /invoiceItems?invoiceId= | Historical item data and rate/package snapshots | all three |
| GET /payments?invoiceId=&customerId=&limit=50&offset=0 | Payment records | all three |
| GET /payments/{id} | One payment including receiving-account snapshot | all three |
| GET /customers/{id}/due | Customer billed/paid/due totals and categories | all three |
| GET /customers/{id}/ledger?limit=50&offset=0 | Invoice debit/payment credit, running due, overall summary | all three |
| GET /customerInsights?billCategory=&buyingCategoryId=&limit=50&offset=0 | Customer financial list/filter | all three |
| GET /customers/{id}/details | Existing customer data plus real transactionSummary | all three |
| GET /stockMovements?type=sale&productId= | Existing history now supports sale movements | all three |

All lists are arrays except ledger, which returns {summary,entries,limit,offset}.
Pagination limit is 1–200; offset is nonnegative. Customer financial summaries include
archived customers so outstanding balances remain collectible. Existing /customers list
and rate APIs retain their contracts; use /customerInsights for billing/buying filters.
Download is JSON data, not a rendered PDF. A future frontend can render/print its
customer/business/product/rate snapshots without depending on current master data.

## Invoice request

Both POST routes REQUIRE `Idempotency-Key: <UUID or 8–80 letters/digits/hyphens/underscores>`.
Use one key per logical operation and keep it for retries. Same key + same decoded body
returns the same invoice/payment ID without repeating stock/payment effects. Changed body
or operation with the same key returns 409. Failed requests do not reserve the key.
Invoice retries return the invoice's current paid/due summary, which may have changed
through later collections; immutable invoice/item snapshots remain identical.

Example: two 25 kg feed bags, customer-specific or global server-configured rate:

```json
{
  "customerId": "CUSTOMER_ID",
  "issuedAt": "2026-11-11T07:00:00Z",
  "dueDate": "2026-11-20T07:00:00Z",
  "discountAmount": 100,
  "initialPaid": 200,
  "paymentMethod": "cash",
  "notes": "Delivered to shop",
  "lastNote": "Collect remaining amount next week",
  "items": [{
    "productId": "FEED_PRODUCT_ID",
    "packages": [{"bagSizeKg": 25, "bagCount": 2}]
  }]
}
```

At 20/kg this produces subtotal 1,000, total 900, paid 200, due 700; deducts exactly
two 25 kg bags and 50 kg total stock. Product cost and selling defaults are not changed.

Medicine item: `medicinePacks:[{"amount":0.25,"unitCode":"l","packCount":2}]` for
registered 250 ml bottles. Chicks/fish use `quantity:10`. Feed/medicine require physical
package counts; total quantity is derived. Optional quantity/medicine measurement must
match that derived amount. Each product appears once, with up to 50 sizes; max 100 items.
Counts are positive integers. No loose/open-package sale workflow is introduced.

### Server rate precedence

1. Customer-specific rate for the exact packaging ID.
2. Customer-specific base-unit rate × package size.
3. Global product base-unit selling rate × package size.

For chicks/fish (piece sales), use customer unit rate then global unit rate. Piece-pack
rate mappings are not used, since pieces do not select a packaging definition.
Missing rates return 409; official catalog reference MRP and purchase suggestions are
not silently substituted for a global selling rate. Configure the customer rate through
Phase 4 or explicitly set the global rate through Phase 5 purchase pricing.

Client unitPrice may be omitted. If supplied, it must equal the server's effective unit
price (line total / base quantity, rounded HALF_UP to 6 decimals); mismatches return 409.
No manual price override is introduced. pricingBasis unit/package is accepted for client
compatibility; server-selected rate precedence remains authoritative. purchasePricing,
client totals, IDs and unknown fields are rejected. JSON numeric strings/duplicate keys
and fractional package counts are rejected.

Derived selling price per package is rounded HALF_UP to 2 decimals BEFORE multiplication
by the integer package count. Each row's snapshot records the selected rate source,
customer rates, package definition amount/unit/type/size and resolved selling price.
The invoice stores transaction-time product name/SKU/category/unit and customer/business
snapshots. Changing customer rates, product names or business details later does not
change historical invoice data. lastNote belongs to the invoice snapshot; it does not
replace the customer master note.

Discount is a flat amount, not a percentage. Discount/initial payment accept up to
2 decimals and 1 billion; invoice total must remain positive and initial payment cannot
exceed it. No tax or fee rules are added. Issued timestamp must be from 2000 through now
plus one day; due date is on/after issue date in Asia/Dhaka and before 2100.

Invoice numbers use a PostgreSQL sequence: INV-<issue-year>-<sequence padded to 8 digits>.
The sequence does not reset yearly. Failed transactions may leave sequence gaps; numbers
are unique and never reused. No editable invoice header, cancellation, return or refund
API is included in this phase.

## Payments

Example invoice-wise cash collection:

```json
{
  "invoiceId": "INVOICE_ID",
  "amount": 300,
  "paidAt": "2026-11-11T08:00:00Z",
  "method": "cash",
  "note": "Partial collection"
}
```

Bank: method `bank` plus receivingAccountId. paymentProvider may be omitted or `bank`.
MFS: method `mobile-banking`, matching receivingAccountId and paymentProvider `bkash`
or `nagad`. Optional senderNumber accepts local or +880/880 mobile format and is normalized.
Optional transactionRef is limited to 80 characters.

```json
{
  "invoiceId": "INVOICE_ID",
  "amount": 400,
  "paidAt": "2026-11-11T09:00:00Z",
  "method": "mobile-banking",
  "receivingAccountId": "NAGAD_ACCOUNT_ID",
  "paymentProvider": "nagad",
  "senderNumber": "01712345678",
  "transactionRef": "ACTUAL-TRANSFER-REFERENCE",
  "note": "Final collection"
}
```

Initial payment uses these same destination fields at the invoice request's top level,
with paymentMethod instead of method. Its paidAt equals issuedAt. When initialPaid is
zero, omit receiving account/provider/sender/reference fields. Cash must omit all bank/MFS
metadata. Noncash requires an active matching receiving account, captured under lock.
Snapshots preserve label, account holder/number/bank or MFS provider/wallet even if the
account is later renamed or deactivated. Payment records never expose a PIN/password.

Payments are positive, max 1 billion, at most 2 decimals, cannot predate the invoice issue
time and cannot exceed remaining due. Collection against an inactive/archived customer's
existing invoice is allowed; new invoices for that customer are blocked.
A nonempty transactionRef is unique, case-insensitively, per receiving account, including
historical payments. This prevents recording one referenced transfer twice; this phase
supports one invoice per referenced transfer, not splitting a bank/MFS transfer across
several invoices. Cash and blank references rely on the idempotency key and due checks.

## Customer due, categories and ledger

Due is derived: sum(invoice totals after discount) minus sum(invoice-linked payments).
No mutable duplicate due balance is stored. Per-invoice status is unpaid/partial/paid.
Customer billCategory is:

- no-invoices: no invoice exists;
- due: remaining customer due > 0;
- paid: invoices exist and all remaining due is zero.

Buying categories come from invoice-item historical category snapshots, not master customer
settings. Summary includes buyingCategoryIds, buyingCategoryNames and buyingCategories.
Ledger sorts business timestamp, invoice-before-payment for ties, then ID; runningDue
includes entries before the requested page. Later backdated entries can change displayed
historical running balances, while stored invoices/payments remain immutable.

## Atomicity and concurrency

Invoice posting locks customer (serializing rate edits), then products in sorted order,
then any receiving account. Existing Phase 5 product locks protect purchases/adjustments
against simultaneous sales. Both total stock and the selected package counts must be
available: enough kg in other bag sizes cannot satisfy a missing requested size.

Collection locks the invoice before calculating due. Concurrent payments cannot exceed
its total. A database insert trigger also locks/checks invoice amount, customer and dates.
Invoice header/items/package details, negative stock movements, package balances, initial
payment, account snapshot, audit and request key commit together. Failure rolls them all
back. Standalone payment, audit and request key also commit together. Sequence gaps are
the intentional exception to rollback; they do not represent posted invoices.

Deferred SQL constraints reconcile invoice totals, package quantities and exact sale
movement counts. Foreign keys preserve customer/product/account relationships. Posted
invoices, items and payments are immutable; previous phase purchase/stock checks stay in
force. The new views add no fake balance or demo data.
