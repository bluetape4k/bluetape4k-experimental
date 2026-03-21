import { describe, it, expect, beforeEach, vi } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideLocationMocks } from '@angular/common/testing';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { signal, computed } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { AppointmentDetailComponent } from './appointment-detail.component';
import { AppointmentService } from '../../../core/services/appointment.service';
import { AuthService } from '../../../core/services/auth.service';
import { Appointment, AppointmentStatus } from '../../../core/models';

const mockAppointment = (status: AppointmentStatus): Appointment => ({
  id: 1,
  clinicId: 1,
  doctorId: 1,
  treatmentTypeId: 1,
  patientName: '홍길동',
  appointmentDate: '2025-06-01',
  startTime: '09:00:00',
  endTime: '09:30:00',
  status,
});

describe('AppointmentDetailComponent', () => {
  const mockAuthService = {
    isAdmin: signal(false),
    isStaff: signal(false),
    isDoctor: signal(false),
    isPatient: signal(false),
    getToken: vi.fn().mockReturnValue(null),
  };

  const mockAppointmentService = {
    getById: vi.fn(),
    updateStatus: vi.fn(),
    appointments: signal<Appointment[]>([]),
    loading: signal(false),
  };

  function createComponent() {
    return TestBed.createComponent(AppointmentDetailComponent);
  }

  beforeEach(() => {
    vi.clearAllMocks();
    mockAppointmentService.getById.mockResolvedValue(mockAppointment(AppointmentStatus.REQUESTED));

    TestBed.configureTestingModule({
      imports: [AppointmentDetailComponent],
      providers: [
        provideRouter([]),
        provideLocationMocks(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideAnimationsAsync(),
        { provide: AuthService, useValue: mockAuthService },
        { provide: AppointmentService, useValue: mockAppointmentService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: { get: () => '1' } },
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

  it('ADMIN 권한이면 canManage()가 true이다', async () => {
    mockAuthService.isAdmin.set(true);
    mockAuthService.isStaff.set(false);

    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.canManage()).toBe(true);
    mockAuthService.isAdmin.set(false);
  });

  it('STAFF 권한이면 canManage()가 true이다', async () => {
    mockAuthService.isAdmin.set(false);
    mockAuthService.isStaff.set(true);

    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.canManage()).toBe(true);
    mockAuthService.isStaff.set(false);
  });

  it('권한이 없으면 canManage()가 false이다', async () => {
    mockAuthService.isAdmin.set(false);
    mockAuthService.isStaff.set(false);

    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();

    expect(fixture.componentInstance.canManage()).toBe(false);
  });

  it('REQUESTED 상태의 예약은 확정/취소 전환이 있다', async () => {
    mockAppointmentService.getById.mockResolvedValue(mockAppointment(AppointmentStatus.REQUESTED));

    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const component = fixture.componentInstance;
    const transitions = component.transitions();
    const labels = transitions.map(t => t.label);
    expect(labels).toContain('확정');
    expect(labels).toContain('취소');
  });

  it('CONFIRMED 상태의 예약은 체크인/재배정요청/취소 전환이 있다', async () => {
    mockAppointmentService.getById.mockResolvedValue(mockAppointment(AppointmentStatus.CONFIRMED));

    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const component = fixture.componentInstance;
    const labels = component.transitions().map(t => t.label);
    expect(labels).toContain('체크인');
    expect(labels).toContain('취소');
  });

  it('예약이 없을 때 transitions()는 빈 배열이다', async () => {
    mockAppointmentService.getById.mockResolvedValue(null as unknown as Appointment);

    const fixture = createComponent();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.componentInstance.transitions()).toEqual([]);
  });
});
