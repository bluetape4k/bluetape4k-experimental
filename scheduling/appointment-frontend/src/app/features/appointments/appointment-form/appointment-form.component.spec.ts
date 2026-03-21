import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideLocationMocks } from '@angular/common/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { AppointmentFormComponent } from './appointment-form.component';
import { AppointmentService } from '../../../core/services/appointment.service';
import { SlotService } from '../../../core/services/slot.service';

describe('AppointmentFormComponent', () => {
  const mockAppointmentService = {
    getById: vi.fn(),
    create: vi.fn(),
    appointments: signal([]),
    loading: signal(false),
  };

  const mockSlotService = {
    getAvailableSlots: vi.fn().mockResolvedValue([]),
  };

  function createComponent() {
    return TestBed.createComponent(AppointmentFormComponent);
  }

  beforeEach(() => {
    vi.clearAllMocks();

    TestBed.configureTestingModule({
      imports: [AppointmentFormComponent],
      providers: [
        provideRouter([]),
        provideLocationMocks(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimationsAsync(),
        { provide: AppointmentService, useValue: mockAppointmentService },
        { provide: SlotService, useValue: mockSlotService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: { get: () => null } },
          },
        },
      ],
    });
  });

  it('컴포넌트가 생성된다', async () => {
    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('폼이 초기에 invalid 상태이다', async () => {
    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('필수 필드(patientName)가 비어있으면 폼이 invalid이다', async () => {
    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    const form = fixture.componentInstance.form;
    form.patchValue({
      doctorId: 1,
      treatmentTypeId: 1,
      appointmentDate: new Date('2025-06-01'),
      startTime: '09:00:00',
      patientName: '',
    });

    expect(form.get('patientName')?.invalid).toBe(true);
    expect(form.invalid).toBe(true);
  });

  it('필수 필드(doctorId)가 없으면 폼이 invalid이다', async () => {
    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    const form = fixture.componentInstance.form;
    form.patchValue({
      doctorId: null,
      treatmentTypeId: 1,
      appointmentDate: new Date('2025-06-01'),
      startTime: '09:00:00',
      patientName: '홍길동',
    });

    expect(form.get('doctorId')?.invalid).toBe(true);
    expect(form.invalid).toBe(true);
  });

  it('필수 필드(treatmentTypeId)가 없으면 폼이 invalid이다', async () => {
    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    const form = fixture.componentInstance.form;
    form.patchValue({
      doctorId: 1,
      treatmentTypeId: null,
      appointmentDate: new Date('2025-06-01'),
      startTime: '09:00:00',
      patientName: '홍길동',
    });

    expect(form.get('treatmentTypeId')?.invalid).toBe(true);
    expect(form.invalid).toBe(true);
  });

  it('필수 필드(appointmentDate)가 없으면 폼이 invalid이다', async () => {
    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    const form = fixture.componentInstance.form;
    form.patchValue({
      doctorId: 1,
      treatmentTypeId: 1,
      appointmentDate: null,
      startTime: '09:00:00',
      patientName: '홍길동',
    });

    expect(form.get('appointmentDate')?.invalid).toBe(true);
    expect(form.invalid).toBe(true);
  });

  it('필수 필드(startTime)가 없으면 폼이 invalid이다', async () => {
    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    const form = fixture.componentInstance.form;
    form.patchValue({
      doctorId: 1,
      treatmentTypeId: 1,
      appointmentDate: new Date('2025-06-01'),
      startTime: '',
      patientName: '홍길동',
    });

    expect(form.get('startTime')?.invalid).toBe(true);
    expect(form.invalid).toBe(true);
  });

  it('모든 필수 필드가 채워지면 폼이 valid이다', async () => {
    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    const form = fixture.componentInstance.form;
    form.setValue({
      doctorId: 1,
      treatmentTypeId: 1,
      appointmentDate: new Date('2025-06-01'),
      startTime: '09:00:00',
      endTime: '09:30:00',
      patientName: '홍길동',
      patientPhone: '',
    });

    expect(form.valid).toBe(true);
  });

  it('새 예약 모드(id 파라미터 없음)에서 isEditMode()가 false이다', async () => {
    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.isEditMode()).toBe(false);
  });
});
