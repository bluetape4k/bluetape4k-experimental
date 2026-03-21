import { Pipe, PipeTransform } from '@angular/core';
import { AppointmentStatus } from '../../core/models';

const STATUS_LABELS: Record<AppointmentStatus, string> = {
  [AppointmentStatus.REQUESTED]: '요청',
  [AppointmentStatus.CONFIRMED]: '확정',
  [AppointmentStatus.CHECKED_IN]: '체크인',
  [AppointmentStatus.IN_PROGRESS]: '진료중',
  [AppointmentStatus.COMPLETED]: '완료',
  [AppointmentStatus.CANCELLED]: '취소',
  [AppointmentStatus.NO_SHOW]: '미방문',
  [AppointmentStatus.PENDING_RESCHEDULE]: '재배정 대기',
  [AppointmentStatus.RESCHEDULED]: '재배정 완료',
};

@Pipe({
  name: 'statusLabel',
  standalone: true,
  pure: true,
})
export class StatusLabelPipe implements PipeTransform {
  transform(status: AppointmentStatus | string | null | undefined): string {
    if (!status) return '';
    return STATUS_LABELS[status as AppointmentStatus] ?? status;
  }
}
