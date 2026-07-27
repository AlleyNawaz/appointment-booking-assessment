import { TestBed } from '@angular/core/testing';

import { StaffSessionService } from './staff-session.service';

describe('StaffSessionService', () => {
  let service: StaffSessionService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(StaffSessionService);
  });

  it('starts unauthenticated', () => {
    expect(service.isAuthenticated()).toBeFalse();
    expect(service.username()).toBeNull();
    expect(service.role()).toBeNull();
  });

  it('populates signals from a session response and reports authenticated', () => {
    service.setSession({
      username: 'jsmith',
      role: 'ROLE_PROVIDER',
      providerId: 5,
      sessionExpiresAt: '2026-08-01T00:00:00Z',
    });

    expect(service.isAuthenticated()).toBeTrue();
    expect(service.username()).toBe('jsmith');
    expect(service.role()).toBe('ROLE_PROVIDER');
    expect(service.providerId()).toBe(5);
    expect(service.sessionExpiresAt()).toBe('2026-08-01T00:00:00Z');
  });

  it('clears back to unauthenticated', () => {
    service.setSession({
      username: 'jsmith',
      role: 'ROLE_STAFF',
      providerId: null,
      sessionExpiresAt: '2026-08-01T00:00:00Z',
    });

    service.clear();

    expect(service.isAuthenticated()).toBeFalse();
    expect(service.username()).toBeNull();
    expect(service.role()).toBeNull();
    expect(service.providerId()).toBeNull();
    expect(service.sessionExpiresAt()).toBeNull();
  });
});
