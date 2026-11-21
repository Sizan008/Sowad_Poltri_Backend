import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { CreateStockAdjustmentRequest, StockMovement } from '../models/stock-movement.model';

@Injectable({ providedIn: 'root' })
export class InventoryService {
  private readonly http = inject(HttpClient);

  getMovements(): Observable<StockMovement[]> {
    return this.http.get<StockMovement[]>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.STOCK_MOVEMENTS}`,
    );
  }

  adjustStock(data: CreateStockAdjustmentRequest): Observable<StockMovement> {
    return this.http.post<StockMovement>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.TX_STOCK_ADJUSTMENT}`,
      data,
    );
  }
}
