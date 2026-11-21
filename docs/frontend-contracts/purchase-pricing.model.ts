/** Purchase prices may be supplied per base unit OR directly per physical pack.
 * Every physical package row must resolve to a purchase cost before saving. */
export interface PurchasePackagePriceDraft {
  key: string;
  purchasePricePerPackage: number | null;
  suggestedSalePricePerPackage: number | null;
  /** Optional rate for this exact size. Supports g/kg and ml/L for medicines. */
  purchaseRatePerUnit?: number | null;
  purchaseRateUnit?: "kg" | "g" | "ml" | "l" | "piece";
  suggestedSaleRatePerUnit?: number | null;
  suggestedSaleRateUnit?: "kg" | "g" | "ml" | "l" | "piece";
}
export interface PurchasePricingDraft {
  unitPurchaseRate: number | null;
  unitSellingRate: number | null;
  packagePrices: PurchasePackagePriceDraft[];
  setGlobalSellingRate: boolean;
}
export const emptyPurchasePricing = (): PurchasePricingDraft => ({
  unitPurchaseRate: null,
  unitSellingRate: null,
  packagePrices: [],
  setGlobalSellingRate: false,
});
