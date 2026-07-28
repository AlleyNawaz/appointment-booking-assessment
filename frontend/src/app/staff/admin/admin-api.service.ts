import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { AppointmentTypeAdmin, AppointmentTypeRequest, FeatureFlag, ProviderAdmin, ProviderRequest } from './admin.model';

/** HTTP calls for the admin console screens (PRD §8.12/§8.13/§8.17). */
@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly baseUrl = '/api/v1/staff';

  constructor(private readonly http: HttpClient) {}

  listAppointmentTypes(): Observable<AppointmentTypeAdmin[]> {
    return this.http.get<AppointmentTypeAdmin[]>(`${this.baseUrl}/appointment-types`, { withCredentials: true });
  }

  createAppointmentType(request: AppointmentTypeRequest): Observable<AppointmentTypeAdmin> {
    return this.http.post<AppointmentTypeAdmin>(`${this.baseUrl}/appointment-types`, request, {
      withCredentials: true,
    });
  }

  updateAppointmentType(id: number, request: AppointmentTypeRequest): Observable<AppointmentTypeAdmin> {
    return this.http.put<AppointmentTypeAdmin>(`${this.baseUrl}/appointment-types/${id}`, request, {
      withCredentials: true,
    });
  }

  deactivateAppointmentType(id: number): Observable<AppointmentTypeAdmin> {
    return this.http.delete<AppointmentTypeAdmin>(`${this.baseUrl}/appointment-types/${id}`, {
      withCredentials: true,
    });
  }

  listProviders(): Observable<ProviderAdmin[]> {
    return this.http.get<ProviderAdmin[]>(`${this.baseUrl}/providers`, { withCredentials: true });
  }

  createProvider(request: ProviderRequest): Observable<ProviderAdmin> {
    return this.http.post<ProviderAdmin>(`${this.baseUrl}/providers`, request, { withCredentials: true });
  }

  updateProvider(id: number, request: ProviderRequest): Observable<ProviderAdmin> {
    return this.http.put<ProviderAdmin>(`${this.baseUrl}/providers/${id}`, request, { withCredentials: true });
  }

  softDeleteProvider(id: number): Observable<ProviderAdmin> {
    return this.http.delete<ProviderAdmin>(`${this.baseUrl}/providers/${id}`, { withCredentials: true });
  }

  replaceProviderAppointmentTypes(id: number, appointmentTypeIds: number[]): Observable<ProviderAdmin> {
    return this.http.put<ProviderAdmin>(
      `${this.baseUrl}/providers/${id}/appointment-types`,
      { appointmentTypeIds },
      { withCredentials: true }
    );
  }

  getFeatureFlag(flagName: string): Observable<FeatureFlag> {
    return this.http.get<FeatureFlag>(`${this.baseUrl}/feature-flags/${flagName}`, { withCredentials: true });
  }

  updateFeatureFlag(flagName: string, isEnabled: boolean): Observable<FeatureFlag> {
    return this.http.put<FeatureFlag>(
      `${this.baseUrl}/feature-flags/${flagName}`,
      { isEnabled },
      { withCredentials: true }
    );
  }
}
