import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';

import { AsyncStateWrapperComponent } from '../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHttpError } from '../../core/interceptors/http-error.interceptor';
import { StaffSessionService } from '../auth/staff-session.service';
import { AdminApiService } from './admin-api.service';
import { AppointmentTypeAdmin } from './admin.model';
import { StaffNavComponent } from '../staff-nav.component';

/** `/staff/admin/appointment-types` (PRD §4/§8.12). */
@Component({
  selector: 'app-appointment-types-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent, StaffNavComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './appointment-types.page.html',
})
export class AppointmentTypesPage implements OnInit {
  private readonly adminApi = inject(AdminApiService);
  private readonly staffSession = inject(StaffSessionService);

  /** §4.1 nav matrix: Admin → Appointment Types write access is ROLE_ADMIN only. */
  readonly canWrite = computed(() => this.staffSession.role() === 'ROLE_ADMIN');

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly types = signal<AppointmentTypeAdmin[] | null>(null);

  readonly code = signal('');
  readonly displayName = signal('');
  readonly durationMinutes = signal(30);
  readonly bufferMinutes = signal(0);
  readonly requiresApproval = signal(false);
  readonly formError = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.adminApi.listAppointmentTypes().subscribe({
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

  create(): void {
    this.formError.set(null);
    this.adminApi
      .createAppointmentType({
        code: this.code(),
        displayName: this.displayName(),
        durationMinutes: this.durationMinutes(),
        bufferMinutes: this.bufferMinutes(),
        requiresApproval: this.requiresApproval(),
        isActive: true,
      })
      .subscribe({
        next: () => {
          this.code.set('');
          this.displayName.set('');
          this.load();
        },
        error: (err: AppHttpError) => this.formError.set(err.userMessage),
      });
  }

  deactivate(type: AppointmentTypeAdmin): void {
    this.adminApi.deactivateAppointmentType(type.id).subscribe({
      next: () => this.load(),
      error: (err: AppHttpError) => this.error.set(err.userMessage),
    });
  }
}
