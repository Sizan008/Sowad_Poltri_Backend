import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { ActivityLogEntry } from '../models/activity-log.model';

@Injectable({ providedIn: 'root' })
export class ActivityLogService {
  private readonly http = inject(HttpClient);
  list(page: number, size: number): Observable<{ items: ActivityLogEntry[]; total: number }> {
    const params = new HttpParams().set('_sort', 'occurredAt').set('_order', 'desc')
      .set('_page', page).set('_limit', size);
    return this.http.get<ActivityLogEntry[]>(
      environment.apiBaseUrl + API_ENDPOINTS.ACTIVITY_LOGS, { observe: 'response', params },
    ).pipe(map(response => ({
      items: response.body ?? [],
      total: Number(response.headers.get('X-Total-Count') ?? response.body?.length ?? 0),
    })));
  }
}
