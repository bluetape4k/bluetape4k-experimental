import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';

import { CalendarStateService, ViewMode } from './calendar-state.service';

/** Helper: create a specific date at midnight */
function date(y: number, m: number, d: number): Date {
  return new Date(y, m - 1, d, 0, 0, 0, 0);
}

describe('CalendarStateService', () => {
  let service: CalendarStateService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(CalendarStateService);
  });

  it('서비스가 생성된다', () => {
    expect(service).toBeTruthy();
  });

  it('초기 viewMode는 week이다', () => {
    expect(service.viewMode()).toBe('week');
  });

  describe('goToToday()', () => {
    it('currentDate를 오늘 날짜로 재설정한다', () => {
      service.currentDate.set(date(2020, 1, 1));
      service.goToToday();
      const today = new Date();
      expect(service.currentDate().toDateString()).toBe(today.toDateString());
    });
  });

  describe('navigateNext()', () => {
    it('day 모드에서 1일 앞으로 이동한다', () => {
      service.viewMode.set('day');
      service.currentDate.set(date(2025, 6, 10));
      service.navigateNext();
      expect(service.currentDate().getDate()).toBe(11);
    });

    it('week 모드에서 7일 앞으로 이동한다', () => {
      service.viewMode.set('week');
      service.currentDate.set(date(2025, 6, 10));
      service.navigateNext();
      expect(service.currentDate().getDate()).toBe(17);
    });

    it('month 모드에서 1달 앞으로 이동한다', () => {
      service.viewMode.set('month');
      service.currentDate.set(date(2025, 6, 10));
      service.navigateNext();
      expect(service.currentDate().getMonth()).toBe(6); // July (0-indexed)
    });
  });

  describe('navigatePrev()', () => {
    it('day 모드에서 1일 뒤로 이동한다', () => {
      service.viewMode.set('day');
      service.currentDate.set(date(2025, 6, 10));
      service.navigatePrev();
      expect(service.currentDate().getDate()).toBe(9);
    });

    it('week 모드에서 7일 뒤로 이동한다', () => {
      service.viewMode.set('week');
      service.currentDate.set(date(2025, 6, 10));
      service.navigatePrev();
      expect(service.currentDate().getDate()).toBe(3);
    });

    it('month 모드에서 1달 뒤로 이동한다', () => {
      service.viewMode.set('month');
      service.currentDate.set(date(2025, 6, 10));
      service.navigatePrev();
      expect(service.currentDate().getMonth()).toBe(4); // May (0-indexed)
    });
  });

  describe('dateRange computed', () => {
    describe('day 모드', () => {
      it('start는 해당 날 자정, end는 23:59:59이다', () => {
        service.viewMode.set('day');
        service.currentDate.set(date(2025, 6, 15));
        const range = service.dateRange();
        expect(range.start.getDate()).toBe(15);
        expect(range.start.getHours()).toBe(0);
        expect(range.end.getDate()).toBe(15);
        expect(range.end.getHours()).toBe(23);
      });
    });

    describe('week 모드', () => {
      it('start는 해당 주 월요일, end는 일요일이다', () => {
        service.viewMode.set('week');
        // 2025-06-11 is a Wednesday
        service.currentDate.set(date(2025, 6, 11));
        const range = service.dateRange();
        // Monday = 2025-06-09
        expect(range.start.getDay()).toBe(1); // Monday
        expect(range.start.getDate()).toBe(9);
        // Sunday = 2025-06-15
        expect(range.end.getDay()).toBe(0); // Sunday
        expect(range.end.getDate()).toBe(15);
      });

      it('일요일을 기준으로 하면 전주 월요일로 이동한다', () => {
        service.viewMode.set('week');
        // 2025-06-15 is a Sunday
        service.currentDate.set(date(2025, 6, 15));
        const range = service.dateRange();
        expect(range.start.getDay()).toBe(1); // Monday
        expect(range.start.getDate()).toBe(9);
      });

      it('week range의 start가 월요일(weekday=1)이다 — 주는 월요일 시작', () => {
        service.viewMode.set('week');
        service.currentDate.set(date(2025, 6, 10)); // Tuesday
        const range = service.dateRange();
        expect(range.start.getDay()).toBe(1);
      });
    });

    describe('month 모드', () => {
      it('start는 해당 월 1일, end는 마지막 날이다', () => {
        service.viewMode.set('month');
        service.currentDate.set(date(2025, 6, 15));
        const range = service.dateRange();
        expect(range.start.getDate()).toBe(1);
        expect(range.start.getMonth()).toBe(5); // June (0-indexed)
        expect(range.end.getDate()).toBe(30); // June has 30 days
        expect(range.end.getMonth()).toBe(5);
      });

      it('2월의 마지막 날을 올바르게 계산한다 (윤년)', () => {
        service.viewMode.set('month');
        service.currentDate.set(date(2024, 2, 1)); // Feb 2024 (leap year)
        const range = service.dateRange();
        expect(range.end.getDate()).toBe(29);
      });
    });
  });
});
