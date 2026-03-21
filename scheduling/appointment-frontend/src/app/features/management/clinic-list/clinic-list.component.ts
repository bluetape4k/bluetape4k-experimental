import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

interface ClinicDisplay {
  id: number;
  name: string;
  phone: string;
  address: string;
  slotDurationMinutes: number;
}

const MOCK_CLINICS: ClinicDisplay[] = [
  {
    id: 1,
    name: '서울 메인 클리닉',
    phone: '02-1234-5678',
    address: '서울특별시 강남구 테헤란로 123',
    slotDurationMinutes: 30,
  },
  {
    id: 2,
    name: '부산 해운대 클리닉',
    phone: '051-9876-5432',
    address: '부산광역시 해운대구 해운대로 456',
    slotDurationMinutes: 20,
  },
];

@Component({
  selector: 'app-clinic-list',
  standalone: true,
  imports: [MatCardModule, MatIconModule],
  template: `
    <div class="page-container">
      <h1 class="page-title">클리닉 정보</h1>

      <div class="clinic-grid">
        @for (clinic of clinics; track clinic.id) {
          <mat-card class="clinic-card">
            <mat-card-header>
              <mat-icon mat-card-avatar>local_hospital</mat-icon>
              <mat-card-title>{{ clinic.name }}</mat-card-title>
              <mat-card-subtitle>클리닉 #{{ clinic.id }}</mat-card-subtitle>
            </mat-card-header>
            <mat-card-content>
              <ul class="info-list">
                <li>
                  <mat-icon>phone</mat-icon>
                  <span>{{ clinic.phone }}</span>
                </li>
                <li>
                  <mat-icon>location_on</mat-icon>
                  <span>{{ clinic.address }}</span>
                </li>
                <li>
                  <mat-icon>schedule</mat-icon>
                  <span>슬롯 단위: {{ clinic.slotDurationMinutes }}분</span>
                </li>
              </ul>
            </mat-card-content>
          </mat-card>
        }
      </div>
    </div>
  `,
  styles: [`
    .page-container {
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

    .clinic-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 16px;
    }

    .clinic-card mat-card-content {
      padding: 16px;
    }

    .info-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .info-list li {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 0.9rem;
      color: #333;
    }

    .info-list mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
      color: #1976d2;
    }
  `],
})
export class ClinicListComponent {
  readonly clinics: ClinicDisplay[] = MOCK_CLINICS;
}
