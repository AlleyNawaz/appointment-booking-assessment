import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** One of the three renderable states every API-backed page must handle (PRD §9). */
@Component({
  selector: 'app-error-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="error-banner" role="alert" aria-live="polite">
      {{ message() }}
    </div>
  `,
  styleUrl: './error-banner.component.scss',
})
export class ErrorBannerComponent {
  readonly message = input.required<string>();
}
