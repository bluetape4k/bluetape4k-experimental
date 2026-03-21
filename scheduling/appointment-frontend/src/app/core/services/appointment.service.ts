import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { ApiResponse, Appointment, CreateAppointmentRequest, UpdateStatusRequest } from '../models';

@Injectable({ providedIn: 'root' })
export class AppointmentService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api/appointments';

  private readonly _appointments = signal<Appointment[]>([]);
  readonly appointments = this._appointments.asReadonly();

  readonly loading = signal(false);

  async getByDateRange(clinicId: number, from: string, to: string): Promise<Appointment[]> {
    this.loading.set(true);
    try {
      const params = new HttpParams()
        .set('clinicId', clinicId)
        .set('startDate', from)
        .set('endDate', to);
      const res = await firstValueFrom(
        this.http.get<ApiResponse<Appointment[]>>(this.baseUrl, { params })
      );
      const data = res.data ?? [];
      this._appointments.set(data);
      return data;
    } finally {
      this.loading.set(false);
    }
  }

  async getById(id: number): Promise<Appointment> {
    const res = await firstValueFrom(
      this.http.get<ApiResponse<Appointment>>(`${this.baseUrl}/${id}`)
    );
    return res.data!;
  }

  async create(request: CreateAppointmentRequest): Promise<Appointment> {
    const res = await firstValueFrom(
      this.http.post<ApiResponse<Appointment>>(this.baseUrl, request)
    );
    const created = res.data!;
    this._appointments.update(list => [...list, created]);
    return created;
  }

  async updateStatus(id: number, request: UpdateStatusRequest): Promise<Appointment> {
    const res = await firstValueFrom(
      this.http.patch<ApiResponse<Appointment>>(`${this.baseUrl}/${id}/status`, request)
    );
    const updated = res.data!;
    this._appointments.update(list =>
      list.map(a => (a.id === id ? updated : a))
    );
    return updated;
  }

  async cancel(id: number, reason?: string): Promise<Appointment> {
    const res = await firstValueFrom(
      this.http.delete<ApiResponse<Appointment>>(`${this.baseUrl}/${id}`)
    );
    const cancelled = res.data!;
    this._appointments.update(list =>
      list.map(a => (a.id === id ? cancelled : a))
    );
    return cancelled;
  }
}
