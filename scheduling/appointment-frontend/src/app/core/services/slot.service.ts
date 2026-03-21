import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ApiResponse, AvailableSlot } from '../models';

@Injectable({ providedIn: 'root' })
export class SlotService {
  private readonly http = inject(HttpClient);

  async getAvailableSlots(
    clinicId: number,
    doctorId: number,
    treatmentTypeId: number,
    date: string,
    requestedDurationMinutes?: number,
  ): Promise<AvailableSlot[]> {
    let params = new HttpParams()
      .set('doctorId', doctorId)
      .set('treatmentTypeId', treatmentTypeId)
      .set('date', date);

    if (requestedDurationMinutes != null) {
      params = params.set('requestedDurationMinutes', requestedDurationMinutes);
    }

    const res = await firstValueFrom(
      this.http.get<ApiResponse<AvailableSlot[]>>(
        `/api/clinics/${clinicId}/slots`,
        { params }
      )
    );
    return res.data ?? [];
  }
}
