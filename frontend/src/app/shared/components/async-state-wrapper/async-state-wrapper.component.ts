import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { EmptyStateComponent } from '../empty-state/empty-state.component';
import { ErrorBannerComponent } from '../error-banner/error-banner.component';
import { LoadingSpinnerComponent } from '../loading-spinner/loading-spinner.component';

/**
 * Every API-backed page wraps its content in this (PRD §9) so loading, empty,
 * and error states can never be forgotten — content is only projected once
 * none of the three apply.
 */
@Component({
  selector: 'app-async-state-wrapper',
  standalone: true,
  imports: [LoadingSpinnerComponent, ErrorBannerComponent, EmptyStateComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (loading()) {
      <app-loading-spinner [label]="loadingLabel()" />
    } @else if (error()) {
      <app-error-banner [message]="error()!" />
    } @else if (empty()) {
      <app-empty-state [message]="emptyMessage()" />
    } @else {
      <ng-content />
    }
  `,
})
export class AsyncStateWrapperComponent {
  readonly loading = input(false);
  readonly error = input<string | null>(null);
  readonly empty = input(false);
  readonly loadingLabel = input('Loading…');
  readonly emptyMessage = input('Nothing to show here.');
}
