import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { CreateInvoiceRequest, Invoice, InvoiceItem } from '../models/invoice.model';

@Injectable({ providedIn: 'root' })
export class InvoiceService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}${API_ENDPOINTS.INVOICES}`;
  private readonly itemsUrl = `${environment.apiBaseUrl}${API_ENDPOINTS.INVOICE_ITEMS}`;

  getInvoices(): Observable<Invoice[]> {
    return this.http.get<Invoice[]>(this.url);
  }

  getInvoice(id: string): Observable<Invoice> {
    return this.http.get<Invoice>(`${this.url}/${encodeURIComponent(id)}`);
  }

  getInvoiceItems(): Observable<InvoiceItem[]> {
    return this.http.get<InvoiceItem[]>(this.itemsUrl);
  }

  createInvoice(data: CreateInvoiceRequest): Observable<Invoice> {
    return this.http.post<Invoice>(
      `${environment.apiBaseUrl}${API_ENDPOINTS.TX_INVOICE}`,
      data,
    );
  }
}
