import { Component, computed, effect, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterOutlet } from '@angular/router';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { CalendarStateService, ViewMode } from '../../../core/services';

@Component({
  selector: 'app-calendar-view',
  standalone: true,
  imports: [
    CommonModule,
    RouterOutlet,
    MatButtonToggleModule,
    MatButtonModule,
    MatIconModule,
    MatToolbarModule,
  ],
  templateUrl: './calendar-view.component.html',
  styleUrl: './calendar-view.component.scss',
})
export class CalendarViewComponent {
  private readonly router = inject(Router);
  protected readonly calendarState = inject(CalendarStateService);

  protected readonly headerLabel = computed(() => {
    const range = this.calendarState.dateRange();
    const mode = this.calendarState.viewMode();
    const opts: Intl.DateTimeFormatOptions =
      mode === 'month'
        ? { year: 'numeric', month: 'long' }
        : { year: 'numeric', month: 'short', day: 'numeric' };
    if (mode === 'day') {
      return range.start.toLocaleDateString('ko-KR', opts);
    }
    const startStr = range.start.toLocaleDateString('ko-KR', opts);
    const endStr = range.end.toLocaleDateString('ko-KR', opts);
    return `${startStr} ~ ${endStr}`;
  });

  constructor() {
    effect(() => {
      const mode = this.calendarState.viewMode();
      const date = this.calendarState.currentDate();
      const dateStr = date.toISOString().slice(0, 10);
      this.router.navigate(['/calendar', mode, dateStr]);
    });
  }

  onViewModeChange(mode: ViewMode): void {
    this.calendarState.viewMode.set(mode);
  }

  navigatePrev(): void {
    this.calendarState.navigatePrev();
  }

  navigateNext(): void {
    this.calendarState.navigateNext();
  }

  goToToday(): void {
    this.calendarState.goToToday();
  }
}
