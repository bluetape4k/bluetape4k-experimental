import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { AvailableSlot } from '../../../core/models';
import { TimeRangePipe } from '../../pipes/time-range.pipe';

@Component({
  selector: 'app-time-slot-picker',
  standalone: true,
  imports: [CommonModule, MatButtonToggleModule, TimeRangePipe],
  template: `
    <div class="slot-picker">
      @if (availableSlots && availableSlots.length > 0) {
        <mat-button-toggle-group class="slot-grid" (change)="onSlotChange($event.value)">
          @for (slot of availableSlots; track slot.startTime) {
            <mat-button-toggle [value]="slot">
              {{ slot | timeRange }}
            </mat-button-toggle>
          }
        </mat-button-toggle-group>
      } @else {
        <p class="no-slots">가용 슬롯 없음</p>
      }
    </div>
  `,
  styles: [`
    .slot-picker {
      width: 100%;
    }
    .slot-grid {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
    }
    .no-slots {
      color: #888;
      font-style: italic;
    }
  `],
})
export class TimeSlotPickerComponent {
  @Input() availableSlots: AvailableSlot[] = [];
  @Output() slotSelected = new EventEmitter<AvailableSlot>();

  onSlotChange(slot: AvailableSlot): void {
    if (slot) {
      this.slotSelected.emit(slot);
    }
  }
}
