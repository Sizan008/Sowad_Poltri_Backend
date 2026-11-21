import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { CascadeFilters, CascadeKind, CascadeSelectionResponse, CascadeStage } from '../models/catalog-cascade.model';

@Injectable({ providedIn: 'root' })
export class CatalogCascadeService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}${API_ENDPOINTS.CATALOG_CASCADE_OPTIONS}`;

  getStage(kind: CascadeKind, stage: CascadeStage, filters: CascadeFilters = {}): Observable<CascadeSelectionResponse> {
    let params = new HttpParams().set('category', kind).set('stage', stage);
    for (const [key, value] of Object.entries(filters)) {
      if (value) params = params.set(key, value);
    }
    return this.http.get<CascadeSelectionResponse>(this.url, { params });
  }
}
