export const FEED_BAG_SIZES_KG = [1, 5, 10, 20, 25, 40, 50] as const;

export interface BagEntry {
  bagSizeKg: number;
  bagCount: number;
}

export interface PackageStock extends BagEntry {
  id: string;
  productId: string;
}

export function totalBagKg(rows: readonly BagEntry[]): number {
  return rows.reduce((sum, item) => sum + item.bagSizeKg * item.bagCount, 0);
}
