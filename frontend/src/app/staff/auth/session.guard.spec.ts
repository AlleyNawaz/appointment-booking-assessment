import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { sessionGuard } from './session.guard';
import { StaffAuthService } from './staff-auth.service';
import { StaffSessionService } from './staff-session.service';

describe('sessionGuard', () => {
  function runGuard() {
    return TestBed.runInInjectionContext(() => sessionGuard({} as any, {} as any));
  }

  it('allows activation and populates StaffSessionService when the session call succeeds', (done) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: StaffAuthService,
          useValue: {
            session: () =>
              of({ username: 'jsmith', role: 'ROLE_STAFF', providerId: null, sessionExpiresAt: '2026-08-01T00:00:00Z' }),
          },
        },
      ],
    });

    const result = runGuard() as any;
    result.subscribe((value: unknown) => {
      expect(value).toBeTrue();
      const staffSession = TestBed.inject(StaffSessionService);
      expect(staffSession.isAuthenticated()).toBeTrue();
      expect(staffSession.username()).toBe('jsmith');
      done();
    });
  });

  it('redirects to /staff/login and clears the session when unauthenticated', (done) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: StaffAuthService, useValue: { session: () => throwError(() => new Error('401')) } },
      ],
    });
    const router = TestBed.inject(Router);
    spyOn(router, 'parseUrl').and.callThrough();

    const result = runGuard() as any;
    result.subscribe((value: any) => {
      expect(router.parseUrl).toHaveBeenCalledWith('/staff/login');
      expect(value.toString()).toBe('/staff/login');
      expect(TestBed.inject(StaffSessionService).isAuthenticated()).toBeFalse();
      done();
    });
  });
});
