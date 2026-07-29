import { APP_INITIALIZER, ApplicationConfig } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';

import { routes } from './app.routes';
import { httpErrorInterceptor } from './core/interceptors/http-error.interceptor';
import { requestIdInterceptor } from './core/interceptors/request-id.interceptor';
import { TranslateService } from './core/i18n/translate.service';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';

/** Loads the i18n resource file before the app renders, so no template ever flashes a raw key. */
function initializeTranslations(translateService: TranslateService): () => Promise<void> {
  return () => translateService.load();
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideHttpClient(withInterceptors([requestIdInterceptor, httpErrorInterceptor])),
    { provide: APP_INITIALIZER, useFactory: initializeTranslations, deps: [TranslateService], multi: true }, provideAnimationsAsync(),
  ],
};
