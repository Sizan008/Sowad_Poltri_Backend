# Phase 7 — Dashboard, reports and supporting features

Baseline: supplied Sowad_Poltri_Backend(5).zip. Backend only, preserving the Phase 1–6
contracts and data. No Angular patch and no SMS gateway, delivery queue or send action.
Existing opaque Bearer tokens are required.

## Dashboard

All three roles (admin/operator/viewer) can read these endpoints.

| Endpoint | Response |
|---|---|
| GET /dashboard or /dashboard/summary | Existing DashboardViewModel field names plus metadata/count |
| GET /dashboard/chart?from=2026-11-01&to=2026-11-11 | Daily [{date,label,sales,purchases}], with zero-filled dates |
| GET /dashboard/recent-invoices?limit=5 | Recent invoice list, newest issuedAt first |
| GET /dashboard/reminders?limit=20&offset=0 | One unpaid reminder per customer, earliest unpaid due date |
| GET /dashboard/low-stock?limit=50&offset=0 | Current active product alerts, including zero stock |

Dashboard summary: todaySales, yesterdaySales, totalDue, dueCustomerCount, stockValue,
totalStockWeightKg, activeCustomers, newCustomersThisMonth, salesGrowthPercent,
chartPoints (7 days), reminders (first 3), recentInvoices (first 5), stockAlerts (first 200),
stockAlertCount (full count), date, timeZone, balanceBasis.

Optional `date=YYYY-MM-DD` anchors dashboard daily/month sales metrics and seven chart
dates; defaults to today in Asia/Dhaka. Due, current stock and recent records remain
current, NOT historical balances at that date. Sales use discounted invoice totals.
Yesterday zero gives growth 0, preserving existing UI behavior. New customers include all
created in the selected month; activeCustomers excludes archived/inactive customers.
Stock value is current quantity × latest purchase unit cost, not FIFO/accounting cost.
Only kg products contribute to totalStockWeightKg; pieces/ml/g are not summed as kg.

Reminders include archived/inactive customers with outstanding invoices and both overdue
and future-due amounts; no money disappears when a customer is archived. Each reminder
sums only unpaid invoice balances and takes their earliest due date. No SMS is sent.
Recent invoice status uses the dashboard UI's paid/partial/due labels. Low-stock is
quantity <= configured product threshold; archived products are excluded.

Chart dates are inclusive; max 366 days. Its default is the last seven Bangladesh dates.
Labels are stable English DD Mon strings; clients may localize using the ISO date field.
Paginated dashboard lists accept limit 1–200 and offset >=0.

## Reports

GET `/reports/{type}` accepts `sales`, `purchases`, `stock`, `dues`, `expenses`.
All three roles can read. Example:

`/reports/sales?from=2026-11-01&to=2026-11-11&customerId=CUSTOMER_ID&limit=50&offset=0`

Response:

```json
{
  "type": "sales",
  "from": "2026-11-01",
  "to": "2026-11-11",
  "timeZone": "Asia/Dhaka",
  "total": 2,
  "summary": {"count": 2, "invoiceAmount": 140, "paidAmount": 70, "dueAmount": 70},
  "items": [],
  "limit": 50,
  "offset": 0,
  "paymentBasis": "current collections against selected invoices"
}
```

The empty items above is a shape illustration. Actual rows use the existing report-model
field names. Summary aggregates ALL matching rows before pagination, not just the page.
Empty reports return items [], count/amounts 0. Grouped sums run in PostgreSQL/JDBC;
invoice payments are preaggregated once per invoice to avoid multiplication from joins.

| Type | Optional entity filter | Date basis | Defaults / totals |
|---|---|---|---|
| sales | customerId | Invoice issuedAt | Last 30 dates; invoiceAmount/paidAmount/dueAmount |
| purchases | supplierId | Purchase purchasedAt | Last 30 dates; totalAmount |
| expenses | category (rent/utilities/salary/transport/maintenance/supplies/other) | spentAt | Last 30 dates; amount and byCategory |
| dues | customerId | Selected invoices' issuedAt | All invoices if dates omitted; only customers with remaining due |
| stock | productId, category (category ID) | Stock movement occurredAt if dates given | Current balances if dates omitted |

Supply both from and to, or neither. YYYY-MM-DD dates are inclusive in Asia/Dhaka,
implemented as >= local midnight and < midnight AFTER to. Valid range 2000–2099, maximum
3660 inclusive days; reversed/partial ranges are rejected. Page limit 1–200, offset >=0.
Unsupported filter/type combinations (e.g. customerId on stock) are rejected.

Sales and dues paid/due figures always use CURRENT collections against the selected
invoices, including payments outside the invoice-date interval. They are NOT historical
receivables as of to, nor a payment cash-flow report. Due rows include all selected invoice
amounts/payments for each customer that still owes money, with earliest UNPAID due date.
Names on sales/purchase reports use transaction-time snapshots; due contact details use
the current customer record so reminders have current phone/name.

Stock without dates reads current non-archived product balances. With dates, it includes
registered products (including archived) and sums movement business timestamps to derive
openingQuantity (< from), quantityChange (within range), and quantity (closing through to).
Backdated movements may change those historical totals; business-date ordering can show
negative intermediate historical stock even though posting-time balances never go negative.
Catalog names, thresholds and costPrice are current. stockValue uses latest purchase cost,
NOT historical cost or FIFO. Metadata states this basis explicitly. No historical catalog
existence/valuation reconstruction is implied. quantitiesByUnit keeps kg/g/ml/piece totals
separate; there is no meaningless mixed-unit grand quantity. Opening/change fields are
zero when dates are omitted and only current quantity is applicable.

## Expenses

| Method / endpoint | Access |
|---|---|
| GET /expenses?from=&to=&q=&category=&method=&limit=50&offset=0 | all three roles |
| GET /expenses/{id} | all three roles |
| POST /transactions/expenses | admin/operator; Idempotency-Key required |
| PUT /transactions/expenses/{id} | admin/operator |
| DELETE /transactions/expenses/{id}?expectedRevision=1 | admin/operator |
| GET /expenses/{id}/history | admin only, also available after deletion |

Example create body:

```json
{
  "title": "Delivery transport",
  "category": "transport",
  "method": "cash",
  "amount": 350.50,
  "spentAt": "2026-11-11T07:00:00Z",
  "reference": "Transport receipt 15",
  "note": "দোকানের পরিবহন খরচ"
}
```

Title 3–120 characters, amount >0 and <=1 billion with max 2 decimals, reference max 80,
note max 500. spentAt is a timezone-bearing timestamp from 2000 through now plus one day.
Method: cash/bank/mobile-banking. The method records an outbound expense classification;
no receiving account, supplier payable or bank-balance transaction is inferred.

Create requires Idempotency-Key with the same 8–80 character rules as Phases 5–6.
Retain the same key for retries; changed body with the same key gives 409. A replay returns
the expense's current representation, possibly edited or soft-deleted; it never recreates it.
New requests cannot supply expectedRevision. Responses include revision, createdBy,
updatedBy, timestamps and deleted. PUT accepts optional expectedRevision from the last read;
DELETE accepts it as query parameter. Mismatch gives 409. Existing clients may omit it:
row locks serialize changes but the last submitted edit then wins. Send revision to detect
stale editing. Deleted expenses cannot be edited or read through normal GET.

Delete is a soft delete, returning the deleted expense (200). Normal lists and reports
exclude it, while each create/update/delete stores a complete immutable revision snapshot.
History and audit commit with the expense; failure rolls all back. Database checks reject
expense changes without their matching revision snapshot. Revision history is admin-only.
List q is literal case-insensitive title/reference/note search; %/_ are not wildcards.
No dates on /expenses means all active expense records, subject to pagination.

## Notebook

Shared shop notebook, preserving the current client contract:

- GET `/notes?_page=1&_limit=20&_sort=serial&_order=desc&q=...` returns an array and
  `X-Total-Count`; CORS exposes this header for the existing Angular service.
- GET `/notes/{id}` returns one nondeleted note.
- POST `/transactions/notes` returns 201.
- PUT `/transactions/notes/{id}` returns the updated note.
- DELETE `/transactions/notes/{id}?expectedRevision=1` returns `{deleted:true,id}`.

All three roles may read; admin/operator may create/edit/delete shared notes, including
notes originally written by another operator. This is not a private per-user notebook.

```json
{"title":"আগামী সপ্তাহের কাজ","content":"সাপ্লায়ারের সঙ্গে যোগাযোগ করতে হবে।"}
```

Title 2–160 characters, content 1–10000 after trimming. Unicode is preserved. IDs, serial,
author ID/name and creation time are generated server-side; unknown/client-author fields
are rejected. Monotonically increasing sequence numbers are unique under concurrency and
never reused; failed/deleted notes can leave gaps. Edits preserve serial/original author.
PUT may include expectedRevision; DELETE accepts the same optional query parameter.
Without it, edits use serialized last-write-wins behavior. Delete is soft and ordinary GET
returns 404 afterward. A repeated delete succeeds without another audit entry.

_page is 1–1,000,000, _limit 1–200, _sort must be serial; _order asc/desc. q max 120 searches
literal title/content, including Bangla. Pagination count and rows share a repeatable-read
snapshot. Note changes and audit records share a transaction.

## Activity log

Existing GET `/activityLogs` remains admin-only and returns its existing array shape.
Filters: actorId, role, action, entity, entityId, from, to, q, text, limit, offset.
Exact filters are case-sensitive (actions such as CREATE/UPDATE/DELETE); q is a literal
case-insensitive substring of actor name, details or entity ID. text uses PostgreSQL simple
full-text token search across actor name/details/entity/action/entity ID. Multiple tokens
are ANDed; this is not fuzzy substring search. Both q and text may be combined.

Date filtering uses the same inclusive Bangladesh dates and paired-range validation.
Exact-filter B-tree indexes and a GIN text-search index are added without extensions.
The indexed text mode uses text=; broad q substring searches do not use that GIN index.
No audit-update/delete endpoint is added. Query inputs are bound parameters; field names
and sorting are fixed, not interpolated user input.

## Customer/supplier details and Message Center

Customer details retains transactionSummary and now adds recentInvoices/recentPayments
(up to five each). Supplier details now has transactionSummaryAvailable=true, with
purchaseCount, purchaseAmount, lastPurchaseAt and recentPurchases. Its
paymentSummaryAvailable=false is intentional: supplier payment/due accounting has not
been introduced, so no fake payable amount is returned.

GET /messageCenter reports scope=recipient-selection-only, sendingEnabled=false, and
all/selected/individual modes. GET /messageCenter/recipients?q=&limit=50&offset=0 returns
active non-archived customer IDs/names/Bangla names/phones. Existing /customers can still
support the old selection UI. There is no send API, SMS queue, gateway, broadcast or
background delivery. No outgoing messages are sent by this phase.

## Operational notes

New report/dashboard multi-query responses use read-only repeatable-read transactions for
consistent totals and lists. Date/amount aggregation is in JDBC/PostgreSQL, not client code.
No dummy data is seeded. Existing invoices, stock, purchases, customer rates and payments
are preserved. Frontend services are not changed here: report/dashboard endpoints are ready
for the later frontend integration rather than automatically switching its dummy sources.
