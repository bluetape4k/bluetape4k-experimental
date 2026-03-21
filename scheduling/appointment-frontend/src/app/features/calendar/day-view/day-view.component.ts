import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { AppointmentService, CalendarStateService, DoctorService } from '../../../core/services';
import { Appointment } from '../../../core/models';
import { TimeRangePipe } from '../../../shared/pipes/time-range.pipe';

const CLINIC_ID = 1;
const START_HOUR = 8;
const END_HOUR = 18;
const SLOT_MINUTES = 30;

interface TimeSlot {
  label: string;
  hour: number;
  minute: number;
}

@Component({
  selector: 'app-day-view',
  standalone: true,
  imports: [CommonModule, TimeRangePipe],
  templateUrl: './day-view.component.html',
  styleUrl: './day-view.component.scss',
})
export class DayViewComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly appointmentService = inject(AppointmentService);
  private readonly calendarState = inject(CalendarStateService);
  protected readonly doctorService = inject(DoctorService);

  protected readonly timeSlots: TimeSlot[] = [];
  protected readonly appointments = this.appointmentService.appointments;
  protected readonly doctors = this.doctorService.doctors;

  protected readonly gridCols = computed(() => this.doctors().length + 1);

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
    this.doctorService.loadByClinic(CLINIC_ID);
    const dateParam = this.route.snapshot.paramMap.get('date');
    if (dateParam) {
      this.calendarState.viewMode.set('day');
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

  protected getAppointmentsForSlot(doctorId: number, slot: TimeSlot): Appointment[] {
    const slotMin = slot.hour * 60 + slot.minute;
    return this.appointments().filter(a => {
      if (a.doctorId !== doctorId) return false;
      const [h, m] = a.startTime.split(':').map(Number);
      const aptMin = h * 60 + m;
      return aptMin >= slotMin && aptMin < slotMin + SLOT_MINUTES;
    });
  }

  protected statusColor(status: string): string {
    const colors: Record<string, string> = {
      REQUESTED: '#2196F3',
      CONFIRMED: '#4CAF50',
      CHECKED_IN: '#FF9800',
      IN_PROGRESS: '#9C27B0',
      COMPLETED: '#607D8B',
      CANCELLED: '#F44336',
      NO_SHOW: '#795548',
    };
    return colors[status] ?? '#9E9E9E';
  }

  protected onAppointmentClick(appointment: Appointment): void {
    console.log('Appointment clicked:', appointment);
  }
}
