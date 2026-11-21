import { ProductUnit } from './product.model';

export type InvoicePaymentStatus = 'paid' | 'partial' | 'due';

export interface ChartPoint {
  label: string;
  date: string;
  sales: number;
  purchases: number;
}

export interface RecentInvoice {
  id: string;
  invoiceNumber: string;
  customerName: string;
  issuedAt: string;
  totalAmount: number;
  dueAmount: number;
  status: InvoicePaymentStatus;
}

export interface PaymentReminder {
  customerId: string;
  customerName: string;
  dueDate: string;
  amount: number;
}

export interface StockAlert {
  id: string;
  name: string;
  quantity: number;
  unit: ProductUnit;
}

export interface DashboardViewModel {
  todaySales: number;
  yesterdaySales: number;
  totalDue: number;
  dueCustomerCount: number;
  stockValue: number;
  totalStockWeightKg: number;
  activeCustomers: number;
  newCustomersThisMonth: number;
  salesGrowthPercent: number;
  chartPoints: ChartPoint[];
  reminders: PaymentReminder[];
  recentInvoices: RecentInvoice[];
  stockAlerts: StockAlert[];
}
