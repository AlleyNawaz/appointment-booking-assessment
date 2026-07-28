/** One row of GET /staff/audit-log (PRD §8.18). */
export interface AuditLogEntry {
  appointmentId: number;
  previousStatus: string | null;
  newStatus: string;
  changedBy: string;
  reason: string | null;
  changedAt: string;
}

/** The standard page envelope (PRD §8.9/§8.18). */
export interface AuditLogPageResponse {
  content: AuditLogEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface AuditLogFilter {
  appointmentId?: number;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}
