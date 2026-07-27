import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AppHttpError, httpErrorInterceptor } from './http-error.interceptor';

describe('httpErrorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([httpErrorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
  });

  afterEach(() => httpMock.verify());

  it('maps a backend errorCode to its user-facing message', (done) => {
    http.get('/api/v1/booking/holds').subscribe({
      error: (err: AppHttpError) => {
        expect(err.errorCode).toBe('SLOT_ALREADY_BOOKED');
        expect(err.userMessage).toBe('This time slot is no longer available.');
        expect(err.status).toBe(409);
        done();
      },
    });

    httpMock
      .expectOne('/api/v1/booking/holds')
      .flush(
        { errorCode: 'SLOT_ALREADY_BOOKED', message: 'This time slot is no longer available.' },
        { status: 409, statusText: 'Conflict' }
      );
  });

  it('falls back to the default message for an unrecognized errorCode', (done) => {
    http.get('/api/v1/booking/config').subscribe({
      error: (err: AppHttpError) => {
        expect(err.userMessage).toBe('Something went wrong. Please try again.');
        done();
      },
    });

    httpMock.expectOne('/api/v1/booking/config').flush({}, { status: 500, statusText: 'Server Error' });
  });

  it('does not navigate away for SLOT_HOLD_EXPIRED — pages handle it themselves', (done) => {
    http.post('/api/v1/booking/appointments', {}).subscribe({
      error: (err: AppHttpError) => {
        expect(err.errorCode).toBe('SLOT_HOLD_EXPIRED');
        expect(router.navigateByUrl).not.toHaveBeenCalled();
        done();
      },
    });

    httpMock
      .expectOne('/api/v1/booking/appointments')
      .flush({ errorCode: 'SLOT_HOLD_EXPIRED', message: 'Hold expired.' }, { status: 410, statusText: 'Gone' });
  });

  it('does not navigate away for an unrelated error code', (done) => {
    http.get('/api/v1/booking/appointment-types').subscribe({
      error: () => {
        expect(router.navigateByUrl).not.toHaveBeenCalled();
        done();
      },
    });

    httpMock
      .expectOne('/api/v1/booking/appointment-types')
      .flush({ errorCode: 'VALIDATION_ERROR', message: 'Bad input.' }, { status: 400, statusText: 'Bad Request' });
  });

  const mutatingRequests: Array<[string, () => unknown]> = [
    ['POST /holds', () => http.post('/api/v1/booking/holds', {})],
    ['POST /appointments', () => http.post('/api/v1/booking/appointments', {})],
    ['GET /appointment-types', () => http.get('/api/v1/booking/appointment-types')],
    ['GET /providers', () => http.get('/api/v1/booking/providers')],
    ['GET /availability', () => http.get('/api/v1/booking/availability')],
  ];

  for (const [label, makeRequest] of mutatingRequests) {
    it(`navigates to /book when ${label} returns FEATURE_DISABLED (§3/§6)`, (done) => {
      (makeRequest() as ReturnType<typeof http.get>).subscribe({
        error: (err: AppHttpError) => {
          expect(err.errorCode).toBe('FEATURE_DISABLED');
          expect(router.navigateByUrl).toHaveBeenCalledWith('/book');
          done();
        },
      });

      httpMock
        .match(() => true)[0]
        .flush(
          { errorCode: 'FEATURE_DISABLED', message: 'Online booking is currently unavailable.' },
          { status: 403, statusText: 'Forbidden' }
        );
    });
  }
});
