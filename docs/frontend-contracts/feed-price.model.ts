/** Historical Nourish price-list reference dated 23 September 2026. */
export interface FeedPriceReference {
  id: string;
  productCode: string;
  group: string;
  name: string;
  feedType: string;
  bagSizeKg: number;
  agentPricePerKg: number;
  mrpPerKg: number;
  mrpPerBag: number;
  effectiveDate: string;
  source: string;
}
