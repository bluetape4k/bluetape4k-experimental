import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDividerModule } from '@angular/material/divider';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';

import { AppointmentService } from '../../../core/services/appointment.service';
import { DoctorService } from '../../../core/services/doctor.service';
import { TreatmentTypeService } from '../../../core/services/treatment-type.service';
import { AuthService } from '../../../core/services/auth.service';
import { Appointment, AppointmentStatus } from '../../../core/models';
import { StatusBadgeComponent, TimeRangePipe, ConfirmDialogComponent, ConfirmDialogData } from '../../../shared';

interface StatusTransition {
  label: string;
  targetStatus: AppointmentStatus;
  color: 'primary' | 'accent' | 'warn';
}

const STATUS_TRANSITIONS: Record<string, StatusTransition[]> = {
  [AppointmentStatus.REQUESTED]: [
    { label: '확정', targetStatus: AppointmentStatus.CONFIRMED, color: 'primary' },
    { label: '취소', targetStatus: AppointmentStatus.CANCELLED, color: 'warn' },
  ],
  [AppointmentStatus.CONFIRMED]: [
    { label: '체크인', targetStatus: AppointmentStatus.CHECKED_IN, color: 'primary' },
    { label: '재배정 요청', targetStatus: AppointmentStatus.PENDING_RESCHEDULE, color: 'accent' },
    { label: '취소', targetStatus: AppointmentStatus.CANCELLED, color: 'warn' },
  ],
  [AppointmentStatus.CHECKED_IN]: [
    { label: '진료 시작', targetStatus: AppointmentStatus.IN_PROGRESS, color: 'primary' },
    { label: '취소', targetStatus: AppointmentStatus.CANCELLED, color: 'warn' },
  ],
  [AppointmentStatus.IN_PROGRESS]: [
    { label: '진료 완료', targetStatus: AppointmentStatus.COMPLETED, color: 'primary' },
  ],
  [AppointmentStatus.PENDING_RESCHEDULE]: [
    { label: '재배정 확인', targetStatus: AppointmentStatus.RESCHEDULED, color: 'primary' },
    { label: '취소', targetStatus: AppointmentStatus.CANCELLED, color: 'warn' },
  ],
};

@Component({
  selector: 'app-appointment-detail',
  standalone: true,
  imports: [
    CommonModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatDividerModule,
    MatProgressSpinnerModule,
    MatDialogModule,
    StatusBadgeComponent,
    TimeRangePipe,
  ],
  templateUrl: './appointment-detail.component.html',
  styleUrls: ['./appointment-detail.component.scss'],
})
export class AppointmentDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly appointmentService = inject(AppointmentService);
  private readonly doctorService = inject(DoctorService);
  private readonly treatmentTypeService = inject(TreatmentTypeService);
  private readonly authService = inject(AuthService);
  private readonly dialog = inject(MatDialog);

  readonly appointment = signal<Appointment | null>(null);
  readonly loading = signal(true);

  readonly canManage = computed(() => this.authService.isAdmin() || this.authService.isStaff());

  readonly transitions = computed<StatusTransition[]>(() => {
    const appt = this.appointment();
    if (!appt) return [];
    return STATUS_TRANSITIONS[appt.status] ?? [];
  });

  readonly doctorName = computed(() => {
    const appt = this.appointment();
    if (!appt) return '-';
    const doctor = this.doctorService.doctors().find(d => d.id === appt.doctorId);
    return doctor?.name ?? '-';
  });

  readonly treatmentTypeName = computed(() => {
    const appt = this.appointment();
    if (!appt) return '-';
    const tt = this.treatmentTypeService.treatmentTypes().find(t => t.id === appt.treatmentTypeId);
    return tt?.name ?? '-';
  });

  private readonly clinicId = 1;

  async ngOnInit(): Promise<void> {
    this.doctorService.loadByClinic(this.clinicId);
    this.treatmentTypeService.loadByClinic(this.clinicId);

    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (id) {
      await this.loadAppointment(id);
    }
  }

  async loadAppointment(id: number): Promise<void> {
    this.loading.set(true);
    try {
      const appt = await this.appointmentService.getById(id);
      this.appointment.set(appt);
    } finally {
      this.loading.set(false);
    }
  }

  async onStatusChange(transition: StatusTransition): Promise<void> {
    const appt = this.appointment();
    if (!appt) return;

    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: '상태 변경',
        message: `예약 상태를 "${transition.label}"(으)로 변경하시겠습니까?`,
        confirmText: transition.label,
      } as ConfirmDialogData,
    });

    const confirmed = await dialogRef.afterClosed().toPromise();
    if (!confirmed) return;

    const updated = await this.appointmentService.updateStatus(appt.id, {
      status: transition.targetStatus,
    });
    this.appointment.set(updated);
  }

  goBack(): void {
    this.router.navigate(['/appointments']);
  }
}
