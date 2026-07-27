import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { BookingApiService } from '../../booking/services/booking-api.service';
import { featureFlagGuard } from './feature-flag.guard';

describe('featureFlagGuard', () => {
  function runGuard() {
    return TestBed.runInInjectionContext(() => featureFlagGuard({} as any, {} as any));
  }

  it('allows activation when the flag is enabled', (done) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: BookingApiService, useValue: { getConfig: () => of({ enabled: true }) } },
      ],
    });

    const result = runGuard() as any;
    result.subscribe((value: unknown) => {
      expect(value).toBeTrue();
      done();
    });
  });

  it('redirects to /book when the flag is disabled', (done) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: BookingApiService, useValue: { getConfig: () => of({ enabled: false }) } },
      ],
    });
    const router = TestBed.inject(Router);
    spyOn(router, 'parseUrl').and.callThrough();

    const result = runGuard() as any;
    result.subscribe((value: any) => {
      expect(router.parseUrl).toHaveBeenCalledWith('/book');
      expect(value.toString()).toBe('/book');
      done();
    });
  });

  it('redirects to /book when the config call fails', (done) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: BookingApiService, useValue: { getConfig: () => throwError(() => new Error('network')) } },
      ],
    });

    const result = runGuard() as any;
    result.subscribe((value: any) => {
      expect(value.toString()).toContain('/book');
      done();
    });
  });
});
