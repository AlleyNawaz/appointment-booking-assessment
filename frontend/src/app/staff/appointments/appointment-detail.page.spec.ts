import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { StaffSessionService } from '../auth/staff-session.service';
import { AppointmentApiService } from './appointment-api.service';
import { AppointmentDetailPage } from './appointment-detail.page';
import { StaffAppointment } from './staff-appointment.model';

describe('AppointmentDetailPage', () => {
  const pendingAppointment: StaffAppointment = {
    id: 1,
    confirmationToken: 'token-1',
    providerId: 5,
    appointmentTypeId: 2,
    patientFullName: 'Jordan Rivera',
    patientEmail: 'jordan@example.com',
    patientPhone: '+14155551234',
    notes: null,
    startDateTime: '2026-08-15T13:00:00Z',
    endDateTime: '2026-08-15T13:30:00Z',
    status: 'PENDING',
    version: 0,
    createdAt: '2026-08-01T00:00:00Z',
  };

  let apiSpies: { approve: jasmine.Spy; reject: jasmine.Spy; complete: jasmine.Spy };

  function createComponent(role: 'ROLE_STAFF' | 'ROLE_PROVIDER' | 'ROLE_ADMIN' | 'ROLE_SYSADMIN') {
    apiSpies = {
      approve: jasmine.createSpy('approve').and.returnValue(of({ ...pendingAppointment, status: 'CONFIRMED' })),
      reject: jasmine.createSpy('reject').and.returnValue(of({ ...pendingAppointment, status: 'REJECTED' })),
      complete: jasmine.createSpy('complete').and.returnValue(of({ ...pendingAppointment, status: 'COMPLETED' })),
    };
    TestBed.configureTestingModule({
      imports: [AppointmentDetailPage],
      providers: [provideRouter([]), { provide: AppointmentApiService, useValue: apiSpies }],
    });
    TestBed.inject(StaffSessionService).setSession({
      username: 'test-user',
      role,
      providerId: role === 'ROLE_PROVIDER' ? 5 : null,
      sessionExpiresAt: '2026-08-01T00:00:00Z',
    });
    const fixture = TestBed.createComponent(AppointmentDetailPage);
    fixture.detectChanges();
    // ngOnInit finds no router-navigation state in this test and sets an error;
    // override both signals directly to simulate arriving from the list page.
    fixture.componentInstance.error.set(null);
    fixture.componentInstance.appointment.set(pendingAppointment);
    fixture.detectChanges();
    return fixture;
  }

  it('hides the action controls for ROLE_SYSADMIN (§4.1 read-only)', () => {
    const fixture = createComponent('ROLE_SYSADMIN');
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.actions')).toBeNull();
  });

  it('does not call the API when an action method is invoked directly under ROLE_SYSADMIN', () => {
    const fixture = createComponent('ROLE_SYSADMIN');

    fixture.componentInstance.approve();
    fixture.componentInstance.complete();

    expect(apiSpies.approve).not.toHaveBeenCalled();
    expect(apiSpies.complete).not.toHaveBeenCalled();
  });

  it('shows the action controls and still calls the API for ROLE_STAFF', () => {
    const fixture = createComponent('ROLE_STAFF');
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.actions')).toBeTruthy();

    fixture.componentInstance.approve();
    expect(apiSpies.approve).toHaveBeenCalledWith(1, 0);
  });

  it('shows the action controls and still calls the API for ROLE_ADMIN', () => {
    const fixture = createComponent('ROLE_ADMIN');
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.actions')).toBeTruthy();

    fixture.componentInstance.approve();
    expect(apiSpies.approve).toHaveBeenCalledWith(1, 0);
  });

  it('shows the action controls and still calls the API for ROLE_PROVIDER', () => {
    const fixture = createComponent('ROLE_PROVIDER');
    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.actions')).toBeTruthy();

    fixture.componentInstance.approve();
    expect(apiSpies.approve).toHaveBeenCalledWith(1, 0);
  });
});
