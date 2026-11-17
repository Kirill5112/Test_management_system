import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { ErrorResponse } from '../models/error-response.model';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const body = err.error as Partial<ErrorResponse> | undefined;
      const message = body?.message
        ?? (err.status === 0 ? 'Сервер недоступен' : `Ошибка ${err.status}`);

      snackBar.open(message, 'OK', { duration: 5000 });
      return throwError(() => err);
    })
  );
};
