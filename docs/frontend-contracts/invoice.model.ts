import { PaymentDestination } from './receiving-account.model';
import { TransactionLineInput } from './transaction.model';

export interface Invoice {
  id: string;
  invoiceNumber: string;
  customerId: string;
  issuedAt: string;
  dueDate: string;
  totalAmount: number;
  subtotal?: number;
  discountAmount?: number;
  notes?: string;
  lastNote?: string;
}

export interface InvoiceItem {
  id: string;
  invoiceId: string;
  productId: string;
  /** Optional transaction-time category snapshot, for future backends. */
  categoryId?: string;
  productName: string;
  unit: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
  packages?: import('./package-stock.model').BagEntry[];
  measurement?: import('./transaction.model').MedicineMeasure | null;
}

export interface CreateInvoiceRequest extends PaymentDestination {
  customerId: string;
  issuedAt: string;
  dueDate: string;
  discountAmount: number;
  initialPaid: number;
  paymentMethod: PaymentMethod;
  notes: string;
  lastNote: string;
  items: TransactionLineInput[];
}

export type PaymentMethod = 'cash' | 'bank' | 'mobile-banking';
