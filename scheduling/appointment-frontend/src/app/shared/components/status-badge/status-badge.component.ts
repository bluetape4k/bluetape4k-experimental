import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AppointmentStatus } from '../../../core/models';
import { StatusLabelPipe } from '../../pipes/status-label.pipe';

@Component({
  selector: 'app-status-badge',
  standalone: true,
  imports: [CommonModule, StatusLabelPipe],
  template: `
    <span class="status-badge" [ngClass]="statusClass">
      {{ status | statusLabel }}
    </span>
  `,
  styles: [`
    .status-badge {
      display: inline-block;
      padding: 2px 10px;
      border-radius: 12px;
      font-size: 0.8rem;
      font-weight: 500;
      color: #fff;
    }
    .status-requested    { background-color: var(--status-requested); }
    .status-confirmed    { background-color: var(--status-confirmed); }
    .status-checked-in   { background-color: var(--status-checked-in); }
    .status-in-progress  { background-color: var(--status-in-progress); }
    .status-completed    { background-color: var(--status-completed); }
    .status-cancelled    { background-color: var(--status-cancelled); }
    .status-no-show      { background-color: var(--status-no-show); }
    .status-pending-reschedule { background-color: var(--status-pending-reschedule); }
    .status-rescheduled  { background-color: var(--status-rescheduled); }
  `],
})
export class StatusBadgeComponent {
  @Input() status!: AppointmentStatus;

  get statusClass(): string {
    if (!this.status) return '';
    return 'status-' + this.status.toLowerCase().replace(/_/g, '-');
  }
}
