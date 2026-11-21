# Phase 3 API

All routes require Phase 2 Bearer authentication. Success bodies remain direct
objects/arrays, errors use the existing code/message/fieldErrors/requestId format.
Read: admin/operator/viewer. Write: admin/operator. Current product lists use the
existing full-array contract; pagination is not silently added.

| Method | Route | Purpose |
|---|---|---|
| GET | /categories | Four fixed categories |
| GET | /units, /units/{id} | Unit list/detail |
| POST / PUT / DELETE | /units, /units/{id} | Unit create/update/delete |
| GET | /products, /products/{id} | Product list/detail; list supports categoryId, unit, kind, id, archived, q |
| POST | /catalog/products | Identity-only registration |
| PUT / DELETE | /catalog/products/{id} | Edit / soft archive |
| GET | /feedPriceCatalog | 76 historical references |
| GET | /catalogOptions/product-types | Registration options |
| GET | /catalog/cascade-options | Existing category/stage/filter contract |
| GET / POST | /catalog/products/{id}/packaging | List / add definition |
| DELETE | /catalog/products/{id}/packaging/{packageId} | Deactivate definition |

Product body: name, sku, categoryId, subtype, grade, manufacturer, medicineForm,
feedGroup, feedType, optional lowStockThreshold. Only relevant category details are
stored. Unknown fields fail; price, inventory, id, archived and catalog association
cannot be injected. Medicine requires company and powder/liquid form; chick requires
broiler/sonali/colorbird/layer and A/B+; fish requires tilapia/sarputi.

Packaging body example: `{"amount":1,"unitCode":"l"}` (liquid medicine) resolves
sizeBase=1000, packageType=bottle. Feed: `{"amount":25,"unitCode":"kg"}`.
There is no pack count, stock or price field. The unique key is product + base size.

Cascade categories: feed, medicine, chick, fish.
- feed stages: groups → feedTypes → products; filters group, feedType
- medicine: medicineNames → manufacturers → medicineForms → products;
  filters medicineName, manufacturer, medicineForm
- chick: breeds → grades → products; filters breed, grade
- fish: fishTypes → products; filter fishType

Only non-archived registered products populate cascades. No fake products are
created to fill an empty dropdown. Fixed registration choices remain in catalogOptions.
