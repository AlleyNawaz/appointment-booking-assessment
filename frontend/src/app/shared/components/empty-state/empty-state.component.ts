import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** One of the three renderable states every API-backed page must handle (PRD §9). */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty-state">
      {{ message() }}
    </div>
  `,
  styleUrl: './empty-state.component.scss',
})
export class EmptyStateComponent {
  readonly message = input('Nothing to show here.');
}
