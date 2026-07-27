import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** One of the three renderable states every API-backed page must handle (PRD §9). */
@Component({
  selector: 'app-loading-spinner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="loading-spinner" role="status" aria-live="polite">
      <span class="loading-spinner__icon" aria-hidden="true"></span>
      <span>{{ label() }}</span>
    </div>
  `,
  styleUrl: './loading-spinner.component.scss',
})
export class LoadingSpinnerComponent {
  readonly label = input('Loading…');
}
