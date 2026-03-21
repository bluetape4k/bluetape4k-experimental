import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { tap } from 'rxjs/operators';
import { ApiResponse } from '../models/api-response.model';

const STATUS_MESSAGES: Record<number, string> = {
  0: '서버에 연결할 수 없습니다',
  400: '잘못된 요청',
  401: '인증 필요',
  403: '권한 없음',
  404: '찾을 수 없음',
  409: '충돌',
  500: '서버 오류',
  502: '서버에 연결할 수 없습니다',
  503: '서비스를 사용할 수 없습니다',
};

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);

  const show = (message: string) =>
    snackBar.open(message, '닫기', { duration: 3000 });

  return next(req).pipe(
    tap({
      next: (event) => {
        if ('body' in event && event.body) {
          const body = event.body as ApiResponse<unknown>;
          if (body.success === false) {
            show(body.error ?? '요청 처리 중 오류가 발생했습니다.');
          }
        }
      },
    }),
    catchError((error: HttpErrorResponse) => {
      // status 200 + JSON parse error: 백엔드 미실행 시 SPA fallback 응답
      if (error.status === 200 || error.status === 0) {
        show('서버에 연결할 수 없습니다. 백엔드가 실행 중인지 확인해 주세요.');
      } else {
        const message =
          STATUS_MESSAGES[error.status] ?? `오류 (${error.status})`;
        show(message);
      }
      return throwError(() => error);
    })
  );
};
