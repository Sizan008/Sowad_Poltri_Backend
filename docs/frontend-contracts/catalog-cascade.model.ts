import { ProductKind } from './product.model';
import { UiSelectOption } from '../common-components/ui-select/ui-select.component';

/** Only meaningful, category-specific stages are requested from the dummy API. */
export type CascadeKind = Exclude<ProductKind, 'legacy'>;
export type CascadeStage =
  | 'groups' | 'feedTypes' | 'medicineNames' | 'manufacturers'
  | 'medicineForms' | 'breeds' | 'grades' | 'fishTypes' | 'products';

export interface CascadeSelectionResponse {
  category: CascadeKind;
  stage: CascadeStage;
  options: UiSelectOption[];
}

export interface CascadeFilters {
  group?: string;
  feedType?: string;
  medicineName?: string;
  manufacturer?: string;
  medicineForm?: string;
  breed?: string;
  grade?: string;
  fishType?: string;
}
