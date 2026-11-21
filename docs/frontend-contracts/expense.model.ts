export type ExpenseCategory =
  | 'rent' | 'utilities' | 'salary' | 'transport'
  | 'maintenance' | 'supplies' | 'other';

export type ExpenseMethod = 'cash' | 'bank' | 'mobile-banking';

export interface Expense {
  id: string;
  title: string;
  category: ExpenseCategory;
  method: ExpenseMethod;
  amount: number;
  spentAt: string;
  reference: string;
  note: string;
}

export type SaveExpenseRequest = Omit<Expense, 'id'>;
