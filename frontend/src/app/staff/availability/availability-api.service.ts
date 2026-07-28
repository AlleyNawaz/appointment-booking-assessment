import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AvailabilityRule,
  AvailabilityRuleRequest,
  Holiday,
  HolidayRequest,
  Unavailability,
  UnavailabilityRequest,
} from './availability.model';

/** HTTP calls for the availability screens (PRD §8.14/§8.15/§8.16). */
@Injectable({ providedIn: 'root' })
export class AvailabilityApiService {
  private readonly baseUrl = '/api/v1/staff';

  constructor(private readonly http: HttpClient) {}

  listRules(providerId: number): Observable<AvailabilityRule[]> {
    return this.http.get<AvailabilityRule[]>(`${this.baseUrl}/providers/${providerId}/availability-rules`, {
      withCredentials: true,
    });
  }

  createRule(providerId: number, request: AvailabilityRuleRequest): Observable<AvailabilityRule> {
    return this.http.post<AvailabilityRule>(
      `${this.baseUrl}/providers/${providerId}/availability-rules`,
      request,
      { withCredentials: true }
    );
  }

  updateRule(id: number, request: AvailabilityRuleRequest): Observable<AvailabilityRule> {
    return this.http.put<AvailabilityRule>(`${this.baseUrl}/availability-rules/${id}`, request, {
      withCredentials: true,
    });
  }

  deleteRule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/availability-rules/${id}`, { withCredentials: true });
  }

  listUnavailability(providerId: number): Observable<Unavailability[]> {
    return this.http.get<Unavailability[]>(`${this.baseUrl}/providers/${providerId}/unavailability`, {
      withCredentials: true,
    });
  }

  createUnavailability(providerId: number, request: UnavailabilityRequest): Observable<Unavailability> {
    return this.http.post<Unavailability>(
      `${this.baseUrl}/providers/${providerId}/unavailability`,
      request,
      { withCredentials: true }
    );
  }

  deleteUnavailability(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/unavailability/${id}`, { withCredentials: true });
  }

  listHolidays(): Observable<Holiday[]> {
    return this.http.get<Holiday[]>(`${this.baseUrl}/holidays`, { withCredentials: true });
  }

  createHoliday(request: HolidayRequest): Observable<Holiday> {
    return this.http.post<Holiday>(`${this.baseUrl}/holidays`, request, { withCredentials: true });
  }

  updateHoliday(id: number, request: HolidayRequest): Observable<Holiday> {
    return this.http.put<Holiday>(`${this.baseUrl}/holidays/${id}`, request, { withCredentials: true });
  }

  deleteHoliday(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/holidays/${id}`, { withCredentials: true });
  }
}
