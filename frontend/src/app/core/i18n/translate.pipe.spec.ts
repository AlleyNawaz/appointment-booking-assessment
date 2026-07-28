import { TestBed } from '@angular/core/testing';

import { TranslatePipe } from './translate.pipe';
import { TranslateService } from './translate.service';

describe('TranslatePipe', () => {
  it('delegates to TranslateService.instant with the key and params', () => {
    const instantSpy = jasmine.createSpy('instant').and.returnValue('Redirecting…');
    TestBed.configureTestingModule({
      providers: [{ provide: TranslateService, useValue: { instant: instantSpy } }],
    });

    const pipe = TestBed.runInInjectionContext(() => new TranslatePipe());
    const result = pipe.transform('booking.entry.redirecting', { foo: 'bar' });

    expect(result).toBe('Redirecting…');
    expect(instantSpy).toHaveBeenCalledWith('booking.entry.redirecting', { foo: 'bar' });
  });
});
