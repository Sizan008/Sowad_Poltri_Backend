import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { Product, SaveProductRequest } from '../models/product.model';
import { PackageStock } from '../models/package-stock.model';
import { MedicinePackStock } from '../models/medicine-pack.model';

@Injectable({ providedIn: 'root' })
export class ProductService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}${API_ENDPOINTS.PRODUCTS}`;

  getProducts(): Observable<Product[]> {
    return this.http.get<Product[]>(this.url);
  }

  getProduct(id: string): Observable<Product> {
    return this.http.get<Product>(`${this.url}/${encodeURIComponent(id)}`);
  }

  getPackageStocks(): Observable<PackageStock[]> {
    return this.http.get<PackageStock[]>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.PACKAGE_STOCKS}`,
    );
  }

  getMedicinePacks(): Observable<MedicinePackStock[]> {
    return this.http.get<MedicinePackStock[]>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.MEDICINE_PACKS}`,
    );
  }

  createCatalogProduct(data: SaveProductRequest): Observable<Product> {
    return this.http.post<Product>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.CATALOG_PRODUCTS}`, data,
    );
  }

  updateCatalogProduct(id: string, data: SaveProductRequest): Observable<Product> {
    return this.http.put<Product>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.CATALOG_PRODUCTS}/${encodeURIComponent(id)}`, data,
    );
  }

  createProduct(data: Product): Observable<Product> {
    return this.http.post<Product>(this.url, data);
  }

  updateProduct(id: string, data: Product): Observable<Product> {
    // Do not overwrite live inventory totals from a stale master-data form.
    // Stock changes only through Phase 3 transaction endpoints.
    const { stockQuantity: _openingStock, ...editableFields } = data;
    return this.http.patch<Product>(
      `${this.url}/${encodeURIComponent(id)}`,
      editableFields,
    );
  }

  deleteProduct(id: string): Observable<void> {
    return this.http.delete<void>(`${environment.apiBaseUrl}${API_ENDPOINTS.CATALOG_PRODUCTS}/${encodeURIComponent(id)}`);
  }
}
