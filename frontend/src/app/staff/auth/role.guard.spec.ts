import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, provideRouter } from '@angular/router';

import { roleGuard } from './role.guard';
import { StaffSessionService } from './staff-session.service';

describe('roleGuard', () => {
  function runGuard(roles: string[] | undefined) {
    const route = { data: { roles } } as unknown as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(() => roleGuard(route, {} as any));
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  it('allows activation when the route declares no roles restriction', () => {
    expect(runGuard(undefined)).toBeTrue();
  });

  it('allows activation when the current role is in the allowed list', () => {
    TestBed.inject(StaffSessionService).setSession({
      username: 'jsmith',
      role: 'ROLE_ADMIN',
      providerId: null,
      sessionExpiresAt: '2026-08-01T00:00:00Z',
    });

    expect(runGuard(['ROLE_ADMIN', 'ROLE_SYSADMIN'])).toBeTrue();
  });

  it('redirects to /staff/login when the current role is not permitted', () => {
    TestBed.inject(StaffSessionService).setSession({
      username: 'jsmith',
      role: 'ROLE_STAFF',
      providerId: null,
      sessionExpiresAt: '2026-08-01T00:00:00Z',
    });
    const router = TestBed.inject(Router);
    spyOn(router, 'parseUrl').and.callThrough();

    const result = runGuard(['ROLE_ADMIN']) as any;

    expect(router.parseUrl).toHaveBeenCalledWith('/staff/login');
    expect(result.toString()).toBe('/staff/login');
  });

  it('redirects to /staff/login when no session is populated at all', () => {
    const result = runGuard(['ROLE_ADMIN']) as any;
    expect(result.toString()).toBe('/staff/login');
  });
});
