import { Injectable, computed, signal } from '@angular/core';

import { StaffRole, StaffSessionResponse } from './staff-session.model';

/**
 * Signal-based staff session state (PRD §9), mirroring `BookingStateService`'s
 * pattern. Holds no long-lived persistence of its own — the `HttpOnly` session
 * cookie is the actual source of truth, and `session.guard.ts` repopulates this
 * from `GET /staff/auth/session` on every guarded route activation (including
 * after a hard page refresh, when this in-memory state is gone).
 */
@Injectable({ providedIn: 'root' })
export class StaffSessionService {
  private readonly _username = signal<string | null>(null);
  private readonly _role = signal<StaffRole | null>(null);
  private readonly _providerId = signal<number | null>(null);
  private readonly _sessionExpiresAt = signal<string | null>(null);

  readonly username = this._username.asReadonly();
  readonly role = this._role.asReadonly();
  readonly providerId = this._providerId.asReadonly();
  readonly sessionExpiresAt = this._sessionExpiresAt.asReadonly();
  readonly isAuthenticated = computed(() => this._username() !== null);

  setSession(session: StaffSessionResponse): void {
    this._username.set(session.username);
    this._role.set(session.role);
    this._providerId.set(session.providerId);
    this._sessionExpiresAt.set(session.sessionExpiresAt);
  }

  clear(): void {
    this._username.set(null);
    this._role.set(null);
    this._providerId.set(null);
    this._sessionExpiresAt.set(null);
  }
}
