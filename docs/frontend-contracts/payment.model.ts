import { PaymentDestination } from './receiving-account.model';
import { PaymentMethod } from './invoice.model';

export interface Payment extends PaymentDestination {
  id: string;
  invoiceId: string;
  customerId: string;
  amount: number;
  paidAt: string;
  method: PaymentMethod;
  note?: string;
}

export interface CreatePaymentRequest extends PaymentDestination {
  invoiceId: string;
  amount: number;
  paidAt: string;
  method: PaymentMethod;
  note: string;
}
