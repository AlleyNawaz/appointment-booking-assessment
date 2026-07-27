import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { StaffAuthService } from './staff-auth.service';

describe('StaffAuthService', () => {
  let service: StaffAuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(StaffAuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('posts credentials to /login with credentials included (§8.20)', () => {
    service.login('jsmith', 'secret123').subscribe();

    const req = httpMock.expectOne('/api/v1/staff/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ username: 'jsmith', password: 'secret123' });
    expect(req.request.withCredentials).toBeTrue();
    req.flush({ username: 'jsmith', role: 'ROLE_STAFF', providerId: null, sessionExpiresAt: '2026-08-01T00:00:00Z' });
  });

  it('posts to /logout with credentials included', () => {
    service.logout().subscribe();

    const req = httpMock.expectOne('/api/v1/staff/auth/logout');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    req.flush(null);
  });

  it('gets /session with credentials included', () => {
    service.session().subscribe();

    const req = httpMock.expectOne('/api/v1/staff/auth/session');
    expect(req.request.method).toBe('GET');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({ username: 'jsmith', role: 'ROLE_STAFF', providerId: null, sessionExpiresAt: '2026-08-01T00:00:00Z' });
  });
});
