import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AppHeaderComponent } from '../shared/layout/app-header/app-header.component';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-faq-page',
  standalone: true,
  imports: [AppHeaderComponent, TranslatePipe, MatExpansionModule, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './faq.page.html',
  styleUrl: './faq.page.scss',
})
export class FaqPage {
}
