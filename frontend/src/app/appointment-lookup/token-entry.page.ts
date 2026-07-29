import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { AppHeaderComponent } from '../shared/layout/app-header/app-header.component';

@Component({
  selector: 'app-token-entry-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatInputModule,
    MatIconModule,
    TranslatePipe,
    AppHeaderComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './token-entry.page.html',
  styleUrl: './token-entry.page.scss',
})
export class TokenEntryPage {
  private readonly fb = inject(FormBuilder);
  private readonly router = inject(Router);

  readonly form = this.fb.nonNullable.group({
    token: ['', [Validators.required, Validators.pattern(/^[0-9a-fA-F-]{36}$/)]],
  });

  onSubmit(): void {
    if (this.form.valid) {
      const token = this.form.controls.token.value;
      this.router.navigate(['/appointments', token]);
    }
  }
}
