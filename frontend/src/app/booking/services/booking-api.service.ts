import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AppointmentDetailResponse,
  AppointmentResponse,
  CreateAppointmentRequest,
  RescheduleRequest,
  RescheduleResponse,
} from '../models/appointment.model';
import { AppointmentType } from '../models/appointment-type.model';
import { Provider } from '../models/provider.model';
import { AvailabilityResponse, SlotHold } from '../models/slot-hold.model';

/** All HTTP calls for the booking flow (PRD §8) — pages never call HttpClient directly (§9). */
@Injectable({ providedIn: 'root' })
export class BookingApiService {
  private readonly baseUrl = '/api/v1/booking';

  constructor(private readonly http: HttpClient) {}

  getConfig(): Observable<{ enabled: boolean }> {
    return this.http.get<{ enabled: boolean }>(`${this.baseUrl}/config`);
  }

  getAppointmentTypes(): Observable<AppointmentType[]> {
    return this.http.get<AppointmentType[]>(`${this.baseUrl}/appointment-types`);
  }

  getProviders(appointmentTypeId: number): Observable<Provider[]> {
    return this.http.get<Provider[]>(`${this.baseUrl}/providers`, {
      params: { appointmentTypeId },
    });
  }

  getAvailability(providerId: number, appointmentTypeId: number, date: string): Observable<AvailabilityResponse> {
    return this.http.get<AvailabilityResponse>(`${this.baseUrl}/availability`, {
      params: { providerId, appointmentTypeId, date },
    });
  }

  createHold(providerId: number, appointmentTypeId: number, startDateTime: string): Observable<SlotHold> {
    return this.http.post<SlotHold>(`${this.baseUrl}/holds`, { providerId, appointmentTypeId, startDateTime });
  }

  createAppointment(request: CreateAppointmentRequest, idempotencyKey: string): Observable<AppointmentResponse> {
    return this.http.post<AppointmentResponse>(`${this.baseUrl}/appointments`, request, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  getAppointment(confirmationToken: string): Observable<AppointmentDetailResponse> {
    return this.http.get<AppointmentDetailResponse>(`${this.baseUrl}/appointments/${confirmationToken}`);
  }

  cancelAppointment(confirmationToken: string, reason?: string): Observable<AppointmentDetailResponse> {
    return this.http.delete<AppointmentDetailResponse>(`${this.baseUrl}/appointments/${confirmationToken}`, {
      body: reason ? { reason } : undefined,
    });
  }

  reschedule(
    confirmationToken: string,
    request: RescheduleRequest,
    idempotencyKey: string
  ): Observable<RescheduleResponse> {
    return this.http.post<RescheduleResponse>(
      `${this.baseUrl}/appointments/${confirmationToken}/reschedule`,
      request,
      { headers: { 'Idempotency-Key': idempotencyKey } }
    );
  }
}
