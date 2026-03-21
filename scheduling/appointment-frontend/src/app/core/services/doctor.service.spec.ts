import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';

import { DoctorService } from './doctor.service';

describe('DoctorService', () => {
  let service: DoctorService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(DoctorService);
  });

  it('서비스가 생성된다', () => {
    expect(service).toBeTruthy();
  });

  it('초기 doctors signal은 빈 배열이다', () => {
    expect(service.doctors()).toEqual([]);
  });

  describe('loadByClinic()', () => {
    it('clinicId=1 로드 시 해당 클리닉 의사만 doctors signal에 설정된다', () => {
      service.loadByClinic(1);
      const doctors = service.doctors();
      expect(doctors.length).toBeGreaterThan(0);
      expect(doctors.every(d => d.clinicId === 1)).toBe(true);
    });

    it('clinicId=2 로드 시 해당 클리닉 의사만 반환된다', () => {
      service.loadByClinic(2);
      const doctors = service.doctors();
      expect(doctors.length).toBeGreaterThan(0);
      expect(doctors.every(d => d.clinicId === 2)).toBe(true);
    });

    it('존재하지 않는 clinicId면 빈 배열이 된다', () => {
      service.loadByClinic(999);
      expect(service.doctors()).toEqual([]);
    });

    it('clinicId=1과 clinicId=2는 서로 다른 의사 목록을 반환한다', () => {
      service.loadByClinic(1);
      const clinic1Ids = service.doctors().map(d => d.id);

      service.loadByClinic(2);
      const clinic2Ids = service.doctors().map(d => d.id);

      const intersection = clinic1Ids.filter(id => clinic2Ids.includes(id));
      expect(intersection).toHaveLength(0);
    });

    it('doctors signal에 id, name, specialty 필드가 있다', () => {
      service.loadByClinic(1);
      const doctor = service.doctors()[0];
      expect(doctor).toHaveProperty('id');
      expect(doctor).toHaveProperty('name');
      expect(doctor).toHaveProperty('specialty');
    });
  });
});
