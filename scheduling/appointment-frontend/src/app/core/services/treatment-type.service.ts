import { Injectable, signal } from '@angular/core';
import { TreatmentType } from '../models';

// Backend does not expose a treatment-type API yet — mock data is returned.
const MOCK_TREATMENT_TYPES: TreatmentType[] = [
  { id: 1, clinicId: 1, name: '기본 진료', durationMinutes: 30 },
  { id: 2, clinicId: 1, name: '스케일링', durationMinutes: 60 },
  { id: 3, clinicId: 2, name: '예방 접종', durationMinutes: 15 },
];

@Injectable({ providedIn: 'root' })
export class TreatmentTypeService {
  private readonly _treatmentTypes = signal<TreatmentType[]>([]);
  readonly treatmentTypes = this._treatmentTypes.asReadonly();

  loadByClinic(clinicId: number): void {
    const filtered = MOCK_TREATMENT_TYPES.filter(t => t.clinicId === clinicId);
    this._treatmentTypes.set(filtered);
  }
}
