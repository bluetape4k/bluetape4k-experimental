import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';

import { SlotService } from './slot.service';
import { AvailableSlot } from '../models';

const mockSlot = (startTime: string): AvailableSlot => ({
  date: '2025-06-01',
  startTime,
  endTime: '10:00:00',
  doctorId: 2,
  equipmentIds: [],
  remainingCapacity: 1,
});

describe('SlotService', () => {
  let service: SlotService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SlotService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('서비스가 생성된다', () => {
    expect(service).toBeTruthy();
  });

  describe('getAvailableSlots()', () => {
    it('올바른 쿼리 파라미터로 HTTP GET 요청을 보낸다', async () => {
      const slots = [mockSlot('09:00:00'), mockSlot('09:30:00')];
      const promise = service.getAvailableSlots(1, 2, 3, '2025-06-01');

      const req = httpMock.expectOne(r =>
        r.url === '/api/clinics/1/slots' &&
        r.params.get('doctorId') === '2' &&
        r.params.get('treatmentTypeId') === '3' &&
        r.params.get('date') === '2025-06-01'
      );
      expect(req.request.method).toBe('GET');
      req.flush({ data: slots });

      const result = await promise;
      expect(result).toEqual(slots);
    });

    it('requestedDurationMinutes 파라미터가 있을 때 쿼리에 포함된다', async () => {
      const promise = service.getAvailableSlots(1, 2, 3, '2025-06-01', 30);

      const req = httpMock.expectOne(r =>
        r.url === '/api/clinics/1/slots' &&
        r.params.get('requestedDurationMinutes') === '30'
      );
      req.flush({ data: [] });
      await promise;

      expect(req.request.params.has('requestedDurationMinutes')).toBe(true);
    });

    it('requestedDurationMinutes 파라미터가 없을 때 쿼리에 포함되지 않는다', async () => {
      const promise = service.getAvailableSlots(1, 2, 3, '2025-06-01');

      const req = httpMock.expectOne(r => r.url === '/api/clinics/1/slots');
      req.flush({ data: [] });
      await promise;

      expect(req.request.params.has('requestedDurationMinutes')).toBe(false);
    });

    it('응답 data가 null이면 빈 배열을 반환한다', async () => {
      const promise = service.getAvailableSlots(1, 2, 3, '2025-06-01');
      httpMock.expectOne(r => r.url === '/api/clinics/1/slots').flush({ data: null });

      const result = await promise;
      expect(result).toEqual([]);
    });

    it('클리닉 ID가 URL 경로에 포함된다', async () => {
      const promise = service.getAvailableSlots(5, 2, 3, '2025-06-01');

      const req = httpMock.expectOne(r => r.url === '/api/clinics/5/slots');
      req.flush({ data: [] });
      await promise;

      expect(req.request.url).toBe('/api/clinics/5/slots');
    });
  });
});
