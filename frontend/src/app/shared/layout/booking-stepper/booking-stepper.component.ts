import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

import { TranslatePipe } from '../../../core/i18n/translate.pipe';

interface StepDefinition {
  step: number;
  labelKey: string;
}

const STEPS: StepDefinition[] = [
  { step: 1, labelKey: 'shared.stepper.stepType' },
  { step: 2, labelKey: 'shared.stepper.stepProvider' },
  { step: 3, labelKey: 'shared.stepper.stepSchedule' },
  { step: 4, labelKey: 'shared.stepper.stepDetails' },
  { step: 5, labelKey: 'shared.stepper.stepConfirmation' },
];

/**
 * Read-only horizontal progress indicator for the 5-step patient booking wizard.
 * Purely presentational — it displays `currentStep` (1-5), it does not navigate
 * or drive routing; each wizard page already knows and passes its own step number.
 */
@Component({
  selector: 'app-booking-stepper',
  standalone: true,
  imports: [MatIconModule, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './booking-stepper.component.html',
  styleUrl: './booking-stepper.component.scss',
})
export class BookingStepperComponent {
  readonly currentStep = input.required<number>();

  readonly steps = computed(() =>
    STEPS.map((definition) => ({
      ...definition,
      status: this.statusFor(definition.step),
    }))
  );

  private statusFor(step: number): 'done' | 'current' | 'upcoming' {
    if (step < this.currentStep()) {
      return 'done';
    }
    if (step === this.currentStep()) {
      return 'current';
    }
    return 'upcoming';
  }
}
