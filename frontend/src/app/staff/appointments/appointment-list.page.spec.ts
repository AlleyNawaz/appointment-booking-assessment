import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { AppointmentApiService } from './appointment-api.service';
import { AppointmentListPage } from './appointment-list.page';

describe('AppointmentListPage', () => {
  let listSpy: jasmine.Spy;

  beforeEach(() => {
    listSpy = jasmine
      .createSpy('list')
      .and.returnValue(of({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }));
    TestBed.configureTestingModule({
      imports: [AppointmentListPage],
      providers: [provideRouter([]), { provide: AppointmentApiService, useValue: { list: listSpy } }],
    });
  });

  function createComponent() {
    const fixture = TestBed.createComponent(AppointmentListPage);
    fixture.detectChanges();
    return fixture;
  }

  it('combines status, provider, and date filters into a single list call (§8.9)', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.onStatusFilterChange('PENDING');
    component.onProviderIdFilterChange('5');
    component.onFromFilterChange('2026-08-01');
    component.onToFilterChange('2026-08-31');

    const lastCall = listSpy.calls.mostRecent().args[0];
    expect(lastCall).toEqual(
      jasmine.objectContaining({
        status: 'PENDING',
        providerId: 5,
        from: '2026-08-01',
        to: '2026-08-31',
      })
    );
  });

  it('resets to page 0 whenever a filter changes', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.goToPage(2);
    component.onStatusFilterChange('CONFIRMED');

    expect(listSpy.calls.mostRecent().args[0].page).toBe(0);
  });

  it('preserves pagination when no filter changes (§19 #42/#43 groundwork)', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance;

    component.goToPage(3);

    expect(listSpy.calls.mostRecent().args[0].page).toBe(3);
  });
});
