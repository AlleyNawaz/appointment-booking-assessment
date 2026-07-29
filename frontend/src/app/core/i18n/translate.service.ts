import { HttpClient } from '@angular/common/http';
import { Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

type TranslationNode = string | { [key: string]: TranslationNode };

@Injectable({ providedIn: 'root' })
export class TranslateService {
  private translations: TranslationNode = {};
  readonly currentLang = signal<string>('en-US');

  constructor(private readonly http: HttpClient) {}

  async load(): Promise<void> {
    await this.use('en-US');
  }

  async use(lang: string): Promise<void> {
    const data = await firstValueFrom(this.http.get<TranslationNode>(`/assets/i18n/${lang}.json`));
    this.translations = data;
    this.currentLang.set(lang);
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
