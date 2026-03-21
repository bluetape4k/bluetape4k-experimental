import { Component, OnInit, inject, signal, effect } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { provideNativeDateAdapter } from '@angular/material/core';

import { AppointmentService } from '../../../core/services/appointment.service';
import { DoctorService } from '../../../core/services/doctor.service';
import { TreatmentTypeService } from '../../../core/services/treatment-type.service';
import { SlotService } from '../../../core/services/slot.service';
import { AvailableSlot, CreateAppointmentRequest } from '../../../core/models';
import { TimeSlotPickerComponent } from '../../../shared';

@Component({
  selector: 'app-appointment-form',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TimeSlotPickerComponent,
  ],
  providers: [provideNativeDateAdapter()],
  templateUrl: './appointment-form.component.html',
  styleUrls: ['./appointment-form.component.scss'],
})
export class AppointmentFormComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly appointmentService = inject(AppointmentService);
  private readonly doctorService = inject(DoctorService);
  private readonly treatmentTypeService = inject(TreatmentTypeService);
  private readonly slotService = inject(SlotService);

  readonly doctors = this.doctorService.doctors;
  readonly treatmentTypes = this.treatmentTypeService.treatmentTypes;

  readonly availableSlots = signal<AvailableSlot[]>([]);
  readonly loadingSlots = signal(false);
  readonly submitting = signal(false);
  readonly isEditMode = signal(false);
  readonly editId = signal<number | null>(null);

  readonly form: FormGroup = this.fb.group({
    doctorId: [null as number | null, Validators.required],
    treatmentTypeId: [null as number | null, Validators.required],
    appointmentDate: [null as Date | null, Validators.required],
    startTime: ['', Validators.required],
    endTime: [''],
    patientName: ['', Validators.required],
    patientPhone: [''],
  });

  private readonly clinicId = 1;

  async ngOnInit(): Promise<void> {
    this.doctorService.loadByClinic(this.clinicId);
    this.treatmentTypeService.loadByClinic(this.clinicId);

    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.isEditMode.set(true);
      this.editId.set(Number(idParam));
      await this.loadExisting(Number(idParam));
    }
  }

  async onSlotSelectionInputChange(): Promise<void> {
    const doctorId = this.form.get('doctorId')!.value;
    const treatmentTypeId = this.form.get('treatmentTypeId')!.value;
    const dateValue = this.form.get('appointmentDate')!.value;

    if (!doctorId || !treatmentTypeId || !dateValue) {
      this.availableSlots.set([]);
      return;
    }

    const dateStr = this.formatDate(dateValue);
    const tt = this.treatmentTypes().find(t => t.id === treatmentTypeId);
    const duration = tt?.durationMinutes;

    this.loadingSlots.set(true);
    try {
      const slots = await this.slotService.getAvailableSlots(
        this.clinicId, doctorId, treatmentTypeId, dateStr, duration,
      );
      this.availableSlots.set(slots);
    } finally {
      this.loadingSlots.set(false);
    }
  }

  onSlotSelected(slot: AvailableSlot): void {
    this.form.patchValue({
      startTime: slot.startTime,
      endTime: slot.endTime,
    });
  }

  async onSubmit(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    try {
      const values = this.form.value;
      const request: CreateAppointmentRequest = {
        clinicId: this.clinicId,
        doctorId: values.doctorId,
        treatmentTypeId: values.treatmentTypeId,
        appointmentDate: this.formatDate(values.appointmentDate),
        startTime: values.startTime,
        endTime: values.endTime || values.startTime,
        patientName: values.patientName,
        patientPhone: values.patientPhone || undefined,
      };

      let result;
      if (this.isEditMode()) {
        // Edit mode: update status is the only edit API available
        // For MVP, re-create is not supported; navigate back
        result = await this.appointmentService.create(request);
      } else {
        result = await this.appointmentService.create(request);
      }

      this.router.navigate(['/appointments', result.id]);
    } finally {
      this.submitting.set(false);
    }
  }

  goBack(): void {
    if (this.isEditMode()) {
      this.router.navigate(['/appointments', this.editId()]);
    } else {
      this.router.navigate(['/appointments']);
    }
  }

  private async loadExisting(id: number): Promise<void> {
    const appt = await this.appointmentService.getById(id);
    this.form.patchValue({
      doctorId: appt.doctorId,
      treatmentTypeId: appt.treatmentTypeId,
      appointmentDate: new Date(appt.appointmentDate),
      startTime: appt.startTime,
      endTime: appt.endTime,
      patientName: appt.patientName,
      patientPhone: appt.patientPhone ?? '',
    });
  }

  private formatDate(date: Date): string {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  }
}
