import { Component, computed, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { AppointmentService, CalendarStateService } from '../../../core/services';

const CLINIC_ID = 1;
const START_HOUR = 8;
const END_HOUR = 18;
const SLOT_MINUTES = 30;
const WEEKDAYS = ['월', '화', '수', '목', '금', '토', '일'];

interface WeekDay {
  date: Date;
  dateStr: string;
  label: string;
  isToday: boolean;
}

interface TimeSlot {
  label: string;
  hour: number;
  minute: number;
}

@Component({
  selector: 'app-week-view',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './week-view.component.html',
  styleUrl: './week-view.component.scss',
})
export class WeekViewComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly appointmentService = inject(AppointmentService);
  private readonly calendarState = inject(CalendarStateService);

  protected readonly timeSlots: TimeSlot[] = [];

  protected readonly weekDays = computed<WeekDay[]>(() => {
    const range = this.calendarState.dateRange();
    const days: WeekDay[] = [];
    const today = new Date().toISOString().slice(0, 10);
    const current = new Date(range.start);
    for (let i = 0; i < 7; i++) {
      const d = new Date(current);
      const dateStr = d.toISOString().slice(0, 10);
      days.push({
        date: d,
        dateStr,
        label: `${WEEKDAYS[i]} ${d.getDate()}`,
        isToday: dateStr === today,
      });
      current.setDate(current.getDate() + 1);
    }
    return days;
  });

  constructor() {
    for (let h = START_HOUR; h < END_HOUR; h++) {
      for (let m = 0; m < 60; m += SLOT_MINUTES) {
        this.timeSlots.push({
          label: `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`,
          hour: h,
          minute: m,
        });
      }
    }
  }

  ngOnInit(): void {
    const dateParam = this.route.snapshot.paramMap.get('date');
    if (dateParam) {
      this.calendarState.viewMode.set('week');
      this.calendarState.currentDate.set(new Date(dateParam + 'T00:00:00'));
    }
    this.loadAppointments();
  }

  private async loadAppointments(): Promise<void> {
    const range = this.calendarState.dateRange();
    const from = range.start.toISOString().slice(0, 10);
    const to = range.end.toISOString().slice(0, 10);
    await this.appointmentService.getByDateRange(CLINIC_ID, from, to);
  }

  protected getCount(dateStr: string, slot: TimeSlot): number {
    const slotMin = slot.hour * 60 + slot.minute;
    return this.appointmentService.appointments().filter(a => {
      if (a.appointmentDate !== dateStr) return false;
      const [h, m] = a.startTime.split(':').map(Number);
      const aptMin = h * 60 + m;
      return aptMin >= slotMin && aptMin < slotMin + SLOT_MINUTES;
    }).length;
  }

  protected cellColor(count: number): string {
    if (count === 0) return 'transparent';
    if (count <= 1) return '#c8e6c9';
    if (count <= 3) return '#fff9c4';
    return '#ffcdd2';
  }

  protected goToDay(dateStr: string): void {
    this.router.navigate(['/calendar', 'day', dateStr]);
  }
}
