import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { ReactiveFormsModule, Validators } from '@angular/forms';
import { NonNullableFormBuilder } from '@angular/forms';
import { Router } from '@angular/router';

import { BookingStateService } from '../../state/booking-state.service';
import { TranslatePipe } from '../../../core/i18n/translate.pipe';

/**
 * `/book/details` (PRD §9) — reactive form mirroring §11's field rules
 * exactly. Client-side validation is a UX convenience only; the backend
 * re-validates everything.
 */
@Component({
  selector: 'app-contact-details-page',
  standalone: true,
  imports: [ReactiveFormsModule, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './contact-details.page.html',
})
export class ContactDetailsPage implements OnInit {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly bookingState = inject(BookingStateService);
  private readonly router = inject(Router);

  readonly contactForm = this.fb.group({
    fullName: ['', [Validators.required, Validators.pattern(/^[\p{L} '.-]{2,100}$/u)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    phone: ['', [Validators.required, Validators.pattern(/^\+[1-9]\d{7,14}$/)]],
    notes: ['', [Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    if (!this.bookingState.hasValidHold()) {
      this.router.navigateByUrl('/book/schedule');
      return;
    }

    const existing = this.bookingState.contact();
    if (existing) {
      this.contactForm.setValue(existing);
    }
  }

  submit(): void {
    if (this.contactForm.invalid) {
      this.contactForm.markAllAsTouched();
      return;
    }
    this.bookingState.setContact(this.contactForm.getRawValue());
    this.router.navigateByUrl('/book/confirm');
  }
}
