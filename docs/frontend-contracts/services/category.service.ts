import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { Category } from '../models/category.model';

@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}${API_ENDPOINTS.CATEGORIES}`;

  getCategories(): Observable<Category[]> {
    return this.http.get<Category[]>(this.url);
  }

  getCategory(id: string): Observable<Category> {
    return this.http.get<Category>(`${this.url}/${encodeURIComponent(id)}`);
  }

  createCategory(data: Category): Observable<Category> {
    return this.http.post<Category>(this.url, data);
  }

  updateCategory(id: string, data: Category): Observable<Category> {
    return this.http.put<Category>(
      `${this.url}/${encodeURIComponent(id)}`,
      data,
    );
  }

  deleteCategory(id: string): Observable<void> {
    return this.http.delete<void>(`${this.url}/${encodeURIComponent(id)}`);
  }
}
