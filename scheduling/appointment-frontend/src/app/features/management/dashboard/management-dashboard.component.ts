import { Component, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { AppointmentService, DoctorService } from '../../../core/services';

@Component({
  selector: 'app-management-dashboard',
  standalone: true,
  imports: [RouterLink, MatCardModule, MatIconModule, MatButtonModule],
  template: `
    <div class="dashboard-container">
      <h1 class="page-title">관리 대시보드</h1>

      <section class="stats-grid">
        <mat-card class="stat-card">
          <mat-card-content>
            <div class="stat-icon">
              <mat-icon>event</mat-icon>
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ todayCount() }}</span>
              <span class="stat-label">오늘 예약</span>
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-content>
            <div class="stat-icon pending">
              <mat-icon>pending_actions</mat-icon>
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ pendingCount() }}</span>
              <span class="stat-label">확인 대기</span>
            </div>
          </mat-card-content>
        </mat-card>

        <mat-card class="stat-card">
          <mat-card-content>
            <div class="stat-icon doctors">
              <mat-icon>medical_services</mat-icon>
            </div>
            <div class="stat-info">
              <span class="stat-value">{{ doctorCount() }}</span>
              <span class="stat-label">등록 의사</span>
            </div>
          </mat-card-content>
        </mat-card>
      </section>

      <h2 class="section-title">빠른 이동</h2>

      <section class="nav-grid">
        <mat-card class="nav-card" routerLink="/management/clinics">
          <mat-card-content>
            <mat-icon class="nav-icon">local_hospital</mat-icon>
            <h3>클리닉 정보</h3>
            <p>클리닉 목록 및 상세 정보를 확인합니다.</p>
          </mat-card-content>
          <mat-card-actions>
            <button mat-button color="primary" routerLink="/management/clinics">바로가기</button>
          </mat-card-actions>
        </mat-card>

        <mat-card class="nav-card" routerLink="/management/doctors">
          <mat-card-content>
            <mat-icon class="nav-icon">people</mat-icon>
            <h3>의사 목록</h3>
            <p>등록된 의사 정보를 확인합니다.</p>
          </mat-card-content>
          <mat-card-actions>
            <button mat-button color="primary" routerLink="/management/doctors">바로가기</button>
          </mat-card-actions>
        </mat-card>

        <mat-card class="nav-card" routerLink="/management/treatments">
          <mat-card-content>
            <mat-icon class="nav-icon">healing</mat-icon>
            <h3>진료 유형</h3>
            <p>진료 유형 및 소요 시간을 확인합니다.</p>
          </mat-card-content>
          <mat-card-actions>
            <button mat-button color="primary" routerLink="/management/treatments">바로가기</button>
          </mat-card-actions>
        </mat-card>
      </section>
    </div>
  `,
  styles: [`
    .dashboard-container {
      padding: 24px;
      max-width: 1200px;
      margin: 0 auto;
    }

    .page-title {
      font-size: 1.75rem;
      font-weight: 600;
      margin-bottom: 24px;
      color: #1a1a1a;
    }

    .section-title {
      font-size: 1.25rem;
      font-weight: 500;
      margin: 32px 0 16px;
      color: #333;
    }

    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
    }

    .stat-card mat-card-content {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 16px;
    }

    .stat-icon {
      width: 48px;
      height: 48px;
      border-radius: 12px;
      background: #e3f2fd;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #1976d2;
    }

    .stat-icon.pending {
      background: #fff3e0;
      color: #f57c00;
    }

    .stat-icon.doctors {
      background: #e8f5e9;
      color: #388e3c;
    }

    .stat-info {
      display: flex;
      flex-direction: column;
    }

    .stat-value {
      font-size: 2rem;
      font-weight: 700;
      line-height: 1;
      color: #1a1a1a;
    }

    .stat-label {
      font-size: 0.875rem;
      color: #666;
      margin-top: 4px;
    }

    .nav-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 16px;
    }

    .nav-card {
      cursor: pointer;
      transition: box-shadow 0.2s;
    }

    .nav-card:hover {
      box-shadow: 0 4px 16px rgba(0,0,0,0.12);
    }

    .nav-card mat-card-content {
      display: flex;
      flex-direction: column;
      align-items: flex-start;
      padding: 16px;
    }

    .nav-icon {
      font-size: 36px;
      width: 36px;
      height: 36px;
      color: #1976d2;
      margin-bottom: 12px;
    }

    .nav-card h3 {
      margin: 0 0 8px;
      font-size: 1.1rem;
      font-weight: 600;
    }

    .nav-card p {
      margin: 0;
      font-size: 0.875rem;
      color: #666;
    }
  `],
})
export class ManagementDashboardComponent implements OnInit {
  private readonly appointmentService = inject(AppointmentService);
  private readonly doctorService = inject(DoctorService);

  readonly todayCount = signal(0);
  readonly pendingCount = signal(0);
  readonly doctorCount = signal(0);

  ngOnInit(): void {
    // Load doctors for clinic 1 as a representative count
    this.doctorService.loadByClinic(1);
    this.doctorCount.set(this.doctorService.doctors().length);

    // Stats from existing appointments signal
    const appointments = this.appointmentService.appointments();
    const today = new Date().toISOString().slice(0, 10);
    const todayAppts = appointments.filter(a => a.appointmentDate === today);
    this.todayCount.set(todayAppts.length);
    this.pendingCount.set(
      todayAppts.filter(a => a.status === 'REQUESTED').length
    );
  }
}
