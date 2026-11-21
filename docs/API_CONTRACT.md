# Frontend contract baseline — Phase 1

Source: Sowad_poltri_without_node_modules(20261106-161530).zip and
Sowad_poltri_dummy_json(10).zip. The frontend-contracts directory contains the exact
TypeScript model, endpoint and HTTP-service source snapshots used for mapping.
These are documentation, not Java runtime source. Phase 1 implements ONLY GET /health.
All business paths below are reserved for their assigned phases, not working stubs.

## Compatibility decisions
- Preserve lower camelCase JSON properties and direct array/object success responses.
  Do not wrap them in CloudNet Status/Message/Result. Preserve CloudNet Java naming.
- Existing API IDs are opaque strings; use Java String and PostgreSQL VARCHAR(64)
  for business primary/foreign keys. New IDs will be UUID strings. Do not cast legacy
  identifiers to PostgreSQL UUID; retain existing IDs on validated import.
- Fixed keys such as catalogOptions/product-types and businessSettings/business stay unchanged.
- Dates map to ISO-8601 strings; event timestamps use Instant/TIMESTAMPTZ.
  Business calendar dates use LocalDate/DATE where applicable; report days use Asia/Dhaka.
- Money uses BigDecimal/NUMERIC(19,2); unit rates may require NUMERIC(19,6);
  base quantities NUMERIC(18,3), physical bag/pack/piece counts integral.
  Confirm precision per feature before creating its tables.
- Feed totals are kg with physical bag size/count preserved. Medicine base measurements
  and physical pack counts are separate. No kg-to-bag reinterpretation during import.
- JSON numbers remain numbers for frontend compatibility. Precision-sensitive totals
  are computed and validated in the backend.
- Errors use {code,message,fieldErrors,requestId,timestamp}; frontend can read message.
- List pagination requires deliberate frontend integration; do not silently replace
  current arrays with Page objects. New report APIs will be documented in Phase 7.
- SQL table/column names are snake_case. Java classes PascalCase, fields camelCase.

## Endpoint map

| Phase | Method and route | Request / response contract |
|---|---|---|
| 1 | GET /health | HealthDetails: status, phase, mode, database, schema, schemaVersion |
| 2 | Auth endpoints (new contract) | Replace local demo login; passwords hashed by backend |
| 2 | GET /businessSettings/business; PUT /transactions/settings | BusinessSettings |
| 3 | GET /categories; GET /units; POST/PUT/DELETE /units[/id] | Category[] / Unit / Unit[] |
| 3 | GET /products[/id]; POST /catalog/products; PUT/DELETE /catalog/products/id | Product and product registration/edit models |
| 3 | GET /catalogOptions/product-types; GET /catalog/cascade-options | CatalogOptions / CascadeSelectionResponse; existing query parameters |
| 3 | GET /feedPriceCatalog | FeedPriceCatalogEntry[] (exact model snapshot authoritative) |
| 4 | GET /customers[/id]; POST /catalog/customers; PUT /catalog/customers/id; DELETE /customers/id | Customer draft / Customer / Customer[] |
| 4 | PUT /customers/id/rates | Existing customer rate payload / Customer |
| 4 | GET/POST/PUT/DELETE /suppliers[/id] | Supplier / Supplier[] |
| 4 | GET /receivingAccounts; POST /transactions/receiving-accounts; PUT/DELETE /transactions/receiving-accounts/id | ReceivingAccount models |
| 5 | GET /purchases[/id]; GET /purchaseItems; POST /transactions/purchases | CreatePurchaseRequest / Purchase; item arrays |
| 5 | GET /packageStocks; GET /medicinePacks; GET /stockMovements | Physical package and movement model arrays |
| 5 | POST /transactions/stock-adjustments; /transactions/package-counts; /transactions/medicine-counts | Exact adjustment/recount payloads in service snapshots |
| 6 | GET /invoices[/id]; GET /invoiceItems; POST /transactions/invoices | CreateInvoiceRequest / Invoice; item arrays |
| 6 | GET /payments; POST /transactions/payments | CreatePaymentRequest / Payment / Payment[] |
| 7 | GET /expenses; POST /transactions/expenses; PUT/DELETE /transactions/expenses/id | Expense models |
| 7 | GET /notes; POST /transactions/notes; PUT/DELETE /transactions/notes/id | Notebook models |
| 2/7 | POST /activity-events/session; GET /activityLogs | Authenticated event recording / ActivityLog[] |
| 7 | Dashboard, reports, dues, ledger query endpoints | New contracts replacing client aggregation; integrate explicitly |

## Existing compatibility gaps to resolve per phase
The frontend still contains legacy ProductService POST /products and PATCH /products/id
methods, while the dummy server blocks direct product writes. These are not a reason to
allow validation bypass: update any remaining callers to /catalog/products during Phase 3.
Client actor headers are demo metadata, not authenticated identity. Phase 2 will obtain
actors from the authenticated principal and update the frontend interceptor.
Customer/supplier deletes will follow archive/reference rules; history must survive.
Categories are fixed in the dummy server; no generic category-write endpoint is planned.
No SMS provider is connected; the message screen stays within its current UI scope.

## Phase 1 health behavior
HTTP 200 only for database nourish_poltri_shop, current schema nourish, schema version 1.
An absent/wrong version returns 503/down. Runtime JDBC failures return 503 with a safe
message. Invalid credentials or missing table can stop startup because JPA validates
schema at startup; run manual SQL first. No fake success response or auto-DDL.
