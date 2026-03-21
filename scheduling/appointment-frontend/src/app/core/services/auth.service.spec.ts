import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';

import { AuthService } from './auth.service';

/** Build a minimal JWT with given payload. */
function makeJwt(payload: Record<string, unknown>): string {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const body = btoa(JSON.stringify(payload));
  return `${header}.${body}.signature`;
}

describe('AuthService', () => {
  let store: Record<string, string>;
  let originalLocalStorage: Storage;

  function makeMockStorage(): Storage {
    return {
      getItem: (key: string) => store[key] ?? null,
      setItem: (key: string, value: string) => { store[key] = value; },
      removeItem: (key: string) => { delete store[key]; },
      clear: () => { for (const k of Object.keys(store)) delete store[k]; },
      key: (index: number) => Object.keys(store)[index] ?? null,
      get length() { return Object.keys(store).length; },
    } as Storage;
  }

  /** Install mock localStorage and create a fresh AuthService. */
  function createService(initialStore: Record<string, string> = {}): AuthService {
    store = { ...initialStore };
    (globalThis as any).localStorage = makeMockStorage();
    TestBed.configureTestingModule({});
    return TestBed.inject(AuthService);
  }

  beforeEach(() => {
    originalLocalStorage = (globalThis as any).localStorage;
    store = {};
  });

  afterEach(() => {
    (globalThis as any).localStorage = originalLocalStorage;
    vi.restoreAllMocks();
    TestBed.resetTestingModule();
  });

  it('서비스가 생성된다', () => {
    const service = createService();
    expect(service).toBeTruthy();
  });

  describe('setToken() / getToken() / removeToken()', () => {
    it('setToken()으로 저장된 토큰을 getToken()으로 가져올 수 있다', () => {
      const service = createService();
      const token = makeJwt({ roles: [] });
      service.setToken(token);
      expect(service.getToken()).toBe(token);
    });

    it('removeToken() 후 getToken()은 null을 반환한다', () => {
      const service = createService();
      service.setToken(makeJwt({ roles: [] }));
      service.removeToken();
      expect(service.getToken()).toBeNull();
    });

    it('토큰 없을 때 getToken()은 null을 반환한다', () => {
      const service = createService();
      expect(service.getToken()).toBeNull();
    });
  });

  describe('JWT 역할 파싱', () => {
    it('roles 배열이 있는 JWT를 파싱하여 roles signal에 반영한다', () => {
      const service = createService();
      service.setToken(makeJwt({ roles: ['ROLE_ADMIN', 'ROLE_STAFF'] }));
      expect(service.roles()).toContain('ROLE_ADMIN');
      expect(service.roles()).toContain('ROLE_STAFF');
    });

    it('role 단일 문자열을 파싱하여 배열로 저장한다', () => {
      const service = createService();
      service.setToken(makeJwt({ role: 'ROLE_PATIENT' }));
      expect(service.roles()).toContain('ROLE_PATIENT');
    });

    it('removeToken() 호출 시 roles signal이 빈 배열이 된다', () => {
      const service = createService();
      service.setToken(makeJwt({ roles: ['ROLE_ADMIN'] }));
      service.removeToken();
      expect(service.roles()).toEqual([]);
    });

    it('잘못된 형식의 토큰은 빈 배열로 처리한다', () => {
      // Seed a malformed token before service initializes
      const service = createService({ 'auth_token': 'header.!!!invalid!!!.sig' });
      expect(service.roles()).toEqual([]);
    });

    it('토큰이 없으면 roles signal은 빈 배열이다', () => {
      const service = createService();
      expect(service.roles()).toEqual([]);
    });
  });

  describe('computed role signals', () => {
    it('ROLE_ADMIN 토큰 → isAdmin()이 true이다', () => {
      const service = createService();
      service.setToken(makeJwt({ roles: ['ROLE_ADMIN'] }));
      expect(service.isAdmin()).toBe(true);
    });

    it('ROLE_ADMIN 토큰 → isStaff(), isDoctor(), isPatient()는 false이다', () => {
      const service = createService();
      service.setToken(makeJwt({ roles: ['ROLE_ADMIN'] }));
      expect(service.isStaff()).toBe(false);
      expect(service.isDoctor()).toBe(false);
      expect(service.isPatient()).toBe(false);
    });

    it('ROLE_STAFF 토큰 → isStaff()가 true이다', () => {
      const service = createService();
      service.setToken(makeJwt({ roles: ['ROLE_STAFF'] }));
      expect(service.isStaff()).toBe(true);
    });

    it('ROLE_DOCTOR 토큰 → isDoctor()가 true이다', () => {
      const service = createService();
      service.setToken(makeJwt({ roles: ['ROLE_DOCTOR'] }));
      expect(service.isDoctor()).toBe(true);
    });

    it('ROLE_PATIENT 토큰 → isPatient()가 true이다', () => {
      const service = createService();
      service.setToken(makeJwt({ roles: ['ROLE_PATIENT'] }));
      expect(service.isPatient()).toBe(true);
    });

    it('토큰 없을 때 모든 role computed는 false이다', () => {
      const service = createService();
      expect(service.isAdmin()).toBe(false);
      expect(service.isStaff()).toBe(false);
      expect(service.isDoctor()).toBe(false);
      expect(service.isPatient()).toBe(false);
    });
  });

  describe('isAuthenticated', () => {
    it('토큰이 있으면 isAuthenticated()가 true이다', () => {
      const service = createService({ 'auth_token': makeJwt({ roles: [] }) });
      expect(service.isAuthenticated()).toBe(true);
    });

    it('토큰이 없으면 isAuthenticated()가 false이다', () => {
      const service = createService();
      expect(service.isAuthenticated()).toBe(false);
    });
  });
});
