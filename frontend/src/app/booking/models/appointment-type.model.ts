/** GET /booking/appointment-types response item (PRD §8.2). */
export interface AppointmentType {
  id: number;
  code: string;
  displayName: string;
  durationMinutes: number;
  requiresApproval: boolean;
}
