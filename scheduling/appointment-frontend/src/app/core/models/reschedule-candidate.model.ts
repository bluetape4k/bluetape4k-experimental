export interface RescheduleCandidate {
  id: number;
  originalAppointmentId: number;
  candidateDate: string;  // YYYY-MM-DD
  startTime: string;      // HH:mm:ss
  endTime: string;        // HH:mm:ss
  doctorId: number;
  priority: number;
  selected: boolean;
}
