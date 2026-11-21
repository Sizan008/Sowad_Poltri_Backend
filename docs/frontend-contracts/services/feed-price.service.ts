import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FeedPriceReference } from '../models/feed-price.model';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { environment } from '../../environments/environment';

@Injectable({ providedIn: 'root' })
export class FeedPriceService {
  private readonly http = inject(HttpClient);
  getCatalog(): Observable<FeedPriceReference[]> {
    return this.http.get<FeedPriceReference[]>(
      environment.apiBaseUrl + API_ENDPOINTS.FEED_PRICE_CATALOG,
    );
  }
}
