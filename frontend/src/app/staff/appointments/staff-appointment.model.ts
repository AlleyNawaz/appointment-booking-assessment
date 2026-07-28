import { AppointmentStatus } from '../../booking/models/appointment.model';

/** Staff-facing appointment shape (PRD §8.9/§8.10) — includes `id`/`version`, unlike the patient-facing shape. */
export interface StaffAppointment {
  id: number;
  confirmationToken: string;
  providerId: number;
  appointmentTypeId: number;
  patientFullName: string;
  patientEmail: string;
  patientPhone: string;
  notes: string | null;
  startDateTime: string;
  endDateTime: string;
  status: AppointmentStatus;
  version: number;
  createdAt: string;
}

/** The standard page envelope (PRD §8.9). */
export interface AppointmentPageResponse {
  content: StaffAppointment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AppointmentListFilter {
  status?: AppointmentStatus;
  providerId?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}
