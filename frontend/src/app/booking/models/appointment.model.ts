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
  providerId: number;
  appointmentTypeId: number;
}

/** POST /booking/appointments request body (PRD §8.6) — no appointmentTypeId; resolved server-side from the hold. */
export interface CreateAppointmentRequest {
  holdToken: string;
  patientFullName: string;
  patientEmail: string;
  patientPhone: string;
  notes?: string;
}

/** POST /booking/appointments/{confirmationToken}/reschedule request body (PRD §8.19). */
export interface RescheduleRequest {
  holdToken: string;
  reason?: string;
}

/** POST /booking/appointments/{confirmationToken}/reschedule response (PRD §8.19). */
export interface RescheduleResponse {
  confirmationToken: string;
  status: AppointmentStatus;
  providerId: number;
  startDateTime: string;
  previousConfirmationToken: string;
}
