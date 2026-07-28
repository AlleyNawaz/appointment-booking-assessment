import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AuditLogApiService } from './audit-log-api.service';
import { AuditLogPage } from './audit-log.page';

describe('AuditLogPage', () => {
  let listSpy: jasmine.Spy;

  beforeEach(() => {
    listSpy = jasmine
      .createSpy('list')
      .and.returnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
    TestBed.configureTestingModule({
      imports: [AuditLogPage],
      providers: [provideRouter([]), { provide: AuditLogApiService, useValue: { list: listSpy } }],
    });
  });

  function createComponent() {
    const fixture = TestBed.createComponent(AuditLogPage);
    fixture.detectChanges();
    return fixture;
  }

  it('combines appointmentId, from, and to filters into a single list call (§8.18)', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.onAppointmentIdFilterChange('118');
    component.onFromFilterChange('2026-08-01');
    component.onToFilterChange('2026-08-31');

    const lastCall = listSpy.calls.mostRecent().args[0];
    expect(lastCall).toEqual(
      jasmine.objectContaining({
        appointmentId: 118,
        from: '2026-08-01T00:00:00Z',
        to: '2026-09-01T00:00:00Z',
      })
    );
  });

  it('resets to page 0 whenever a filter changes', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.goToPage(2);
    component.onFromFilterChange('2026-08-01');

    expect(listSpy.calls.mostRecent().args[0].page).toBe(0);
  });

  it('preserves pagination when no filter changes', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.goToPage(3);

    expect(listSpy.calls.mostRecent().args[0].page).toBe(3);
  });
});
