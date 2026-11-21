# Phase 5 — Purchase and inventory

Backend baseline: supplied Sowad_Poltri_Backend(3).zip. No frontend changes.
Requires Phase 4 database and existing bearer-token authentication. No demo stock imported.

## Endpoints and roles

| Method / route | Purpose | Roles |
|---|---|---|
| POST /transactions/purchases | Post immutable purchase, prices, stock and audit atomically | admin, operator |
| GET /purchases?supplierId=&limit=50&offset=0 | Purchase list, newest purchase date first | all three roles |
| GET /purchases/{id} | Header and item pricing snapshots | all three roles |
| GET /purchaseItems?purchaseId= | Items for one purchase | all three roles |
| GET /inventory?productId=&kind=&lowStock=false&limit=50&offset=0 | Current balances, revision and thresholds | all three roles |
| GET /inventory/{productId} | Balance, revision and registered package counts | all three roles |
| GET /packageStocks?productId=&limit=50&offset=0 | Feed bag stock by size | all three roles |
| GET /medicinePacks?productId=&limit=50&offset=0 | Medicine bottles/packets by size | all three roles |
| GET /stockMovements?productId=&type=&limit=50&offset=0 | Movement history | all three roles |
| GET /stockMovements/{id} | Movement including package before/after counts | all three roles |
| POST /transactions/stock-adjustments | Signed adjustment or physical recount | admin |
| GET /stockAdjustments/{id} | Saved adjustment and movement | all three roles |

All list responses are arrays. Limit 1–200, offset >=0. History is ordered by recorded
time; stock revision is the authoritative sequence for each product. Backdated purchases
do not rewrite historical balances. Movement types: purchase, adjustment, recount.
Low stock means current quantity <= the product's lowStockThreshold; archived products
are excluded from low-stock results. Missing inventory rows mean zero opening stock,
revision zero. Normal inventory/history reads can include archived products.

## Transaction retries

Both POST routes require `Idempotency-Key: <8–80 letters/digits/hyphens/underscores>`.
Generate a UUID once for a logical submission; retain it for retries. The same key and
same decoded command return the original result without another stock change. Reuse with
changed content or another operation returns 409. Failed operations do not reserve keys.
Keys are shared across users and both routes. Authorization is still checked on every retry.
Successful POST/replay returns 201. There is no edit/delete endpoint for posted purchases.

## Purchase example: mixed feed sizes

Replace IDs with existing active supplier/product IDs; register both packaging sizes first.
Dates accept an ISO timestamp with timezone, from 2000 through the current time plus one day.

```json
{
  "supplierId": "SUPPLIER_ID",
  "purchasedAt": "2026-11-11T06:00:00Z",
  "reference": "Supplier invoice 101",
  "notes": "Received and counted",
  "items": [{
    "productId": "PRODUCT_ID",
    "quantity": 100,
    "packages": [
      {"bagSizeKg": 25, "bagCount": 2},
      {"bagSizeKg": 50, "bagCount": 1}
    ],
    "purchasePricing": {
      "unitPurchaseRate": 60,
      "unitSellingRate": 75,
      "setGlobalSellingRate": false,
      "packagePrices": [{
        "key": "bag:25",
        "purchasePricePerPackage": 1400,
        "purchaseRatePerUnit": 70,
        "purchaseRateUnit": "kg",
        "suggestedSalePricePerPackage": 1700
      }]
    }
  }]
}
```

Result: 100 kg stock, two 25 kg bags, one 50 kg bag; purchase total 5,800.
The 25 kg bag direct price wins over its entered unit rate. The 50 kg bag uses shared
60/kg, making 3,000/bag. Both entered values and the selected pricing source are retained.

### Pricing and quantity rules

- 1–100 distinct products per purchase; up to 50 distinct sizes per product. Supplier
  must be active and not archived; products must not be archived.
- Feed requires packages with positive integer bagCount and registered active bag sizes.
- Medicine requires medicinePacks with amount, unitCode and positive integer packCount.
  Match is by canonical base size against registered active product packaging.
- Chicks/fish require positive integer quantity; no bags, medicine packs or measurements.
- For packaged products quantity may be omitted; if supplied, it must exactly match the
  sum of base size × count. Optional medicine measurement must match this sum too.
- Price precedence per selected size: direct package price, then that size's unit rate,
  then shared unitPurchaseRate. Suggested selling prices follow the same precedence.
- Unit rates allow 4 decimal places; monetary package prices and totals are rounded
  HALF_UP to 2 decimals before multiplying package count. A resolved package price must
  be at least 0.01. Effective purchase unit cost is total / quantity, rounded to 6 decimals.
- Unit and direct price inputs must be positive and <=1 billion. Counts <=1 billion;
  resulting total base stock <=1 billion, with at most 3 decimals (piece stock is integer).
- Per-size medicine rate units support g/kg or ml/l. Feed uses kg; pieces use piece.
  Shared rates are always per product base unit (kg, g, ml or piece).
- Package price keys are `bag:25`, `medicine:250`, or `piece`. Keys use normalized base
  size without trailing decimal zeros. Unknown or duplicate keys are rejected.
- Suggested selling prices are snapshots only. `setGlobalSellingRate=true` requires
  shared unitSellingRate and updates only this product's global base-unit selling rate.
  False/omitted preserves the previous global rate. Customer-specific rates remain intact.
- Latest purchase updates costPrice to effective unit cost. This is last purchase cost,
  not FIFO or weighted-average valuation. Official catalog reference prices stay unchanged.
- Legacy unitPrice is accepted only without purchasePricing and with unit pricing.
  When purchasePricing exists, unitPrice is a compatibility field and is not used.
  Explicit package pricing requires purchasePricing; no existing selling price is used
  to guess purchase cost.
- Unknown fields, numeric strings, fractional counts and duplicate JSON keys are rejected.

Medicine example item (250 ml bottle × 2, 800 per litre = 200 per bottle):

```json
{
  "productId": "MEDICINE_ID",
  "quantity": 500,
  "medicinePacks": [{"amount": 0.25, "unitCode": "l", "packCount": 2}],
  "purchasePricing": {
    "setGlobalSellingRate": false,
    "packagePrices": [{
      "key": "medicine:250",
      "purchaseRatePerUnit": 800,
      "purchaseRateUnit": "l",
      "suggestedSaleRatePerUnit": 1000,
      "suggestedSaleRateUnit": "l"
    }]
  }
}
```

Purchase details include supplier name, product name/SKU/base unit and an immutable
purchasePricingSnapshot with the definition's amount/unit/type/size, counts, entered
rates, selected rates, suggestions and line totals. Future catalog or price changes do
not alter these snapshots. Package-level detail is stored relationally as well.

## Adjustments and recount

Signed package adjustment (one damaged bag):

```json
{
  "productId": "PRODUCT_ID",
  "mode": "delta",
  "packages": [{"packagingId": "PACKAGING_ID", "count": -1}],
  "reason": "One bag damaged"
}
```

For a piece product, use `quantity: -1` instead of packages. Packaged products do not
accept direct quantity adjustment: exact bag/pack counts are necessary to preserve totals.
A positive delta can establish physically counted opening stock, but is not a purchase
and does not set cost or selling rates. No loose/open-package workflow is introduced.

First GET `/inventory/{productId}`, then physical recount:

```json
{
  "productId": "PRODUCT_ID",
  "mode": "recount",
  "expectedRevision": 3,
  "packages": [{"packagingId": "PACKAGING_ID", "count": 7}],
  "reason": "Physical count at shop closing"
}
```

Recount counts are absolute and nonnegative. Include every size with current stock;
use zero for a missing size. Include any extra found size too. For pieces use absolute
quantity. expectedRevision is mandatory for recount and optional for delta. If stock
changed since reading, return 409 and refresh/recount; never blindly retry with a new
revision. A recount that confirms the same balance still records history and advances
revision. Delta must change a count or quantity. Reason length 3–500 is required.

Negative resulting stock is rejected. Archived products cannot receive adjustments;
inactive packaging cannot gain stock. Products with stock cannot be archived and
packaging with stock cannot be deactivated. Zero-stock archival retains purchase,
rate, movement and adjustment history through foreign keys.

## Integrity and transaction boundaries

Supplier and sorted product row locks serialize posting against master-data changes and
concurrent stock operations. Purchases with opposite product order acquire the same lock
order. Package count, total balance, stock revision, movement details, purchase snapshots,
last cost/global selling rate, request key and audit all commit in one transaction.
Any failure rolls all of them back. Concurrent deductions cannot produce negative stock.

Database constraints enforce package ownership and size, nonnegative/integer counts,
piece quantities, package-derived balance, movement revision/balance chains and purchase
header/item/package totals. Posted purchase/movement/adjustment rows cannot be updated or
deleted. Corrections use new stock adjustments with reasons. Adjustments do not change
supplier financial liabilities or reverse purchase financial values; that workflow and
invoices/payments/dues belong to later phases.

The schema uses one inventory_package_balance table for feed bags and medicine packs,
linked to product_packaging; API projections separate the two. Balances start empty/zero,
with no fake purchases or dummy JSON imports. Product responses now use actual stock,
last cost and global selling price instead of Phase 3 zero placeholders.
