import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';

import { AsyncStateWrapperComponent } from '../../shared/components/async-state-wrapper/async-state-wrapper.component';
import { AppHttpError } from '../../core/interceptors/http-error.interceptor';
import { AdminApiService } from './admin-api.service';
import { FeatureFlag } from './admin.model';
import { StaffNavComponent } from '../staff-nav.component';

const FLAG_NAME = 'enable_online_booking';

/**
 * `/staff/admin/settings` (PRD §4/§8.17) — the one mutating action ROLE_SYSADMIN is
 * granted (§2); both ROLE_ADMIN and ROLE_SYSADMIN see this screen with write access.
 */
@Component({
  selector: 'app-settings-page',
  standalone: true,
  imports: [AsyncStateWrapperComponent, StaffNavComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './settings.page.html',
})
export class SettingsPage implements OnInit {
  private readonly adminApi = inject(AdminApiService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly flag = signal<FeatureFlag | null>(null);
  readonly toggling = signal(false);
  readonly toggleError = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.adminApi.getFeatureFlag(FLAG_NAME).subscribe({
      next: (flag) => {
        this.loading.set(false);
        this.flag.set(flag);
      },
      error: (err: AppHttpError) => {
        this.loading.set(false);
        this.error.set(err.userMessage);
      },
    });
  }

  toggle(): void {
    const current = this.flag();
    if (!current) {
      return;
    }
    this.toggling.set(true);
    this.toggleError.set(null);
    this.adminApi.updateFeatureFlag(FLAG_NAME, !current.isEnabled).subscribe({
      next: (flag) => {
        this.toggling.set(false);
        this.flag.set(flag);
      },
      error: (err: AppHttpError) => {
        this.toggling.set(false);
        this.toggleError.set(err.userMessage);
      },
    });
  }
}
