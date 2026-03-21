import { TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { TimeSlotPickerComponent } from './time-slot-picker.component';
import { AvailableSlot } from '../../../core/models';

const mockSlots: AvailableSlot[] = [
  { date: '2025-01-01', startTime: '09:00:00', endTime: '09:30:00', doctorId: 1, equipmentIds: [], remainingCapacity: 3 },
  { date: '2025-01-01', startTime: '09:30:00', endTime: '10:00:00', doctorId: 1, equipmentIds: [], remainingCapacity: 2 },
];

describe('TimeSlotPickerComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TimeSlotPickerComponent],
      providers: [provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(TimeSlotPickerComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should display "가용 슬롯 없음" when slots are empty', () => {
    const fixture = TestBed.createComponent(TimeSlotPickerComponent);
    fixture.componentInstance.availableSlots = [];
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.textContent).toContain('가용 슬롯 없음');
  });

  it('should render slots as toggle buttons', () => {
    const fixture = TestBed.createComponent(TimeSlotPickerComponent);
    fixture.componentInstance.availableSlots = mockSlots;
    fixture.detectChanges();
    const buttons = fixture.nativeElement.querySelectorAll('mat-button-toggle');
    expect(buttons.length).toBe(2);
  });

  it('should emit slotSelected when a slot is selected', () => {
    const fixture = TestBed.createComponent(TimeSlotPickerComponent);
    fixture.componentInstance.availableSlots = mockSlots;
    fixture.detectChanges();

    let emittedSlot: AvailableSlot | undefined;
    fixture.componentInstance.slotSelected.subscribe((slot: AvailableSlot) => {
      emittedSlot = slot;
    });

    fixture.componentInstance.onSlotChange(mockSlots[0]);
    expect(emittedSlot).toEqual(mockSlots[0]);
  });
});
