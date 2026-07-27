import { TestBed } from '@angular/core/testing';

import { AppointmentType } from '../models/appointment-type.model';
import { Provider } from '../models/provider.model';
import { BookingStateService } from './booking-state.service';

const TYPE: AppointmentType = {
  id: 2,
  code: 'GENERAL_CONSULT',
  displayName: 'General Consultation',
  durationMinutes: 30,
  requiresApproval: false,
};

const PROVIDER: Provider = { id: 5, firstName: 'Ada', lastName: 'Okafor', specialty: 'Family Medicine' };

describe('BookingStateService', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
  });

  it('resets downstream selections when a new appointment type is chosen', () => {
    const service = TestBed.inject(BookingStateService);
    service.setAppointmentType(TYPE);
    service.setProvider(PROVIDER);
    service.setSelectedDate('2026-08-15');

    service.setAppointmentType(TYPE);

    expect(service.provider()).toBeNull();
    expect(service.selectedDate()).toBeNull();
  });

  it('reports no valid hold when none has been set', () => {
    const service = TestBed.inject(BookingStateService);
    expect(service.hasValidHold()).toBeFalse();
  });

  it('reports a valid hold only while unexpired', () => {
    const service = TestBed.inject(BookingStateService);
    service.setHold({ holdToken: 'abc', expiresAt: new Date(Date.now() + 60_000).toISOString() });
    expect(service.hasValidHold()).toBeTrue();

    service.setHold({ holdToken: 'abc', expiresAt: new Date(Date.now() - 1000).toISOString() });
    expect(service.hasValidHold()).toBeFalse();
  });

  it('restores state from sessionStorage when the hold is still valid (§19 #5)', () => {
    const first = TestBed.inject(BookingStateService);
    first.setAppointmentType(TYPE);
    first.setProvider(PROVIDER);
    first.setHold({ holdToken: 'abc', expiresAt: new Date(Date.now() + 60_000).toISOString() });
    first.setSelectedSlot('2026-08-15T13:00:00Z');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    const restored = TestBed.inject(BookingStateService);

    expect(restored.appointmentType()).toEqual(TYPE);
    expect(restored.provider()).toEqual(PROVIDER);
    expect(restored.hasValidHold()).toBeTrue();
    expect(restored.selectedSlot()).toBe('2026-08-15T13:00:00Z');
  });

  it('drops an expired hold on restore but keeps contact info (§3 hold-timeout flow)', () => {
    const first = TestBed.inject(BookingStateService);
    first.setAppointmentType(TYPE);
    first.setProvider(PROVIDER);
    first.setHold({ holdToken: 'abc', expiresAt: new Date(Date.now() - 1000).toISOString() });
    first.setContact({ fullName: 'Jordan Rivera', email: 'jordan@example.com', phone: '+14155551234', notes: '' });

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    const restored = TestBed.inject(BookingStateService);

    expect(restored.hasValidHold()).toBeFalse();
    expect(restored.hold()).toBeNull();
    expect(restored.contact()?.fullName).toBe('Jordan Rivera');
  });

  it('reuses the same idempotency key across calls until reset', () => {
    const service = TestBed.inject(BookingStateService);
    const key1 = service.getOrCreateIdempotencyKey();
    const key2 = service.getOrCreateIdempotencyKey();
    expect(key1).toBe(key2);

    service.reset();
    const key3 = service.getOrCreateIdempotencyKey();
    expect(key3).not.toBe(key1);
  });

  it('surfaces and clears the flash message set on hold expiry exactly once', () => {
    const service = TestBed.inject(BookingStateService);
    service.clearExpiredHold('That time slot was only held for 5 minutes and has been released — please pick a time again.');

    expect(service.consumeFlashMessage()).toContain('released');
    expect(service.consumeFlashMessage()).toBeNull();
  });
});
