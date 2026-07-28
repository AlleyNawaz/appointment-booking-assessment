import { Pipe, PipeTransform, inject } from '@angular/core';

import { TranslateService } from './translate.service';

/** Template-facing wrapper around {@link TranslateService.instant} — {@code {{ 'a.b.c' | translate }}}. */
@Pipe({ name: 'translate', standalone: true })
export class TranslatePipe implements PipeTransform {
  private readonly translateService = inject(TranslateService);

  transform(key: string, params?: Record<string, string>): string {
    return this.translateService.instant(key, params);
  }
}
