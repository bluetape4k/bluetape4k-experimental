import { Pipe, PipeTransform } from '@angular/core';

function toHHmm(time: string): string {
  // Accepts "HH:mm:ss" or "HH:mm"
  const parts = time.split(':');
  return `${parts[0]}:${parts[1]}`;
}

@Pipe({
  name: 'timeRange',
  standalone: true,
  pure: true,
})
export class TimeRangePipe implements PipeTransform {
  transform(slot: { startTime: string; endTime: string } | null | undefined): string {
    if (!slot?.startTime || !slot?.endTime) return '';
    return `${toHHmm(slot.startTime)} ~ ${toHHmm(slot.endTime)}`;
  }
}
