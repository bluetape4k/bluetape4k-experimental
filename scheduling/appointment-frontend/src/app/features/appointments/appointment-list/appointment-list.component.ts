import { Component, OnInit, inject, signal, computed, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { provideNativeDateAdapter } from '@angular/material/core';

import { AppointmentService } from '../../../core/services/appointment.service';
import { DoctorService } from '../../../core/services/doctor.service';
import { AuthService } from '../../../core/services/auth.service';
import { Appointment, AppointmentStatus, Doctor } from '../../../core/models';
import { StatusBadgeComponent, StatusLabelPipe, TimeRangePipe } from '../../../shared';

@Component({
  selector: 'app-appointment-list',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatTableModule,
    MatPaginatorModule,
    MatSelectModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatChipsModule,
    MatProgressSpinnerModule,
    StatusBadgeComponent,
    StatusLabelPipe,
    TimeRangePipe,
  ],
  providers: [provideNativeDateAdapter()],
  templateUrl: './appointment-list.component.html',
  styleUrls: ['./appointment-list.component.scss'],
})
export class AppointmentListComponent implements OnInit {
  private readonly appointmentService = inject(AppointmentService);
  private readonly doctorService = inject(DoctorService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  readonly canCreate = computed(() => this.authService.isAdmin() || this.authService.isStaff());
  readonly loading = this.appointmentService.loading;
  readonly doctors = this.doctorService.doctors;

  readonly allStatuses = Object.values(AppointmentStatus);

  dataSource = new MatTableDataSource<Appointment>([]);
  displayedColumns = ['appointmentDate', 'time', 'patientName', 'doctor', 'status'];

  dateFrom: Date = this.getMonday(new Date());
  dateTo: Date = this.getSunday(new Date());
  selectedDoctorId: number | null = null;
  selectedStatuses: AppointmentStatus[] = [];

  private readonly clinicId = 1;

  ngOnInit(): void {
    this.doctorService.loadByClinic(this.clinicId);
    this.loadAppointments();
  }

  ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
  }

  async loadAppointments(): Promise<void> {
    const from = this.formatDate(this.dateFrom);
    const to = this.formatDate(this.dateTo);
    const appointments = await this.appointmentService.getByDateRange(this.clinicId, from, to);
    this.applyFilters(appointments);
  }

  applyFilters(appointments?: Appointment[]): void {
    const source = appointments ?? this.appointmentService.appointments();
    let filtered = source;

    if (this.selectedDoctorId) {
      filtered = filtered.filter(a => a.doctorId === this.selectedDoctorId);
    }

    if (this.selectedStatuses.length > 0) {
      filtered = filtered.filter(a => this.selectedStatuses.includes(a.status as AppointmentStatus));
    }

    this.dataSource.data = filtered;
  }

  onFilterChange(): void {
    this.applyFilters();
  }

  onDateRangeChange(): void {
    if (this.dateFrom && this.dateTo) {
      this.loadAppointments();
    }
  }

  onRowClick(appointment: Appointment): void {
    this.router.navigate(['/appointments', appointment.id]);
  }

  navigateToNew(): void {
    this.router.navigate(['/appointments', 'new']);
  }

  getDoctorName(doctorId: number): string {
    const doctor = this.doctors().find(d => d.id === doctorId);
    return doctor?.name ?? '-';
  }

  toggleStatus(status: AppointmentStatus): void {
    const index = this.selectedStatuses.indexOf(status);
    if (index >= 0) {
      this.selectedStatuses.splice(index, 1);
    } else {
      this.selectedStatuses.push(status);
    }
    this.onFilterChange();
  }

  isStatusSelected(status: AppointmentStatus): boolean {
    return this.selectedStatuses.includes(status);
  }

  private formatDate(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }

  private getMonday(date: Date): Date {
    const d = new Date(date);
    const day = d.getDay();
    const diff = d.getDate() - day + (day === 0 ? -6 : 1);
    d.setDate(diff);
    return d;
  }

  private getSunday(date: Date): Date {
    const monday = this.getMonday(date);
    const sunday = new Date(monday);
    sunday.setDate(monday.getDate() + 6);
    return sunday;
  }
}
