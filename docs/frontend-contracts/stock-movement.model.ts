import { BagEntry } from './package-stock.model';
import { MedicinePackEntry } from './medicine-pack.model';

export interface StockMovement {
  id: string;
  productId: string;
  type: 'purchase' | 'sale' | 'adjustment';
  quantityChange: number;
  stockBefore: number;
  stockAfter: number;
  relatedId: string | null;
  occurredAt: string;
  note: string;
  packages?: BagEntry[];
  medicinePacks?: MedicinePackEntry[];
}

export interface CreateStockAdjustmentRequest {
  productId: string;
  direction: 'in' | 'out';
  quantity: number;
  packages?: BagEntry[];
  medicinePacks?: MedicinePackEntry[];
  occurredAt: string;
  reason: string;
}
