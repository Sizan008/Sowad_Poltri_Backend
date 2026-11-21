export type MedicineUnitCode = 'g' | 'kg' | 'ml' | 'l';
export interface MedicinePackEntry {
  amount: number;
  unitCode: MedicineUnitCode;
  packCount: number;
}
export interface MedicinePackStock extends MedicinePackEntry {
  id: string;
  productId: string;
  sizeBase: number;
}
export const toBaseAmount = (row: MedicinePackEntry): number =>
  row.amount * (row.unitCode === 'kg' || row.unitCode === 'l' ? 1000 : 1);
export const totalMedicineBase = (rows: readonly MedicinePackEntry[]): number =>
  Math.round(rows.reduce((sum, row) => sum + toBaseAmount(row) * row.packCount, 0) * 1000) / 1000;
