import { TransactionLineInput } from './transaction.model';

export interface Purchase {
  id: string;
  supplierId: string;
  purchasedAt: string;
  totalAmount: number;
  purchaseNumber?: string;
  reference?: string;
  notes?: string;
}

export interface PurchaseItem {
  id: string;
  purchaseId: string;
  productId: string;
  productName: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  packages?: import('./package-stock.model').BagEntry[];
  measurement?: import('./transaction.model').MedicineMeasure | null;
  purchasePricingSnapshot?: {
    unitPurchaseRate: number | null;
    unitSellingRate: number | null;
    packages: {
      key: string; label: string; count: number;
      purchasePerPackage: number;
      purchaseRatePerUnit?: number | null;
      purchaseRateUnit?: string | null;
      suggestedSaleRatePerUnit?: number | null;
      suggestedSaleRateUnit?: string | null;
      suggestedSalePerPackage: number | null;
      purchaseLineTotal: number;
    }[];
  } | null;
}

export interface CreatePurchaseRequest {
  supplierId: string;
  purchasedAt: string;
  reference: string;
  notes: string;
  items: TransactionLineInput[];
}
