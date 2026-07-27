import { HttpClient, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { BookingApiService } from './booking-api.service';

describe('BookingApiService', () => {
  let service: BookingApiService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BookingApiService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests providers filtered by appointmentTypeId (§8.3)', () => {
    service.getProviders(2).subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/booking/providers');
    expect(req.request.params.get('appointmentTypeId')).toBe('2');
    req.flush([]);
  });

  it('requests availability with providerId/appointmentTypeId/date (§8.4)', () => {
    service.getAvailability(5, 2, '2026-08-15').subscribe();
    const req = httpMock.expectOne((r) => r.url === '/api/v1/booking/availability');
    expect(req.request.params.get('providerId')).toBe('5');
    expect(req.request.params.get('appointmentTypeId')).toBe('2');
    expect(req.request.params.get('date')).toBe('2026-08-15');
    req.flush({ date: '2026-08-15', slots: [] });
  });

  it('sends the Idempotency-Key header on appointment creation (§8.6)', () => {
    service
      .createAppointment(
        { holdToken: 'hold-1', patientFullName: 'Jordan Rivera', patientEmail: 'j@example.com', patientPhone: '+14155551234' },
        'key-123'
      )
      .subscribe();

    const req = httpMock.expectOne('/api/v1/booking/appointments');
    expect(req.request.headers.get('Idempotency-Key')).toBe('key-123');
    expect(req.request.body.holdToken).toBe('hold-1');
    req.flush({ confirmationToken: 'c1', status: 'CONFIRMED', providerId: 5, startDateTime: '2026-08-15T13:00:00Z' });
  });

  it('sends a DELETE with an optional reason body (§8.8)', () => {
    service.cancelAppointment('token-1', 'Schedule conflict').subscribe();
    const req = httpMock.expectOne('/api/v1/booking/appointments/token-1');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ reason: 'Schedule conflict' });
    req.flush({
      confirmationToken: 'token-1',
      providerName: 'Ada Okafor',
      appointmentTypeName: 'General Consultation',
      startDateTime: '2026-08-15T13:00:00Z',
      status: 'CANCELLED',
      cancellationEligible: false,
    });
  });
});
