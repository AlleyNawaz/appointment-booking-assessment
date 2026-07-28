import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';

import { AsyncStateWrapperComponent } from '../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHttpError } from '../../core/interceptors/http-error.interceptor';
import { StaffSessionService } from '../auth/staff-session.service';
import { AvailabilityApiService } from './availability-api.service';
import { Holiday } from './availability.model';
import { StaffNavComponent } from '../staff-nav.component';
import { TranslatePipe } from '../../core/i18n/translate.pipe';

/** `/staff/availability/holidays` (PRD §4/§8.16) — clinic-wide holiday calendar. */
@Component({
  selector: 'app-holidays-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent, StaffNavComponent, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './holidays.page.html',
})
export class HolidaysPage implements OnInit {
  private readonly availabilityApi = inject(AvailabilityApiService);
  private readonly staffSession = inject(StaffSessionService);

  /** §4.1 nav matrix: Availability → Holidays write access is ROLE_ADMIN only. */
  readonly canWrite = computed(() => this.staffSession.role() === 'ROLE_ADMIN');

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly holidays = signal<Holiday[] | null>(null);

  readonly holidayDate = signal('');
  readonly name = signal('');
  readonly isRecurringAnnually = signal(false);
  readonly formError = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.availabilityApi.listHolidays().subscribe({
      next: (holidays) => {
        this.loading.set(false);
        this.holidays.set(holidays);
      },
      error: (err: AppHttpError) => {
        this.loading.set(false);
        this.error.set(err.userMessage);
      },
    });
  }

  create(): void {
    this.formError.set(null);
    this.availabilityApi
      .createHoliday({
        holidayDate: this.holidayDate(),
        name: this.name(),
        isRecurringAnnually: this.isRecurringAnnually(),
      })
      .subscribe({
        next: () => {
          this.name.set('');
          this.load();
        },
        error: (err: AppHttpError) => this.formError.set(err.userMessage),
      });
  }

  delete(id: number): void {
    this.availabilityApi.deleteHoliday(id).subscribe({
      next: () => this.load(),
      error: (err: AppHttpError) => this.error.set(err.userMessage),
    });
  }
}
