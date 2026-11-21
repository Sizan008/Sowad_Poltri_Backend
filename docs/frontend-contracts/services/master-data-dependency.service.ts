import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { forkJoin, map, Observable, of } from 'rxjs';

import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { Invoice } from '../models/invoice.model';
import { Payment } from '../models/payment.model';
import { Product } from '../models/product.model';
import { Purchase } from '../models/purchase.model';

/**
 * UI-level referential checks for the demo API.
 * A real backend must enforce these rules atomically.
 */
@Injectable({ providedIn: 'root' })
export class MasterDataDependencyService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = environment.apiBaseUrl;

  private byField<T>(path: string, field: string, id: string): Observable<T[]> {
    const params = new HttpParams().set(field, id);
    return this.http.get<T[]>(`${this.baseUrl}${path}`, { params });
  }

  canDeleteCustomer(id: string): Observable<boolean> {
    return forkJoin([
      this.byField<Invoice>(API_ENDPOINTS.INVOICES, 'customerId', id),
      this.byField<Payment>(API_ENDPOINTS.PAYMENTS, 'customerId', id),
    ]).pipe(map(([invoices, payments]) => !invoices.length && !payments.length));
  }

  canDeleteSupplier(id: string): Observable<boolean> {
    return this.byField<Purchase>(API_ENDPOINTS.PURCHASES, 'supplierId', id)
      .pipe(map(items => items.length === 0));
  }

  canDeleteCategory(id: string): Observable<boolean> {
    return this.byField<Product>(API_ENDPOINTS.PRODUCTS, 'categoryId', id)
      .pipe(map(items => items.length === 0));
  }

  canDeleteUnit(code: string): Observable<boolean> {
    return this.byField<Product>(API_ENDPOINTS.PRODUCTS, 'unit', code)
      .pipe(map(items => items.length === 0));
  }

  canDeleteProduct(id: string): Observable<boolean> {
    const references = [
      API_ENDPOINTS.INVOICE_ITEMS,
      API_ENDPOINTS.PURCHASE_ITEMS,
      API_ENDPOINTS.STOCK_MOVEMENTS,
      API_ENDPOINTS.PACKAGE_STOCKS,
    ];
    if (!references.length) return of(true);

    return forkJoin(
      references.map(path => this.byField<unknown>(path, 'productId', id)),
    ).pipe(map(groups => groups.every(items => items.length === 0)));
  }
}
