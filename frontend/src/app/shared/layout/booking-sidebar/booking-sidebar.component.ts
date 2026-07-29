import { ChangeDetectionStrategy, Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';

import { CLINIC_HOURS_DISPLAY, CLINIC_PHONE_NUMBER } from '../../../core/clinic-info.const';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/**
 * Purely presentational "what happens next" + "need help" panel shown
 * alongside the booking wizard's main content. Static, informational only —
 * no state, no API calls.
 */
@Component({
  selector: 'app-booking-sidebar',
  standalone: true,
  imports: [MatCardModule, MatDividerModule, MatIconModule, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './booking-sidebar.component.html',
  styleUrl: './booking-sidebar.component.scss',
})
export class BookingSidebarComponent {
  readonly clinicPhoneNumber = CLINIC_PHONE_NUMBER;
  readonly clinicHoursDisplay = CLINIC_HOURS_DISPLAY;
}
