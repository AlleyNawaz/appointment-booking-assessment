import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/** Purely presentational, static informational banner — no logic. */
@Component({
  selector: 'app-booking-info-banner',
  standalone: true,
  imports: [MatIconModule, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './booking-info-banner.component.html',
  styleUrl: './booking-info-banner.component.scss',
})
export class BookingInfoBannerComponent {}
