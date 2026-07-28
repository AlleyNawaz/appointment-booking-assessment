import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { AppointmentApiService } from './appointment-api.service';

describe('AppointmentApiService', () => {
  let service: AppointmentApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AppointmentApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('sends filters as query params on list (§8.9)', () => {
    service.list({ status: 'PENDING', providerId: 5, page: 1, size: 20, sort: 'startDateTime,asc' }).subscribe();

    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/staff/appointments' && r.params.get('status') === 'PENDING'
    );
    expect(req.request.params.get('providerId')).toBe('5');
    expect(req.request.params.get('page')).toBe('1');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({ content: [], page: 1, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('sends the If-Match header on approve (§8.10)', () => {
    service.approve(42, 3).subscribe();

    const req = httpMock.expectOne('/api/v1/staff/appointments/42/approve');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('If-Match')).toBe('3');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({});
  });

  it('sends the reason body and If-Match header on reject (§8.10)', () => {
    service.reject(42, 3, 'Schedule conflict').subscribe();

    const req = httpMock.expectOne('/api/v1/staff/appointments/42/reject');
    expect(req.request.body).toEqual({ reason: 'Schedule conflict' });
    expect(req.request.headers.get('If-Match')).toBe('3');
    req.flush({});
  });

  it('retries exactly once when the first attempt is rejected with 403 (no CSRF cookie primed yet)', () => {
    let succeeded = false;
    service.complete(42, 3).subscribe(() => (succeeded = true));

    const first = httpMock.expectOne('/api/v1/staff/appointments/42/complete');
    first.flush({ errorCode: 'FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });

    const second = httpMock.expectOne('/api/v1/staff/appointments/42/complete');
    second.flush({});

    expect(succeeded).toBeTrue();
  });
});
