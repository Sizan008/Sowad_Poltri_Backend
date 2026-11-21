import { Injectable, computed, signal, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../environments/environment';
import { API_ENDPOINTS } from '../onboarding/constants/api-endpoints';
import { DemoRole, DemoUser } from '../models/auth.model';
import { DEMO_ACCOUNTS } from '../onboarding/constants/demo-accounts.constants';

const SESSION_KEY = 'nourish-demo-user-v1';
const LEGACY_SESSION_KEY = 'krishi-bondhu-demo-user-v1';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly currentUser = signal<DemoUser | null>(this.readSession());
  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = computed(() => this.currentUser() !== null);

  login(username: string, password: string): boolean {
    const account = DEMO_ACCOUNTS.find(candidate =>
      candidate.username === username.trim().toLowerCase() &&
      candidate.password === password,
    );
    if (!account) return false;

    const user: DemoUser = {
      id: account.id,
      username: account.username,
      displayName: account.displayName,
      role: account.role,
    };
    this.currentUser.set(user);
    this.auditSession('login');
    // Store only demo identity, never the (public) demo password.
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(SESSION_KEY, account.id);
    }
    return true;
  }

  logout(): void {
    this.auditSession('logout');
    this.currentUser.set(null);
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(SESSION_KEY);
      localStorage.removeItem(LEGACY_SESSION_KEY);
    }
  }

  private auditSession(event: 'login' | 'logout'): void {
    // Best-effort demo history: login never depends on JSON Server being online.
    this.http.post(`${environment.apiBaseUrl}${API_ENDPOINTS.SESSION_EVENT}`, { event })
      .subscribe({ error: () => undefined });
  }

  hasAnyRole(roles: readonly DemoRole[]): boolean {
    const role = this.currentUser()?.role;
    return Boolean(role && roles.includes(role));
  }

  private readSession(): DemoUser | null {
    if (typeof localStorage === 'undefined') return null;
    const id = localStorage.getItem(SESSION_KEY)
      ?? localStorage.getItem(LEGACY_SESSION_KEY);
    const account = DEMO_ACCOUNTS.find(item => item.id === id);
    if (!account) return null;
    localStorage.setItem(SESSION_KEY, account.id);
    localStorage.removeItem(LEGACY_SESSION_KEY);
    return {
      id: account.id,
      username: account.username,
      displayName: account.displayName,
      role: account.role,
    };
  }
}
