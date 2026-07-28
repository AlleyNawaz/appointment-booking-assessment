import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { StaffSessionService } from './auth/staff-session.service';
import { StaffNavComponent } from './staff-nav.component';

/** Covers the Milestone 10 validation checklist's nav-visibility item (PRD §4.1). */
describe('StaffNavComponent', () => {
  function setRole(role: 'ROLE_STAFF' | 'ROLE_PROVIDER' | 'ROLE_ADMIN' | 'ROLE_SYSADMIN') {
    TestBed.configureTestingModule({ imports: [StaffNavComponent], providers: [provideRouter([])] });
    TestBed.inject(StaffSessionService).setSession({
      username: 'test-user',
      role,
      providerId: role === 'ROLE_PROVIDER' ? 5 : null,
      sessionExpiresAt: '2026-08-01T00:00:00Z',
    });
    const fixture = TestBed.createComponent(StaffNavComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('hides the admin nav items entirely for ROLE_STAFF', () => {
    const fixture = setRole('ROLE_STAFF');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('System Settings');
  });

  it('hides the admin nav items entirely for ROLE_PROVIDER', () => {
    const fixture = setRole('ROLE_PROVIDER');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('System Settings');
  });

  it('shows the admin nav items for ROLE_ADMIN', () => {
    const fixture = setRole('ROLE_ADMIN');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('System Settings');
  });

  it('shows the admin nav items (read-only server-side) for ROLE_SYSADMIN', () => {
    const fixture = setRole('ROLE_SYSADMIN');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('System Settings');
  });

  it('hides the audit log nav item for ROLE_STAFF', () => {
    const fixture = setRole('ROLE_STAFF');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Audit Log');
  });

  it('hides the audit log nav item for ROLE_PROVIDER', () => {
    const fixture = setRole('ROLE_PROVIDER');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Audit Log');
  });

  it('hides the audit log nav item for ROLE_ADMIN', () => {
    const fixture = setRole('ROLE_ADMIN');
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Audit Log');
  });

  it('shows the audit log nav item for ROLE_SYSADMIN', () => {
    const fixture = setRole('ROLE_SYSADMIN');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Audit Log');
  });
});
