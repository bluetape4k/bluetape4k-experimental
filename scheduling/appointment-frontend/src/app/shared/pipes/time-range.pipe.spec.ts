import { TimeRangePipe } from './time-range.pipe';

describe('TimeRangePipe', () => {
  let pipe: TimeRangePipe;

  beforeEach(() => {
    pipe = new TimeRangePipe();
  });

  it('should format HH:mm:ss times to HH:mm ~ HH:mm', () => {
    expect(pipe.transform({ startTime: '09:00:00', endTime: '09:30:00' })).toBe('09:00 ~ 09:30');
  });

  it('should format HH:mm times to HH:mm ~ HH:mm', () => {
    expect(pipe.transform({ startTime: '14:00', endTime: '14:45' })).toBe('14:00 ~ 14:45');
  });

  it('should return empty string for null', () => {
    expect(pipe.transform(null)).toBe('');
  });

  it('should return empty string for undefined', () => {
    expect(pipe.transform(undefined)).toBe('');
  });

  it('should return empty string when startTime is missing', () => {
    expect(pipe.transform({ startTime: '', endTime: '09:30:00' })).toBe('');
  });
});
