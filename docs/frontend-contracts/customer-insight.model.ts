import { Customer } from './customer.model';
import { Category } from './category.model';
import { Invoice, InvoiceItem } from './invoice.model';
import { Payment } from './payment.model';
import { Product } from './product.model';

/** A customer's billing and buying labels are derived from transactions, never stored twice. */
export type BillCategory = 'due' | 'paid' | 'no-invoices';

export interface CustomerInsight {
  customer: Customer;
  billCategory: BillCategory;
  dueAmount: number;
  invoiceCount: number;
  buyingCategoryIds: string[];
  buyingCategoryNames: string[];
}

export interface CustomerInsightSource {
  customers: Customer[];
  invoices: Invoice[];
  items: InvoiceItem[];
  payments: Payment[];
  products: Product[];
  categories: Category[];
}
