import { TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { StatusBadgeComponent } from './status-badge.component';
import { AppointmentStatus } from '../../../core/models';

describe('StatusBadgeComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [StatusBadgeComponent],
      providers: [provideAnimations()],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentInstance.status = AppointmentStatus.REQUESTED;
    fixture.detectChanges();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should display 요청 for REQUESTED status', () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentInstance.status = AppointmentStatus.REQUESTED;
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.status-badge')?.textContent?.trim()).toBe('요청');
  });

  it('should display 확정 for CONFIRMED status', () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentInstance.status = AppointmentStatus.CONFIRMED;
    fixture.detectChanges();
    const el: HTMLElement = fixture.nativeElement;
    expect(el.querySelector('.status-badge')?.textContent?.trim()).toBe('확정');
  });

  it('should apply correct CSS class for REQUESTED', () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentInstance.status = AppointmentStatus.REQUESTED;
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.status-badge');
    expect(badge?.classList).toContain('status-requested');
  });

  it('should apply correct CSS class for IN_PROGRESS', () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentInstance.status = AppointmentStatus.IN_PROGRESS;
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.status-badge');
    expect(badge?.classList).toContain('status-in-progress');
  });

  it('should apply correct CSS class for PENDING_RESCHEDULE', () => {
    const fixture = TestBed.createComponent(StatusBadgeComponent);
    fixture.componentInstance.status = AppointmentStatus.PENDING_RESCHEDULE;
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('.status-badge');
    expect(badge?.classList).toContain('status-pending-reschedule');
  });
});
