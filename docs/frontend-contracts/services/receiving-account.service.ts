import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { ReceivingAccount, SaveReceivingAccount } from '../models/receiving-account.model';

@Injectable({ providedIn: 'root' })
export class ReceivingAccountService {
  private readonly http = inject(HttpClient);
  private readonly listUrl = `${environment.apiBaseUrl}${API_ENDPOINTS.RECEIVING_ACCOUNTS}`;
  private readonly txUrl = `${environment.apiBaseUrl}${API_ENDPOINTS.TX_RECEIVING_ACCOUNTS}`;

  list(): Observable<ReceivingAccount[]> {
    return this.http.get<ReceivingAccount[]>(this.listUrl);
  }

  add(data: SaveReceivingAccount): Observable<ReceivingAccount> {
    return this.http.post<ReceivingAccount>(this.txUrl, data);
  }

  update(id: string, data: SaveReceivingAccount): Observable<ReceivingAccount> {
    return this.http.put<ReceivingAccount>(`${this.txUrl}/${encodeURIComponent(id)}`, data);
  }

  deactivate(id: string): Observable<ReceivingAccount> {
    return this.http.delete<ReceivingAccount>(`${this.txUrl}/${encodeURIComponent(id)}`);
  }
}
