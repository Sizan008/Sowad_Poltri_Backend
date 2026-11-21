import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { CreatePaymentRequest, Payment } from '../models/payment.model';

@Injectable({ providedIn: 'root' })
export class PaymentService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}${API_ENDPOINTS.PAYMENTS}`;

  getPayments(): Observable<Payment[]> {
    return this.http.get<Payment[]>(this.url);
  }

  recordPayment(data: CreatePaymentRequest): Observable<Payment> {
    return this.http.post<Payment>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.TX_PAYMENT}`,
      data,
    );
  }
}
