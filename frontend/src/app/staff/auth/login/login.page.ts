import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AppHttpError } from '../../../core/interceptors/http-error.interceptor';
import { StaffAuthService } from '../staff-auth.service';
import { StaffSessionService } from '../staff-session.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/** `/staff/login` (PRD §4/§8.20) — the only staff-console route this milestone implements. */
@Component({
  selector: 'app-staff-login-page',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.page.html',
})
export class LoginPage {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly staffAuth = inject(StaffAuthService);
  private readonly staffSession = inject(StaffSessionService);
  private readonly router = inject(Router);

  readonly loginForm = this.fb.group({
    username: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  submit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.errorMessage.set(null);
    this.submitting.set(true);
    const { username, password } = this.loginForm.getRawValue();

    this.staffAuth.login(username, password).subscribe({
      next: (session) => {
        this.staffSession.setSession(session);
        this.router.navigateByUrl('/staff/appointments');
      },
      error: (error: AppHttpError) => {
        this.submitting.set(false);
        this.errorMessage.set(error.userMessage);
      },
    });
  }
}
