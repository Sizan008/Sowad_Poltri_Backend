import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { Unit } from '../models/unit.model';

@Injectable({ providedIn: 'root' })
export class UnitService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}${API_ENDPOINTS.UNITS}`;

  getUnits(): Observable<Unit[]> {
    return this.http.get<Unit[]>(this.url);
  }

  getUnit(id: string): Observable<Unit> {
    return this.http.get<Unit>(`${this.url}/${encodeURIComponent(id)}`);
  }

  createUnit(data: Unit): Observable<Unit> {
    return this.http.post<Unit>(this.url, data);
  }

  updateUnit(id: string, data: Unit): Observable<Unit> {
    return this.http.put<Unit>(
      `${this.url}/${encodeURIComponent(id)}`,
      data,
    );
  }

  deleteUnit(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(id)}`);
  }
}
