import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

type TranslationNode = string | { [key: string]: TranslationNode };

/**
 * PRD §14 Internationalization readiness: loads the single {@code en-US.json}
 * resource file (the i18n readiness artifact) once at app startup and resolves
 * dot-separated keys against it. Only {@code en-US} ships (§20), so there is
 * no locale-switching here — this is the minimal mechanism that makes the
 * resource file an actual runtime source of UI strings rather than an inert
 * catalog, per the Milestone 13 checklist ("every string... sourced from an
 * i18n resource file, none hardcoded").
 */
@Injectable({ providedIn: 'root' })
export class TranslateService {
  private translations: TranslationNode = {};

  constructor(private readonly http: HttpClient) {}

  async load(): Promise<void> {
    this.translations = await firstValueFrom(this.http.get<TranslationNode>('/assets/i18n/en-US.json'));
  }

  /** Resolves a dot-separated key (e.g. {@code "booking.entry.redirecting"}) and interpolates {@code {param}} placeholders. */
  instant(key: string, params?: Record<string, string>): string {
    const value = this.resolve(key);
    if (typeof value !== 'string') {
      return key;
    }
    if (!params) {
      return value;
    }
    return Object.entries(params).reduce(
      (result, [paramKey, paramValue]) => result.replaceAll(`{${paramKey}}`, paramValue),
      value
    );
  }

  private resolve(key: string): TranslationNode | undefined {
    return key.split('.').reduce<TranslationNode | undefined>((node, segment) => {
      if (node && typeof node === 'object' && segment in node) {
        return node[segment];
      }
      return undefined;
    }, this.translations);
  }
}
