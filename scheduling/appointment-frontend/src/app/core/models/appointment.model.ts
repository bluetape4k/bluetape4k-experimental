export enum AppointmentStatus {
  REQUESTED = 'REQUESTED',
  CONFIRMED = 'CONFIRMED',
  CHECKED_IN = 'CHECKED_IN',
  IN_PROGRESS = 'IN_PROGRESS',
  COMPLETED = 'COMPLETED',
  CANCELLED = 'CANCELLED',
  NO_SHOW = 'NO_SHOW',
  PENDING_RESCHEDULE = 'PENDING_RESCHEDULE',
  RESCHEDULED = 'RESCHEDULED',
}

export interface Appointment {
  id: number;
  clinicId: number;
  doctorId: number;
  treatmentTypeId: number;
  equipmentId?: number;
  patientName: string;
  patientPhone?: string;
  appointmentDate: string; // LocalDate → ISO date string (YYYY-MM-DD)
  startTime: string;       // LocalTime → HH:mm:ss
  endTime: string;         // LocalTime → HH:mm:ss
  status: string;
  timezone?: string;
  locale?: string;
  createdAt?: string;      // Instant → ISO date-time string
  updatedAt?: string;
}

export interface CreateAppointmentRequest {
  clinicId: number;
  doctorId: number;
  treatmentTypeId: number;
  equipmentId?: number;
  patientName: string;
  patientPhone?: string;
  appointmentDate: string; // YYYY-MM-DD
  startTime: string;       // HH:mm:ss
  endTime: string;         // HH:mm:ss
}

export interface UpdateStatusRequest {
  status: string;
  reason?: string;
}
