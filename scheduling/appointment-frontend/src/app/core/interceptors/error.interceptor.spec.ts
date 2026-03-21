import { TestBed } from '@angular/core/testing';
import { HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { throwError } from 'rxjs';
import { MatSnackBar } from '@angular/material/snack-bar';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  let snackBar: { open: ReturnType<typeof vi.fn> };

  const runInterceptor = (next: HttpHandlerFn) => {
    const req = new HttpRequest('GET', '/api/test');
    let error: unknown;
    TestBed.runInInjectionContext(() =>
      errorInterceptor(req, next).subscribe({ error: (e) => (error = e) })
    );
    return error;
  };

  beforeEach(() => {
    snackBar = { open: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: MatSnackBar, useValue: snackBar }],
    });
  });

  it('400 상태코드에서 스낵바에 "잘못된 요청" 표시', () => {
    const next: HttpHandlerFn = () =>
      throwError(() => new HttpErrorResponse({ status: 400 }));
    runInterceptor(next);
    expect(snackBar.open).toHaveBeenCalledWith('잘못된 요청', '닫기', { duration: 3000 });
  });

  it('404 상태코드에서 스낵바에 "찾을 수 없음" 표시', () => {
    const next: HttpHandlerFn = () =>
      throwError(() => new HttpErrorResponse({ status: 404 }));
    runInterceptor(next);
    expect(snackBar.open).toHaveBeenCalledWith('찾을 수 없음', '닫기', { duration: 3000 });
  });

  it('500 상태코드에서 스낵바에 "서버 오류" 표시', () => {
    const next: HttpHandlerFn = () =>
      throwError(() => new HttpErrorResponse({ status: 500 }));
    runInterceptor(next);
    expect(snackBar.open).toHaveBeenCalledWith('서버 오류', '닫기', { duration: 3000 });
  });
});
