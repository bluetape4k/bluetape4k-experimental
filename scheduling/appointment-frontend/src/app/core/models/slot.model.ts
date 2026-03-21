export interface AvailableSlot {
  date: string;        // YYYY-MM-DD
  startTime: string;   // HH:mm:ss
  endTime: string;     // HH:mm:ss
  doctorId: number;
  equipmentIds: number[];
  remainingCapacity: number;
}
