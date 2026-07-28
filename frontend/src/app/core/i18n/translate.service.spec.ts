import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { TranslateService } from './translate.service';

describe('TranslateService', () => {
  let service: TranslateService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TranslateService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads the resource file and resolves a nested key', async () => {
    const loadPromise = service.load();
    httpMock.expectOne('/assets/i18n/en-US.json').flush({ booking: { entry: { redirecting: 'Redirecting…' } } });
    await loadPromise;

    expect(service.instant('booking.entry.redirecting')).toBe('Redirecting…');
  });

  it('interpolates {param} placeholders', async () => {
    const loadPromise = service.load();
    httpMock.expectOne('/assets/i18n/en-US.json').flush({ a: { b: 'Call {clinicPhoneNumber} now' } });
    await loadPromise;

    expect(service.instant('a.b', { clinicPhoneNumber: '+1-555-0100' })).toBe('Call +1-555-0100 now');
  });

  it('falls back to the key itself when the key is not found', async () => {
    const loadPromise = service.load();
    httpMock.expectOne('/assets/i18n/en-US.json').flush({});
    await loadPromise;

    expect(service.instant('missing.key')).toBe('missing.key');
  });
});
