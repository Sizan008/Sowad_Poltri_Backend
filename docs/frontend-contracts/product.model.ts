import { BagEntry } from './package-stock.model';
import { MedicinePackEntry } from './medicine-pack.model';

export type ProductUnit = string;
export type ProductKind = 'feed' | 'medicine' | 'chick' | 'fish' | 'legacy';
export type MedicineForm = 'powder' | 'liquid';

/** Prices in BDT for ONE sealed packet/bottle of the specified size. */
export interface MedicinePackPrice {
  amount: number;
  unitCode: 'g' | 'kg' | 'ml' | 'l';
  sizeBase: number;
  purchasePrice: number;
  salePrice: number;
}


export interface Product {
  id: string;
  archived?: boolean;
  isSample?: boolean;
  registrationSource?: 'manual-v14';
  packagingStatus?: 'demo-allocation-unverified' | 'legacy-unit-needs-reconciliation' | 'verified-by-recount';
  name: string;
  categoryId: string;
  kind?: ProductKind;
  subtype?: string;
  medicineForm?: MedicineForm | null;
  manufacturer?: string;
  grade?: 'A' | 'B+' | null;
  medicinePackPrices?: MedicinePackPrice[];
  sku: string;
  feedCatalogId?: string | null;
  productCode?: string;
  feedGroup?: string;
  feedType?: string;
  defaultBagSizeKg?: number;
  referenceAgentPriceKg?: number;
  referenceMrpKg?: number;
  referenceMrpBag?: number;
  referencePriceDate?: string;
  referencePricesUnverified?: boolean;
  unit: ProductUnit;
  costPrice: number;
  salePrice: number;
  stockQuantity: number;
  lowStockThreshold: number;
}

export interface SaveProductRequest {
  name: string;
  sku: string;
  feedCatalogId?: string | null;
  productCode?: string;
  feedGroup?: string;
  feedType?: string;
  defaultBagSizeKg?: number;
  referenceAgentPriceKg?: number;
  referenceMrpKg?: number;
  referenceMrpBag?: number;
  referencePriceDate?: string;
  referencePricesUnverified?: boolean;
  categoryId: string;
  subtype: string;
  medicineForm: MedicineForm | null;
  manufacturer?: string;
  grade?: 'A' | 'B+' | null;
  medicinePackPrices?: Omit<MedicinePackPrice, 'sizeBase'>[];
  costPrice?: number;
  salePrice?: number;
  lowStockThreshold?: number;
  openingQuantity?: number;
  openingPackages?: BagEntry[];
  openingMedicinePacks?: MedicinePackEntry[];
  recountMedicinePacks?: MedicinePackEntry[];
  recountPackages?: BagEntry[];
  recountReason?: string;
}
