import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { CreatePurchaseRequest, Purchase, PurchaseItem } from '../models/purchase.model';

@Injectable({ providedIn: 'root' })
export class PurchaseService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}${API_ENDPOINTS.PURCHASES}`;

  getPurchases(): Observable<Purchase[]> {
    return this.http.get<Purchase[]>(this.url);
  }

  getPurchase(id: string): Observable<Purchase> {
    return this.http.get<Purchase>(`${this.url}/${encodeURIComponent(id)}`);
  }

  getPurchaseItems(): Observable<PurchaseItem[]> {
    return this.http.get<PurchaseItem[]>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.PURCHASE_ITEMS}`,
    );
  }

  createPurchase(data: CreatePurchaseRequest): Observable<Purchase> {
    return this.http.post<Purchase>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.TX_PURCHASE}`,
      data,
    );
  }
}
