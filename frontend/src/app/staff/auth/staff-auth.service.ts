import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { StaffSessionResponse } from './staff-session.model';

/** All HTTP calls for staff login/logout/session (PRD §8.20). */
@Injectable({ providedIn: 'root' })
export class StaffAuthService {
  private readonly baseUrl = '/api/v1/staff/auth';

  constructor(private readonly http: HttpClient) {}

  login(username: string, password: string): Observable<StaffSessionResponse> {
    return this.http.post<StaffSessionResponse>(
      `${this.baseUrl}/login`,
      { username, password },
      { withCredentials: true }
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/logout`, null, { withCredentials: true });
  }

  session(): Observable<StaffSessionResponse> {
    return this.http.get<StaffSessionResponse>(`${this.baseUrl}/session`, { withCredentials: true });
  }
}
