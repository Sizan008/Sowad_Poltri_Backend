export type ReportType = 'sales' | 'purchases' | 'stock' | 'dues';

export interface SalesReportRow {
  id: string;
  invoiceNumber: string;
  customerName: string;
  date: string;
  invoiceAmount: number;
  paidAmount: number;
  dueAmount: number;
}

export interface PurchaseReportRow {
  id: string;
  purchaseNumber: string;
  supplierName: string;
  date: string;
  totalAmount: number;
}

export interface StockReportRow {
  id: string;
  productName: string;
  sku: string;
  unit: string;
  quantity: number;
  costPrice: number;
  stockValue: number;
  isLow: boolean;
}

export interface DueReportRow {
  id: string;
  customerName: string;
  phone: string;
  invoiceCount: number;
  invoiceAmount: number;
  paidAmount: number;
  dueAmount: number;
  earliestDueDate: string;
}

export interface ReportData {
  sales: SalesReportRow[];
  purchases: PurchaseReportRow[];
  stock: StockReportRow[];
  dues: DueReportRow[];
}
