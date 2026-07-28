import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { retry } from 'rxjs/operators';

import { AppointmentListFilter, AppointmentPageResponse, StaffAppointment } from './staff-appointment.model';

/**
 * All HTTP calls for the staff appointment console (PRD §8.9/§8.10). Staff
 * endpoints use session cookies (§15.4), so every call needs credentials.
 *
 * <p>The very first state-changing request in a browser session has no CSRF
 * cookie yet — the CSRF filter only issues one on a non-safe-method request
 * (§15.4's synchronizer-token scheme), so nothing before it (login is
 * CSRF-exempt; every session/list call is a safe `GET`) can prime it in
 * advance. That first attempt is rejected once (`403`) but the rejection
 * itself sets the cookie, so retrying immediately succeeds — {@link retry}
 * here does exactly that, once, only for a `403`.
 */
@Injectable({ providedIn: 'root' })
export class AppointmentApiService {
  private readonly baseUrl = '/api/v1/staff/appointments';

  constructor(private readonly http: HttpClient) {}

  list(filter: AppointmentListFilter): Observable<AppointmentPageResponse> {
    let params = new HttpParams()
      .set('page', filter.page ?? 0)
      .set('size', filter.size ?? 20)
      .set('sort', filter.sort ?? 'startDateTime,asc');
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.providerId != null) {
      params = params.set('providerId', filter.providerId);
    }
    if (filter.from) {
      params = params.set('from', filter.from);
    }
    if (filter.to) {
      params = params.set('to', filter.to);
    }
    return this.http.get<AppointmentPageResponse>(this.baseUrl, { params, withCredentials: true });
  }

  approve(id: number, version: number): Observable<StaffAppointment> {
    return this.post(`${this.baseUrl}/${id}/approve`, null, version);
  }

  reject(id: number, version: number, reason: string): Observable<StaffAppointment> {
    return this.post(`${this.baseUrl}/${id}/reject`, { reason }, version);
  }

  complete(id: number, version: number): Observable<StaffAppointment> {
    return this.post(`${this.baseUrl}/${id}/complete`, null, version);
  }

  private post(url: string, body: unknown, version: number): Observable<StaffAppointment> {
    return this.http
      .post<StaffAppointment>(url, body, {
        withCredentials: true,
        headers: { 'If-Match': String(version) },
      })
      .pipe(retry({ count: 1, delay: (error) => (error?.status === 403 ? of(0) : throwError(() => error)) }));
  }
}
