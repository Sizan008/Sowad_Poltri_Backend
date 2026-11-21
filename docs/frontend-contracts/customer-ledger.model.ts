import { Customer } from './customer.model';
import { Invoice } from './invoice.model';

export interface CustomerDueSummary {
  customer: Customer;
  invoiceCount: number;
  totalInvoiced: number;
  totalPaid: number;
  remaining: number;
  earliestDueDate: string;
}

export interface CustomerLedgerEntry {
  id: string;
  at: string;
  description: string;
  invoiceId: string;
  debit: number;
  credit: number;
  balance: number;
}

export interface CustomerLedger {
  customer: Customer;
  invoices: Invoice[];
  rows: CustomerLedgerEntry[];
  totalInvoiced: number;
  totalPaid: number;
  balance: number;
}
