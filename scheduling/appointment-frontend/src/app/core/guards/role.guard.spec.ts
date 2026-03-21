import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { signal } from '@angular/core';
import { roleGuard } from './role.guard';
import { AuthService } from '../services/auth.service';

describe('roleGuard', () => {
  let authService: { roles: ReturnType<typeof vi.fn> };
  let router: { createUrlTree: ReturnType<typeof vi.fn> };

  const runGuard = (requiredRoles: string[]) => {
    const route = { data: { requiredRoles } } as unknown as ActivatedRouteSnapshot;
    const state = {} as RouterStateSnapshot;
    let result: unknown;
    TestBed.runInInjectionContext(() => {
      result = roleGuard(route, state);
    });
    return result;
  };

  beforeEach(() => {
    authService = { roles: vi.fn() };
    router = { createUrlTree: vi.fn().mockReturnValue({ toString: () => '/calendar' }) };
    TestBed.configureTestingModule({
      providers: [
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });
  });

  it('ADMIN 역할이 있을 때 true를 반환한다', () => {
    authService.roles.mockReturnValue(['ROLE_ADMIN']);
    const result = runGuard(['ROLE_ADMIN']);
    expect(result).toBe(true);
  });

  it('ADMIN 역할이 없을 때 /calendar로 리다이렉트한다', () => {
    authService.roles.mockReturnValue(['ROLE_PATIENT']);
    runGuard(['ROLE_ADMIN']);
    expect(router.createUrlTree).toHaveBeenCalledWith(['/calendar']);
  });

  it('requiredRoles가 비어 있을 때 true를 반환한다', () => {
    authService.roles.mockReturnValue([]);
    const result = runGuard([]);
    expect(result).toBe(true);
  });
});
