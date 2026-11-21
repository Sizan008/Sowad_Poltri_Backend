import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { BusinessSettings, UpdateBusinessSettingsRequest } from '../models/settings.model';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { environment } from '../../environments/environment';
import { LanguageService } from './language.service';

@Injectable({ providedIn: 'root' })
export class SettingsService {
  private readonly http = inject(HttpClient);
  private readonly language = inject(LanguageService);
  private readonly settingsState = signal<BusinessSettings | null>(null);
  readonly settings = this.settingsState.asReadonly();

  getSettings(): Observable<BusinessSettings> {
    return this.http.get<BusinessSettings>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.BUSINESS_SETTINGS}`,
    ).pipe(tap(value => {
      this.settingsState.set(value);
      this.language.setLanguage(value.uiLanguage ?? 'bn');
    }));
  }

  saveSettings(data: UpdateBusinessSettingsRequest): Observable<BusinessSettings> {
    return this.http.put<BusinessSettings>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.TX_SETTINGS}`,
      data,
    ).pipe(tap(value => {
      this.settingsState.set(value);
      this.language.setLanguage(value.uiLanguage ?? 'bn');
    }));
  }
}
