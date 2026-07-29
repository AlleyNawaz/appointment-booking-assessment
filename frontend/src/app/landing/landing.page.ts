import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AppHeaderComponent } from '../shared/layout/app-header/app-header.component';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslatePipe } from '../core/i18n/translate.pipe';

@Component({
  selector: 'app-landing-page',
  standalone: true,
  imports: [AppHeaderComponent, RouterLink, MatButtonModule, MatIconModule, TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './landing.page.html',
  styleUrl: './landing.page.scss',
})
export class LandingPage {
  // We can use a mocked calendar for the right side illustration.
  // We don't need real logic here since it's just for show.
  mockDays = Array.from({ length: 31 }, (_, i) => i + 1);
}
