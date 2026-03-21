import { Injectable, computed, signal } from '@angular/core';

export type ViewMode = 'day' | 'week' | 'month';

export interface DateRange {
  start: Date;
  end: Date;
}

@Injectable({ providedIn: 'root' })
export class CalendarStateService {
  readonly currentDate = signal<Date>(new Date());
  readonly viewMode = signal<ViewMode>('week');

  readonly dateRange = computed<DateRange>(() => {
    const date = this.currentDate();
    const mode = this.viewMode();
    return this._computeRange(date, mode);
  });

  navigateNext(): void {
    this.currentDate.update(d => this._shift(d, this.viewMode(), 1));
  }

  navigatePrev(): void {
    this.currentDate.update(d => this._shift(d, this.viewMode(), -1));
  }

  goToToday(): void {
    this.currentDate.set(new Date());
  }

  private _shift(date: Date, mode: ViewMode, direction: 1 | -1): Date {
    const d = new Date(date);
    switch (mode) {
      case 'day':
        d.setDate(d.getDate() + direction);
        break;
      case 'week':
        d.setDate(d.getDate() + direction * 7);
        break;
      case 'month':
        d.setMonth(d.getMonth() + direction);
        break;
    }
    return d;
  }

  private _computeRange(date: Date, mode: ViewMode): DateRange {
    switch (mode) {
      case 'day': {
        const start = this._startOfDay(date);
        const end = this._endOfDay(date);
        return { start, end };
      }
      case 'week': {
        const start = this._startOfWeek(date);
        const end = new Date(start);
        end.setDate(end.getDate() + 6);
        end.setHours(23, 59, 59, 999);
        return { start, end };
      }
      case 'month': {
        const start = new Date(date.getFullYear(), date.getMonth(), 1);
        const end = new Date(date.getFullYear(), date.getMonth() + 1, 0);
        end.setHours(23, 59, 59, 999);
        return { start, end };
      }
    }
  }

  private _startOfDay(d: Date): Date {
    const r = new Date(d);
    r.setHours(0, 0, 0, 0);
    return r;
  }

  private _endOfDay(d: Date): Date {
    const r = new Date(d);
    r.setHours(23, 59, 59, 999);
    return r;
  }

  private _startOfWeek(d: Date): Date {
    const r = new Date(d);
    const day = r.getDay(); // 0=Sunday
    const diff = day === 0 ? -6 : 1 - day; // Monday as week start
    r.setDate(r.getDate() + diff);
    r.setHours(0, 0, 0, 0);
    return r;
  }
}
