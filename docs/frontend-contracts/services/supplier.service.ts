import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { Supplier } from '../models/supplier.model';

@Injectable({ providedIn: 'root' })
export class SupplierService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}${API_ENDPOINTS.SUPPLIERS}`;

  getSuppliers(): Observable<Supplier[]> {
    return this.http.get<Supplier[]>(this.url);
  }

  getSupplier(id: string): Observable<Supplier> {
    return this.http.get<Supplier>(`${this.url}/${encodeURIComponent(id)}`);
  }

  createSupplier(data: Supplier): Observable<Supplier> {
    return this.http.post<Supplier>(this.url, data);
  }

  updateSupplier(id: string, data: Supplier): Observable<Supplier> {
    return this.http.put<Supplier>(
      `${this.url}/${encodeURIComponent(id)}`,
      data,
    );
  }

  deleteSupplier(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(id)}`);
  }
}
