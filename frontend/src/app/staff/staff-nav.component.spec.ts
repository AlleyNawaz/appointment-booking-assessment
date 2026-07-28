import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { StaffSessionService } from './auth/staff-session.service';
import { StaffNavComponent } from './staff-nav.component';
import { TranslateService } from '../core/i18n/translate.service';

const NAV_TRANSLATIONS = {
  staff: {
    nav: {
      appointments: 'Appointments',
      hours: 'Hours',
      timeOff: 'Time Off',
      holidays: 'Holidays',
      appointmentTypes: 'Appointment Types',
      providers: 'Providers',
      systemSettings: 'System Settings',
      auditLog: 'Audit Log',
    },
  },
};

/** Covers the Milestone 10 validation checklist's nav-visibility item (PRD §4.1). */
describe('StaffNavComponent', () => {
  async function setRole(role: 'ROLE_STAFF' | 'ROLE_PROVIDER' | 'ROLE_ADMIN' | 'ROLE_SYSADMIN') {
    TestBed.configureTestingModule({
      imports: [StaffNavComponent],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(StaffSessionService).setSession({
      username: 'test-user',
      role,
      providerId: role === 'ROLE_PROVIDER' ? 5 : null,
      sessionExpiresAt: '2026-08-01T00:00:00Z',
    });

    const loadPromise = TestBed.inject(TranslateService).load();
    TestBed.inject(HttpTestingController).expectOne('/assets/i18n/en-US.json').flush(NAV_TRANSLATIONS);
    await loadPromise;

    const fixture = TestBed.createComponent(StaffNavComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('hides the admin nav items entirely for ROLE_STAFF', async () => {
    const fixture = await setRole('ROLE_STAFF');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('System Settings');
  });

  it('hides the admin nav items entirely for ROLE_PROVIDER', async () => {
    const fixture = await setRole('ROLE_PROVIDER');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('System Settings');
  });

  it('shows the admin nav items for ROLE_ADMIN', async () => {
    const fixture = await setRole('ROLE_ADMIN');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('System Settings');
  });

  it('shows the admin nav items (read-only server-side) for ROLE_SYSADMIN', async () => {
    const fixture = await setRole('ROLE_SYSADMIN');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('System Settings');
  });

  it('hides the audit log nav item for ROLE_STAFF', async () => {
    const fixture = await setRole('ROLE_STAFF');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Audit Log');
  });

  it('hides the audit log nav item for ROLE_PROVIDER', async () => {
    const fixture = await setRole('ROLE_PROVIDER');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Audit Log');
  });

  it('hides the audit log nav item for ROLE_ADMIN', async () => {
    const fixture = await setRole('ROLE_ADMIN');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Audit Log');
  });

  it('shows the audit log nav item for ROLE_SYSADMIN', async () => {
    const fixture = await setRole('ROLE_SYSADMIN');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Audit Log');
  });
});
