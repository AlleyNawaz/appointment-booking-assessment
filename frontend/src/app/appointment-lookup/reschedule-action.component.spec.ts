import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { AppointmentDetailResponse } from '../booking/models/appointment.model';
import { BookingApiService } from '../booking/services/booking-api.service';
import { AppHttpError } from '../core/interceptors/http-error.interceptor';
import { RescheduleActionComponent } from './reschedule-action.component';

const APPOINTMENT: AppointmentDetailResponse = {
  confirmationToken: 'token-1',
  providerName: 'Ada Okafor',
  appointmentTypeName: 'General Consultation',
  startDateTime: '2026-08-15T13:00:00Z',
  status: 'CONFIRMED',
  cancellationEligible: true,
  providerId: 5,
  appointmentTypeId: 2,
};

describe('RescheduleActionComponent', () => {
  let getAvailabilitySpy: jasmine.Spy;
  let createHoldSpy: jasmine.Spy;
  let rescheduleSpy: jasmine.Spy;
  let router: Router;

  beforeEach(() => {
    getAvailabilitySpy = jasmine
      .createSpy('getAvailability')
      .and.returnValue(of({ date: '2026-08-20', slots: ['2026-08-20T14:00:00Z'] }));
    createHoldSpy = jasmine
      .createSpy('createHold')
      .and.returnValue(of({ holdToken: 'hold-1', expiresAt: '2026-08-15T13:05:00Z' }));
    rescheduleSpy = jasmine.createSpy('reschedule').and.returnValue(
      of({
        confirmationToken: 'token-2',
        status: 'CONFIRMED',
        providerId: 5,
        startDateTime: '2026-08-20T14:00:00Z',
        previousConfirmationToken: 'token-1',
      })
    );

    TestBed.configureTestingModule({
      imports: [RescheduleActionComponent],
      providers: [
        provideRouter([]),
        {
          provide: BookingApiService,
          useValue: {
            getAvailability: getAvailabilitySpy,
            createHold: createHoldSpy,
            reschedule: rescheduleSpy,
          },
        },
      ],
    });
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl');
  });

  function createComponent() {
    const fixture = TestBed.createComponent(RescheduleActionComponent);
    fixture.componentRef.setInput('appointment', APPOINTMENT);
    fixture.componentRef.setInput('confirmationToken', APPOINTMENT.confirmationToken);
    fixture.detectChanges();
    return fixture;
  }

  it('loads availability for the same provider/appointment type when a date is chosen', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.start();
    component.onDateChange('2026-08-20');

    expect(getAvailabilitySpy).toHaveBeenCalledWith(5, 2, '2026-08-20');
    expect(component.slots()).toEqual(['2026-08-20T14:00:00Z']);
  });

  it('creates a hold when a slot is selected', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.start();
    component.onDateChange('2026-08-20');
    component.selectSlot('2026-08-20T14:00:00Z');

    expect(createHoldSpy).toHaveBeenCalledWith(5, 2, '2026-08-20T14:00:00Z');
    expect(component.holdToken()).toBe('hold-1');
  });

  it('submits the reschedule request and navigates to the new confirmation token on success', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.start();
    component.onDateChange('2026-08-20');
    component.selectSlot('2026-08-20T14:00:00Z');
    component.reason.set('Schedule conflict');
    component.submit();

    expect(rescheduleSpy).toHaveBeenCalledWith(
      'token-1',
      { holdToken: 'hold-1', reason: 'Schedule conflict' },
      jasmine.any(String)
    );
    expect(router.navigateByUrl).toHaveBeenCalledWith('/appointments/token-2');
  });

  it('on SLOT_HOLD_EXPIRED, clears the hold and reloads slots instead of surfacing a raw error', () => {
    rescheduleSpy.and.returnValue(
      throwError(
        () =>
          ({ errorCode: 'SLOT_HOLD_EXPIRED', userMessage: 'expired', status: 410, original: {} }) as AppHttpError
      )
    );
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.start();
    component.onDateChange('2026-08-20');
    component.selectSlot('2026-08-20T14:00:00Z');
    getAvailabilitySpy.calls.reset();
    component.submit();

    expect(component.holdToken()).toBeNull();
    expect(getAvailabilitySpy).toHaveBeenCalledWith(5, 2, '2026-08-20');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });
});
