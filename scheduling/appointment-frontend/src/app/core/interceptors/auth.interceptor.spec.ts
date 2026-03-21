import { TestBed } from '@angular/core/testing';
import { HttpRequest, HttpHandlerFn, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';

describe('authInterceptor', () => {
  let authService: { getToken: ReturnType<typeof vi.fn> };

  const runInterceptor = (req: HttpRequest<unknown>, token: string | null) => {
    authService.getToken.mockReturnValue(token);
    let capturedReq: HttpRequest<unknown> | undefined;
    const next: HttpHandlerFn = (r) => {
      capturedReq = r as HttpRequest<unknown>;
      return of(new HttpResponse({ status: 200 }));
    };
    TestBed.runInInjectionContext(() => authInterceptor(req, next));
    return capturedReq;
  };

  beforeEach(() => {
    authService = { getToken: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: authService }],
    });
  });

  it('토큰이 있을 때 Authorization 헤더를 추가한다', () => {
    const req = new HttpRequest('GET', '/api/test');
    const result = runInterceptor(req, 'my-token');
    expect(result?.headers.get('Authorization')).toBe('Bearer my-token');
  });

  it('토큰이 없을 때 Authorization 헤더를 추가하지 않는다', () => {
    const req = new HttpRequest('GET', '/api/test');
    const result = runInterceptor(req, null);
    expect(result?.headers.get('Authorization')).toBeNull();
  });
});
