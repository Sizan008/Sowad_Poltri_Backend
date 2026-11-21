import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { CatalogOptions } from '../models/catalog-options.model';
import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';

@Injectable({ providedIn: 'root' })
export class CatalogOptionsService {
  private readonly http = inject(HttpClient);
  getOptions(): Observable<CatalogOptions> {
    return this.http.get<CatalogOptions>(
      environment.apiBaseUrl + API_ENDPOINTS.CATALOG_OPTIONS,
    );
  }
}
