import { StatusLabelPipe } from './status-label.pipe';
import { AppointmentStatus } from '../../core/models';

describe('StatusLabelPipe', () => {
  let pipe: StatusLabelPipe;

  beforeEach(() => {
    pipe = new StatusLabelPipe();
  });

  it('should transform REQUESTED to 요청', () => {
    expect(pipe.transform(AppointmentStatus.REQUESTED)).toBe('요청');
  });

  it('should transform CONFIRMED to 확정', () => {
    expect(pipe.transform(AppointmentStatus.CONFIRMED)).toBe('확정');
  });

  it('should transform CHECKED_IN to 체크인', () => {
    expect(pipe.transform(AppointmentStatus.CHECKED_IN)).toBe('체크인');
  });

  it('should transform IN_PROGRESS to 진료중', () => {
    expect(pipe.transform(AppointmentStatus.IN_PROGRESS)).toBe('진료중');
  });

  it('should transform COMPLETED to 완료', () => {
    expect(pipe.transform(AppointmentStatus.COMPLETED)).toBe('완료');
  });

  it('should transform CANCELLED to 취소', () => {
    expect(pipe.transform(AppointmentStatus.CANCELLED)).toBe('취소');
  });

  it('should transform NO_SHOW to 미방문', () => {
    expect(pipe.transform(AppointmentStatus.NO_SHOW)).toBe('미방문');
  });

  it('should transform PENDING_RESCHEDULE to 재배정 대기', () => {
    expect(pipe.transform(AppointmentStatus.PENDING_RESCHEDULE)).toBe('재배정 대기');
  });

  it('should transform RESCHEDULED to 재배정 완료', () => {
    expect(pipe.transform(AppointmentStatus.RESCHEDULED)).toBe('재배정 완료');
  });

  it('should return empty string for null', () => {
    expect(pipe.transform(null)).toBe('');
  });

  it('should return empty string for undefined', () => {
    expect(pipe.transform(undefined)).toBe('');
  });
});
