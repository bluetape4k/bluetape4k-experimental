import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { AppointmentService } from './appointment.service';
import { Appointment, AppointmentStatus } from '../models';

const mockAppointment = (id: number): Appointment => ({
  id,
  clinicId: 1,
  doctorId: 1,
  treatmentTypeId: 1,
  patientName: '홍길동',
  appointmentDate: '2025-06-01',
  startTime: '09:00:00',
  endTime: '09:30:00',
  status: AppointmentStatus.REQUESTED,
});

describe('AppointmentService', () => {
  let service: AppointmentService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AppointmentService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('서비스가 생성된다', () => {
    expect(service).toBeTruthy();
  });

  describe('getByDateRange()', () => {
    it('HTTP GET 요청 파라미터를 올바르게 전송하고 appointments signal을 업데이트한다', async () => {
      const mockData = [mockAppointment(1), mockAppointment(2)];
      const promise = service.getByDateRange(1, '2025-06-01', '2025-06-07');

      const req = httpMock.expectOne(r =>
        r.url === '/api/appointments' &&
        r.params.get('clinicId') === '1' &&
        r.params.get('startDate') === '2025-06-01' &&
        r.params.get('endDate') === '2025-06-07'
      );
      expect(req.request.method).toBe('GET');
      req.flush({ data: mockData });

      const result = await promise;
      expect(result).toEqual(mockData);
      expect(service.appointments()).toEqual(mockData);
    });

    it('응답 data가 null이면 빈 배열을 반환한다', async () => {
      const promise = service.getByDateRange(1, '2025-06-01', '2025-06-07');
      httpMock.expectOne(r => r.url === '/api/appointments').flush({ data: null });

      const result = await promise;
      expect(result).toEqual([]);
      expect(service.appointments()).toEqual([]);
    });
  });

  describe('loading signal', () => {
    it('요청 중에는 true, 완료 후에는 false가 된다', async () => {
      expect(service.loading()).toBe(false);

      const promise = service.getByDateRange(1, '2025-06-01', '2025-06-07');
      expect(service.loading()).toBe(true);

      httpMock.expectOne(r => r.url === '/api/appointments').flush({ data: [] });
      await promise;

      expect(service.loading()).toBe(false);
    });
  });

  describe('getById()', () => {
    it('id로 단건 조회 시 올바른 URL로 GET 요청을 보낸다', async () => {
      const mock = mockAppointment(42);
      const promise = service.getById(42);

      const req = httpMock.expectOne('/api/appointments/42');
      expect(req.request.method).toBe('GET');
      req.flush({ data: mock });

      const result = await promise;
      expect(result).toEqual(mock);
    });
  });

  describe('create()', () => {
    it('POST 요청으로 예약을 생성하고 appointments signal에 추가한다', async () => {
      const created = mockAppointment(10);
      const request = {
        clinicId: 1,
        doctorId: 1,
        treatmentTypeId: 1,
        patientName: '홍길동',
        appointmentDate: '2025-06-01',
        startTime: '09:00:00',
        endTime: '09:30:00',
      };

      const promise = service.create(request);

      const req = httpMock.expectOne('/api/appointments');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      req.flush({ data: created });

      const result = await promise;
      expect(result).toEqual(created);
      expect(service.appointments()).toContainEqual(created);
    });

    it('기존 목록에 새 항목을 추가한다', async () => {
      const existing = mockAppointment(1);
      const created = mockAppointment(2);

      // seed initial appointments
      const promise1 = service.getByDateRange(1, '2025-06-01', '2025-06-07');
      httpMock.expectOne(r => r.url === '/api/appointments').flush({ data: [existing] });
      await promise1;

      const promise2 = service.create({
        clinicId: 1, doctorId: 1, treatmentTypeId: 1,
        patientName: '김철수', appointmentDate: '2025-06-02',
        startTime: '10:00:00', endTime: '10:30:00',
      });
      httpMock.expectOne('/api/appointments').flush({ data: created });
      await promise2;

      expect(service.appointments()).toHaveLength(2);
      expect(service.appointments()).toContainEqual(created);
    });
  });

  describe('updateStatus()', () => {
    it('PATCH 요청으로 상태를 변경하고 appointments signal을 업데이트한다', async () => {
      const original = mockAppointment(5);
      const updated = { ...original, status: AppointmentStatus.CONFIRMED };

      // seed
      const seedPromise = service.getByDateRange(1, '2025-06-01', '2025-06-07');
      httpMock.expectOne(r => r.url === '/api/appointments').flush({ data: [original] });
      await seedPromise;

      const promise = service.updateStatus(5, { status: AppointmentStatus.CONFIRMED });

      const req = httpMock.expectOne('/api/appointments/5/status');
      expect(req.request.method).toBe('PATCH');
      expect(req.request.body).toEqual({ status: AppointmentStatus.CONFIRMED });
      req.flush({ data: updated });

      const result = await promise;
      expect(result).toEqual(updated);
      expect(service.appointments()[0].status).toBe(AppointmentStatus.CONFIRMED);
    });
  });

  describe('cancel()', () => {
    it('DELETE 요청으로 예약을 취소하고 appointments signal을 업데이트한다', async () => {
      const original = mockAppointment(7);
      const cancelled = { ...original, status: AppointmentStatus.CANCELLED };

      // seed
      const seedPromise = service.getByDateRange(1, '2025-06-01', '2025-06-07');
      httpMock.expectOne(r => r.url === '/api/appointments').flush({ data: [original] });
      await seedPromise;

      const promise = service.cancel(7);

      const req = httpMock.expectOne('/api/appointments/7');
      expect(req.request.method).toBe('DELETE');
      req.flush({ data: cancelled });

      const result = await promise;
      expect(result).toEqual(cancelled);
      expect(service.appointments()[0].status).toBe(AppointmentStatus.CANCELLED);
    });
  });
});
