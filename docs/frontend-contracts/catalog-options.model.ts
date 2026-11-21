export interface CatalogOptions {
  id: 'product-types';
  feedBagSizesKg: number[];
  feedTypes: string[];
  chickTypes: string[];
  chickGrades: string[];
  fishTypes: string[];
  medicineTypes: string[];
  medicineForms: ('powder' | 'liquid')[];
}
