import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { tap } from 'rxjs/operators';
import { ApiResponse } from '../models/api-response.model';

const STATUS_MESSAGES: Record<number, string> = {
  400: '잘못된 요청',
  401: '인증 필요',
  403: '권한 없음',
  404: '찾을 수 없음',
  409: '충돌',
  500: '서버 오류',
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
      const message =
        STATUS_MESSAGES[error.status] ?? `오류 (${error.status})`;
      show(message);
      return throwError(() => error);
    })
  );
};
