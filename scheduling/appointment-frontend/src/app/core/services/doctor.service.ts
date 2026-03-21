import { Injectable, signal } from '@angular/core';
import { Doctor } from '../models';

// Backend does not expose a doctor API yet — mock data is returned.
const MOCK_DOCTORS: Doctor[] = [
  { id: 1, clinicId: 1, name: '김민준', specialty: '일반의' },
  { id: 2, clinicId: 1, name: '이서연', specialty: '치과' },
  { id: 3, clinicId: 2, name: '박지호', specialty: '소아과' },
];

@Injectable({ providedIn: 'root' })
export class DoctorService {
  private readonly _doctors = signal<Doctor[]>([]);
  readonly doctors = this._doctors.asReadonly();

  loadByClinic(clinicId: number): void {
    const filtered = MOCK_DOCTORS.filter(d => d.clinicId === clinicId);
    this._doctors.set(filtered);
  }
}
