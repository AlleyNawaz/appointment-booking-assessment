import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { AuditLogFilter, AuditLogPageResponse } from './audit-log.model';

/** HTTP calls for the audit-log viewer (PRD §8.18). */
@Injectable({ providedIn: 'root' })
export class AuditLogApiService {
  private readonly baseUrl = '/api/v1/staff/audit-log';

  constructor(private readonly http: HttpClient) {}

  list(filter: AuditLogFilter): Observable<AuditLogPageResponse> {
    let params = new HttpParams()
      .set('page', filter.page ?? 0)
      .set('size', filter.size ?? 20)
      .set('sort', filter.sort ?? 'changedAt,asc');
    if (filter.appointmentId != null) {
      params = params.set('appointmentId', filter.appointmentId);
    }
    if (filter.from) {
      params = params.set('from', filter.from);
    }
    if (filter.to) {
      params = params.set('to', filter.to);
    }
    return this.http.get<AuditLogPageResponse>(this.baseUrl, { params, withCredentials: true });
  }
}
