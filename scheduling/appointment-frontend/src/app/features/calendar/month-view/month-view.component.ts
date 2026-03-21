import { Component, computed, effect, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AppointmentService, CalendarStateService } from '../../../core/services';

const CLINIC_ID = 1;
const WEEKDAY_LABELS = ['월', '화', '수', '목', '금', '토', '일'];

interface CalendarCell {
  date: Date;
  dateStr: string;
  day: number;
  isCurrentMonth: boolean;
  isToday: boolean;
}

@Component({
  selector: 'app-month-view',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './month-view.component.html',
  styleUrl: './month-view.component.scss',
})
export class MonthViewComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly appointmentService = inject(AppointmentService);
  private readonly calendarState = inject(CalendarStateService);

  protected readonly weekdays = WEEKDAY_LABELS;

  protected readonly cells = computed<CalendarCell[]>(() => {
    const current = this.calendarState.currentDate();
    const year = current.getFullYear();
    const month = current.getMonth();
    const firstDay = new Date(year, month, 1);
    const lastDay = new Date(year, month + 1, 0);
    const todayStr = new Date().toISOString().slice(0, 10);

    // Monday-based: 0=Mon..6=Sun
    let startDow = firstDay.getDay() - 1;
    if (startDow < 0) startDow = 6;

    const cells: CalendarCell[] = [];

    // Previous month padding
    for (let i = startDow - 1; i >= 0; i--) {
      const d = new Date(year, month, -i);
      cells.push(this.makeCell(d, false, todayStr));
    }

    // Current month
    for (let day = 1; day <= lastDay.getDate(); day++) {
      const d = new Date(year, month, day);
      cells.push(this.makeCell(d, true, todayStr));
    }

    // Next month padding (fill to complete last row)
    const remainder = cells.length % 7;
    if (remainder > 0) {
      const pad = 7 - remainder;
      for (let i = 1; i <= pad; i++) {
        const d = new Date(year, month + 1, i);
        cells.push(this.makeCell(d, false, todayStr));
      }
    }

    return cells;
  });

  constructor() {
    // currentDate가 변경될 때마다 예약 데이터 재조회
    effect(() => {
      const cells = this.cells();
      if (cells.length > 0) {
        const from = cells[0].dateStr;
        const to = cells[cells.length - 1].dateStr;
        this.appointmentService.getByDateRange(CLINIC_ID, from, to);
      }
    });
  }

  ngOnInit(): void {
    const dateParam = this.route.snapshot.paramMap.get('date');
    if (dateParam) {
      this.calendarState.viewMode.set('month');
      this.calendarState.currentDate.set(new Date(dateParam + 'T00:00:00'));
    }
  }

  protected getAppointments(dateStr: string) {
    return this.appointmentService.appointments()
      .filter(a => a.appointmentDate === dateStr)
      .sort((a, b) => a.startTime.localeCompare(b.startTime));
  }

  protected goToDay(dateStr: string): void {
    this.router.navigate(['/calendar', 'day', dateStr]);
  }

  private makeCell(d: Date, isCurrentMonth: boolean, todayStr: string): CalendarCell {
    const dateStr = d.toISOString().slice(0, 10);
    return {
      date: d,
      dateStr,
      day: d.getDate(),
      isCurrentMonth,
      isToday: dateStr === todayStr,
    };
  }
}
