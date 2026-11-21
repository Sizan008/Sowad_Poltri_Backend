import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { NotePage, NotebookNote, SaveNotebookNote } from '../models/notebook.model';

@Injectable({ providedIn: 'root' })
export class NotebookService {
  private readonly http = inject(HttpClient);
  private readonly url = environment.apiBaseUrl + API_ENDPOINTS.NOTES;
  private readonly mutationUrl = environment.apiBaseUrl + API_ENDPOINTS.TX_NOTES;

  list(page: number, size: number, query = ''): Observable<NotePage> {
    let params = new HttpParams()
      .set('_sort', 'serial').set('_order', 'desc')
      .set('_page', page).set('_limit', size);
    if (query.trim()) params = params.set('q', query.trim());
    return this.http.get<NotebookNote[]>(this.url, { params, observe: 'response' }).pipe(
      map(response => ({
        items: response.body ?? [],
        total: Number(response.headers.get('X-Total-Count') ?? response.body?.length ?? 0),
      })),
    );
  }

  create(data: SaveNotebookNote): Observable<NotebookNote> {
    return this.http.post<NotebookNote>(this.mutationUrl, data);
  }
  update(id: string, data: SaveNotebookNote): Observable<NotebookNote> {
    return this.http.put<NotebookNote>(`${this.mutationUrl}/${encodeURIComponent(id)}`, data);
  }
  delete(id: string): Observable<{ deleted: boolean }> {
    return this.http.delete<{ deleted: boolean }>(`${this.mutationUrl}/${encodeURIComponent(id)}`);
  }
}
