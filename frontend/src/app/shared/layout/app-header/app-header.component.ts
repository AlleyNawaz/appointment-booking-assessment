import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatButtonModule } from '@angular/material/button';

import { CLINIC_NAME } from '../../../core/clinic-info.const';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';
import { TranslateService } from '../../../core/i18n/translate.service';

/**
 * Purely presentational site header for the patient-facing booking flow —
 * branding + top-level navigation.
 */
@Component({
  selector: 'app-header',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, MatIconModule, MatMenuModule, MatButtonModule, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app-header.component.html',
  styleUrl: './app-header.component.scss',
})
export class AppHeaderComponent {
  readonly clinicName = CLINIC_NAME;
  private readonly translateService = inject(TranslateService);

  get currentLang(): string {
    return this.translateService.currentLang();
  }

  setLanguage(lang: string) {
    this.translateService.use(lang);
  }
}
