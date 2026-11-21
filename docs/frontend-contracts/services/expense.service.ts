import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { Expense, SaveExpenseRequest } from '../models/expense.model';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class ExpenseService {
  private readonly http = inject(HttpClient);
  private readonly listUrl = `${environment.apiBaseUrl}${API_ENDPOINTS.EXPENSES}`;
  private readonly transactionUrl = `${environment.apiBaseUrl}${API_ENDPOINTS.TX_EXPENSE}`;

  getExpenses(): Observable<Expense[]> {
    return this.http.get<Expense[]>(this.listUrl);
  }

  getExpense(id: string): Observable<Expense> {
    return this.http.get<Expense>(`${this.listUrl}/${encodeURIComponent(id)}`);
  }

  createExpense(data: SaveExpenseRequest): Observable<Expense> {
    return this.http.post<Expense>(this.transactionUrl, data);
  }

  updateExpense(id: string, data: SaveExpenseRequest): Observable<Expense> {
    return this.http.put<Expense>(`${this.transactionUrl}/${encodeURIComponent(id)}`, data);
  }

  deleteExpense(id: string): Observable<Expense> {
    return this.http.delete<Expense>(`${this.transactionUrl}/${encodeURIComponent(id)}`);
  }
}
