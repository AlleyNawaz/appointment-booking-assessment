export type StaffRole = 'ROLE_STAFF' | 'ROLE_PROVIDER' | 'ROLE_ADMIN' | 'ROLE_SYSADMIN';

/** Response shape shared by login/session (PRD §8.20). */
export interface StaffSessionResponse {
  username: string;
  role: StaffRole;
  providerId: number | null;
  sessionExpiresAt: string;
}
