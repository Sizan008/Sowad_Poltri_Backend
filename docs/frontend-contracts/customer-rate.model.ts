/** Product-specific sale price in the product's base unit (kg, g, ml, piece).
 * pricePerKg is kept only to read legacy feed rates from older db.json records.
 */
export interface CustomerRate {
  productId: string;
  pricePerUnit?: number;
  pricePerKg?: number;
  unit?: string;
}
