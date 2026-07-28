import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { AsyncStateWrapperComponent } from '../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHttpError } from '../../core/interceptors/http-error.interceptor';
import { formatClinicDate, formatClinicTime } from '../../core/clinic-info.const';
import { AppointmentApiService } from './appointment-api.service';
import { AppointmentPageResponse, StaffAppointment } from './staff-appointment.model';
import { StaffNavComponent } from '../staff-nav.component';

/** `/staff/appointments` (PRD §4.1/§8.9) — list/search with pagination and filters. */
@Component({
  selector: 'app-appointment-list-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent, StaffNavComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './appointment-list.page.html',
})
export class AppointmentListPage implements OnInit {
  private readonly appointmentApi = inject(AppointmentApiService);
  private readonly router = inject(Router);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly page = signal<AppointmentPageResponse | null>(null);
  readonly statusFilter = signal('');
  readonly providerIdFilter = signal('');
  readonly fromFilter = signal('');
  readonly toFilter = signal('');

  ngOnInit(): void {
    this.load();
  }

  formatDate(iso: string): string {
    return formatClinicDate(iso);
  }

  formatTime(iso: string): string {
    return formatClinicTime(iso);
  }

  onStatusFilterChange(status: string): void {
    this.statusFilter.set(status);
    this.load(0);
  }

  onProviderIdFilterChange(providerId: string): void {
    this.providerIdFilter.set(providerId);
    this.load(0);
  }

  onFromFilterChange(from: string): void {
    this.fromFilter.set(from);
    this.load(0);
  }

  onToFilterChange(to: string): void {
    this.toFilter.set(to);
    this.load(0);
  }

  goToPage(page: number): void {
    this.load(page);
  }

  openDetail(appointment: StaffAppointment): void {
    this.router.navigate(['/staff/appointments', appointment.id], { state: { appointment } });
  }

  private load(page = 0): void {
    this.loading.set(true);
    this.error.set(null);
    const status = (this.statusFilter() || undefined) as StaffAppointment['status'] | undefined;
    const providerId = this.providerIdFilter() ? Number(this.providerIdFilter()) : undefined;
    const from = this.fromFilter() || undefined;
    const to = this.toFilter() || undefined;
    this.appointmentApi.list({ status, providerId, from, to, page }).subscribe({
      next: (result) => {
        this.loading.set(false);
        this.page.set(result);
      },
      error: (err: AppHttpError) => {
        this.loading.set(false);
        this.error.set(err.userMessage);
      },
    });
  }
}
