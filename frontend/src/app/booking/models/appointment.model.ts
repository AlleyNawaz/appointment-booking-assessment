/** Mirrors the appointments.status enum (PRD §7.7/§12.7). */
export type AppointmentStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'CANCELLED'
  | 'COMPLETED'
  | 'REJECTED'
  | 'EXPIRED'
  | 'MISSED';

/** POST /booking/appointments response (PRD §8.6). */
export interface AppointmentResponse {
  confirmationToken: string;
  status: AppointmentStatus;
  providerId: number;
  startDateTime: string;
}

/** GET/DELETE /booking/appointments/{confirmationToken} response (PRD §8.7/§8.8). */
export interface AppointmentDetailResponse {
  confirmationToken: string;
  providerName: string;
  appointmentTypeName: string;
  startDateTime: string;
  status: AppointmentStatus;
  cancellationEligible: boolean;
}

/** POST /booking/appointments request body (PRD §8.6) — no appointmentTypeId; resolved server-side from the hold. */
export interface CreateAppointmentRequest {
  holdToken: string;
  patientFullName: string;
  patientEmail: string;
  patientPhone: string;
  notes?: string;
}
