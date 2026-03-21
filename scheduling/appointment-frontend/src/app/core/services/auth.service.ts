import { Injectable, computed, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly TOKEN_KEY = 'auth_token';

  private readonly _roles = signal<string[]>(this._parseRoles());

  readonly roles = this._roles.asReadonly();

  readonly isAuthenticated = computed(() => !!this.getToken());

  readonly isAdmin = computed(() => this._roles().includes('ROLE_ADMIN'));
  readonly isStaff = computed(() => this._roles().includes('ROLE_STAFF'));
  readonly isDoctor = computed(() => this._roles().includes('ROLE_DOCTOR'));
  readonly isPatient = computed(() => this._roles().includes('ROLE_PATIENT'));

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  setToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
    this._roles.set(this._parseRoles());
  }

  removeToken(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    this._roles.set([]);
  }

  private _parseRoles(): string[] {
    const token = localStorage.getItem(this.TOKEN_KEY);
    if (!token) return [];
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      const roles = payload['roles'] ?? payload['role'] ?? [];
      return Array.isArray(roles) ? roles : [roles];
    } catch {
      return [];
    }
  }
}
