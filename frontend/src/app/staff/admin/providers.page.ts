import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';

import { AsyncStateWrapperComponent } from '../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHttpError } from '../../core/interceptors/http-error.interceptor';
import { StaffSessionService } from '../auth/staff-session.service';
import { AdminApiService } from './admin-api.service';
import { ProviderAdmin } from './admin.model';
import { StaffNavComponent } from '../staff-nav.component';

/** `/staff/admin/providers` (PRD §4/§8.13). */
@Component({
  selector: 'app-providers-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent, StaffNavComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './providers.page.html',
})
export class ProvidersPage implements OnInit {
  private readonly adminApi = inject(AdminApiService);
  private readonly staffSession = inject(StaffSessionService);

  /** §4.1 nav matrix: Admin → Providers write access is ROLE_ADMIN only. */
  readonly canWrite = computed(() => this.staffSession.role() === 'ROLE_ADMIN');

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly providers = signal<ProviderAdmin[] | null>(null);

  readonly firstName = signal('');
  readonly lastName = signal('');
  readonly specialty = signal('');
  readonly email = signal('');
  readonly timezone = signal('America/New_York');
  readonly formError = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.adminApi.listProviders().subscribe({
      next: (providers) => {
        this.loading.set(false);
        this.providers.set(providers);
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
      .createProvider({
        firstName: this.firstName(),
        lastName: this.lastName(),
        specialty: this.specialty(),
        email: this.email(),
        timezone: this.timezone(),
        isActive: true,
      })
      .subscribe({
        next: () => {
          this.firstName.set('');
          this.lastName.set('');
          this.email.set('');
          this.load();
        },
        error: (err: AppHttpError) => this.formError.set(err.userMessage),
      });
  }

  softDelete(provider: ProviderAdmin): void {
    this.adminApi.softDeleteProvider(provider.id).subscribe({
      next: () => this.load(),
      error: (err: AppHttpError) => this.error.set(err.userMessage),
    });
  }
}
