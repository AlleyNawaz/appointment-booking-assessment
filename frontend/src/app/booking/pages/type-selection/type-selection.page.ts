import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';

import { AsyncStateWrapperComponent } from '../../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHeaderComponent } from '../../../shared/layout/app-header/app-header.component';
import { BookingStepperComponent } from '../../../shared/layout/booking-stepper/booking-stepper.component';
import { BookingSidebarComponent } from '../../../shared/layout/booking-sidebar/booking-sidebar.component';
import { BookingInfoBannerComponent } from '../../../shared/layout/booking-info-banner/booking-info-banner.component';
import { AppointmentType } from '../../models/appointment-type.model';
import { BookingApiService } from '../../services/booking-api.service';
import { BookingStateService } from '../../state/booking-state.service';
import { AppHttpError } from '../../../core/interceptors/http-error.interceptor';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/** Presentational-only icon/description lookup by appointment-type code — no business meaning. */
const TYPE_DISPLAY: Record<string, { icon: string; descriptionKey: string }> = {
  GENERAL_CONSULT: { icon: 'healing', descriptionKey: 'booking.typeSelection.descriptionGeneral' },
  NEW_PATIENT: { icon: 'assignment_ind', descriptionKey: 'booking.typeSelection.descriptionNewPatient' },
  FOLLOW_UP: { icon: 'event_repeat', descriptionKey: 'booking.typeSelection.descriptionFollowUp' },
  SPECIALIST_CONSULT: { icon: 'medical_services', descriptionKey: 'booking.typeSelection.descriptionSpecialist' },
};
const DEFAULT_TYPE_DISPLAY = { icon: 'medical_services', descriptionKey: 'booking.typeSelection.descriptionDefault' };

/** `/book/type` (PRD §4/§8.2) — list active appointment types. */
@Component({
  selector: 'app-type-selection-page',
  standalone: true,
  imports: [
    AsyncStateWrapperComponent,
    AppHeaderComponent,
    BookingStepperComponent,
    BookingSidebarComponent,
    BookingInfoBannerComponent,
    MatChipsModule,
    MatIconModule,
    TranslatePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './type-selection.page.html',
  styleUrl: './type-selection.page.scss',
})
export class TypeSelectionPage implements OnInit {
  private readonly bookingApi = inject(BookingApiService);
  private readonly bookingState = inject(BookingStateService);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly types = signal<AppointmentType[]>([]);
  readonly selectedTypeId = signal<number | null>(null);

  ngOnInit(): void {
    this.bookingApi.getAppointmentTypes().subscribe({
      next: (types) => {
        this.loading.set(false);
        this.types.set(types);
      },
      error: (err: AppHttpError) => {
        this.loading.set(false);
        this.error.set(err.userMessage);
      },
    });
  }

  /** Presentational only — which icon/description to render for a given type. */
  displayFor(type: AppointmentType): { icon: string; descriptionKey: string } {
    return TYPE_DISPLAY[type.code] ?? DEFAULT_TYPE_DISPLAY;
  }

  select(type: AppointmentType): void {
    this.selectedTypeId.set(type.id);
    this.bookingState.setAppointmentType(type);
    this.router.navigateByUrl('/book/provider');
  }
}
