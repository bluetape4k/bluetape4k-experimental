import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';

import { TreatmentTypeService } from './treatment-type.service';

describe('TreatmentTypeService', () => {
  let service: TreatmentTypeService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TreatmentTypeService);
  });

  it('서비스가 생성된다', () => {
    expect(service).toBeTruthy();
  });

  it('초기 treatmentTypes signal은 빈 배열이다', () => {
    expect(service.treatmentTypes()).toEqual([]);
  });

  describe('loadByClinic()', () => {
    it('clinicId=1 로드 시 해당 클리닉 치료 유형만 설정된다', () => {
      service.loadByClinic(1);
      const types = service.treatmentTypes();
      expect(types.length).toBeGreaterThan(0);
      expect(types.every(t => t.clinicId === 1)).toBe(true);
    });

    it('clinicId=2 로드 시 해당 클리닉 치료 유형만 반환된다', () => {
      service.loadByClinic(2);
      const types = service.treatmentTypes();
      expect(types.length).toBeGreaterThan(0);
      expect(types.every(t => t.clinicId === 2)).toBe(true);
    });

    it('존재하지 않는 clinicId면 빈 배열이 된다', () => {
      service.loadByClinic(999);
      expect(service.treatmentTypes()).toEqual([]);
    });

    it('treatmentTypes signal에 id, name, durationMinutes 필드가 있다', () => {
      service.loadByClinic(1);
      const type = service.treatmentTypes()[0];
      expect(type).toHaveProperty('id');
      expect(type).toHaveProperty('name');
      expect(type).toHaveProperty('durationMinutes');
    });

    it('clinicId=1과 clinicId=2는 서로 다른 치료 유형 목록을 반환한다', () => {
      service.loadByClinic(1);
      const clinic1Ids = service.treatmentTypes().map(t => t.id);

      service.loadByClinic(2);
      const clinic2Ids = service.treatmentTypes().map(t => t.id);

      const intersection = clinic1Ids.filter(id => clinic2Ids.includes(id));
      expect(intersection).toHaveLength(0);
    });
  });
});
