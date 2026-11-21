import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { Customer } from '../models/customer.model';
import { CustomerRate } from '../models/customer-rate.model';

@Injectable({ providedIn: 'root' })
export class CustomerService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}${API_ENDPOINTS.CUSTOMERS}`;

  getCustomers(): Observable<Customer[]> {
    return this.http.get<Customer[]>(this.url);
  }

  getCustomer(id: string): Observable<Customer> {
    return this.http.get<Customer>(`${this.url}/${encodeURIComponent(id)}`);
  }

  createCustomer(data: Customer): Observable<Customer> {
    return this.http.post<Customer>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.CATALOG_CUSTOMERS}`, data,
    );
  }

  updateCustomer(id: string, data: Customer): Observable<Customer> {
    return this.http.put<Customer>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.CATALOG_CUSTOMERS}/${encodeURIComponent(id)}`, data,
    );
  }

  saveCustomerRates(id: string, rates: CustomerRate[]): Observable<Customer> {
    return this.http.put<Customer>(
      `${this.url}/${encodeURIComponent(id)}/rates`, { rates },
    );
  }

  deleteCustomer(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(id)}`);
  }
}
