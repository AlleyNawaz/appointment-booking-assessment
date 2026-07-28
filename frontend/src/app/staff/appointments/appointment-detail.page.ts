import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';

import { AsyncStateWrapperComponent } from '../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHttpError } from '../../core/interceptors/http-error.interceptor';
import { formatClinicDate, formatClinicTime } from '../../core/clinic-info.const';
import { StaffSessionService } from '../auth/staff-session.service';
import { AppointmentApiService } from './appointment-api.service';
import { StaffAppointment } from './staff-appointment.model';

/**
 * `/staff/appointments/:id` (PRD §4.1/§8.10) — approve/reject/complete a
 * single appointment. PRD §8.9/§8.10 defines no single-appointment GET
 * endpoint, so this page is only reachable via router navigation state set
 * by the list page (§8.9's list response already has every field this page
 * needs) — a direct URL visit without that state shows a message to return
 * to the list rather than inventing a new backend endpoint for it.
 *
 * <p>§4.1's role-visibility matrix marks `ROLE_SYSADMIN` read-only (👁) on
 * this screen — the server already rejects its attempts (§8.10 explicitly
 * excludes it), so hiding the buttons here is UX only, avoiding the
 * "confusing dead-end click" the matrix note calls out, never the actual
 * access control.
 */
@Component({
  selector: 'app-appointment-detail-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './appointment-detail.page.html',
})
export class AppointmentDetailPage implements OnInit {
  private readonly router = inject(Router);
  private readonly appointmentApi = inject(AppointmentApiService);
  private readonly staffSession = inject(StaffSessionService);

  readonly canAct = computed(() => this.staffSession.role() !== 'ROLE_SYSADMIN');

  readonly error = signal<string | null>(null);
  readonly appointment = signal<StaffAppointment | null>(null);

  readonly rejectReason = signal('');
  readonly actionInProgress = signal(false);
  readonly actionError = signal<string | null>(null);

  ngOnInit(): void {
    const state = (history.state ?? {}) as { appointment?: StaffAppointment };
    if (!state.appointment) {
      this.error.set('Open this appointment from the appointment list.');
      return;
    }
    this.appointment.set(state.appointment);
  }

  formatDate(iso: string): string {
    return formatClinicDate(iso);
  }

  formatTime(iso: string): string {
    return formatClinicTime(iso);
  }

  approve(): void {
    const current = this.appointment();
    if (!current || !this.canAct()) {
      return;
    }
    this.runAction(this.appointmentApi.approve(current.id, current.version));
  }

  reject(): void {
    const current = this.appointment();
    if (!current || !this.canAct() || !this.rejectReason().trim()) {
      return;
    }
    this.runAction(this.appointmentApi.reject(current.id, current.version, this.rejectReason()));
  }

  complete(): void {
    const current = this.appointment();
    if (!current || !this.canAct()) {
      return;
    }
    this.runAction(this.appointmentApi.complete(current.id, current.version));
  }

  back(): void {
    this.router.navigateByUrl('/staff/appointments');
  }

  private runAction(action: Observable<StaffAppointment>): void {
    this.actionInProgress.set(true);
    this.actionError.set(null);
    action.subscribe({
      next: (updated) => {
        this.actionInProgress.set(false);
        this.appointment.set(updated);
        this.rejectReason.set('');
      },
      error: (err: AppHttpError) => {
        this.actionInProgress.set(false);
        this.actionError.set(err.userMessage);
      },
    });
  }
}
