import { BagEntry } from './package-stock.model';
import { MedicinePackEntry } from './medicine-pack.model';
import { PurchasePricingDraft } from './purchase-pricing.model';

export interface MedicineMeasure {
  unitCode: 'g' | 'kg' | 'ml' | 'l';
  amount: number;
}

export interface TransactionLineInput {
  productId: string;
  quantity: number;
  unitPrice: number;
  pricingBasis?: 'unit' | 'package';
  purchasePricing?: PurchasePricingDraft;
  packages?: BagEntry[];
  medicinePacks?: MedicinePackEntry[];
  measurement?: MedicineMeasure | null;
}

export interface TransactionLineDraft extends TransactionLineInput {
  lineTotalOverride?: number;
  key: number;
}

export type TransactionKind = 'purchase' | 'sale';
