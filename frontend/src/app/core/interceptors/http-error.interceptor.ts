import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { ApiError } from '../models/api-error.model';
import { errorMessageFor } from '../error-messages.const';

/** Backend error shape (PRD §8) enriched with the resolved user-facing message (§9). */
export interface AppHttpError {
  status: number;
  errorCode?: string;
  userMessage: string;
  original: HttpErrorResponse;
}

/**
 * Global error handling (PRD §9): maps every backend `errorCode` to a
 * user-facing message via `error-messages.const.ts` so no page contains its
 * own ad hoc error strings. Rethrows an {@link AppHttpError} so callers can
 * still branch on `errorCode` (e.g. hold expiry returning to slot selection)
 * without redoing the lookup themselves.
 *
 * <p>§3/§6: a {@code FEATURE_DISABLED} response means the flag was flipped
 * off mid-session — handled here, once, for every wizard step's request
 * rather than duplicated per page, by navigating straight to `/book` (the
 * landing/unavailable state) so the stale wizard form is torn out of the DOM
 * immediately instead of being left rendered behind an inline error.
 */
export const httpErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        const apiError = error.error as Partial<ApiError> | null;
        if (apiError?.errorCode === 'FEATURE_DISABLED') {
          router.navigateByUrl('/book');
        }
        const appError: AppHttpError = {
          status: error.status,
          errorCode: apiError?.errorCode,
          userMessage: apiError?.message || errorMessageFor(apiError?.errorCode),
          original: error,
        };
        return throwError(() => appError);
      }
      return throwError(() => error);
    })
  );
};
