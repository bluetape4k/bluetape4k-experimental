import { Component, inject, OnInit, computed } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { DoctorService } from '../../../core/services';
import { Doctor } from '../../../core/models';

// All mock doctors across all clinics for display
const ALL_DOCTORS: Doctor[] = [
  { id: 1, clinicId: 1, name: '김민준', specialty: '일반의' },
  { id: 2, clinicId: 1, name: '이서연', specialty: '치과' },
  { id: 3, clinicId: 2, name: '박지호', specialty: '소아과' },
];

const CLINIC_NAMES: Record<number, string> = {
  1: '서울 메인 클리닉',
  2: '부산 해운대 클리닉',
};

@Component({
  selector: 'app-doctor-list',
  standalone: true,
  imports: [MatTableModule, MatCardModule, MatIconModule],
  template: `
    <div class="page-container">
      <h1 class="page-title">의사 목록</h1>

      <mat-card>
        <mat-card-content>
          <table mat-table [dataSource]="doctors" class="full-width">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>이름</th>
              <td mat-cell *matCellDef="let doctor">{{ doctor.name }}</td>
            </ng-container>

            <ng-container matColumnDef="specialty">
              <th mat-header-cell *matHeaderCellDef>전문 분야</th>
              <td mat-cell *matCellDef="let doctor">{{ doctor.specialty ?? '—' }}</td>
            </ng-container>

            <ng-container matColumnDef="clinic">
              <th mat-header-cell *matHeaderCellDef>클리닉</th>
              <td mat-cell *matCellDef="let doctor">{{ clinicName(doctor.clinicId) }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>

            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell no-data" [attr.colspan]="displayedColumns.length">
                등록된 의사가 없습니다.
              </td>
            </tr>
          </table>
        </mat-card-content>
      </mat-card>
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

    .full-width {
      width: 100%;
    }

    .no-data {
      text-align: center;
      padding: 24px;
      color: #999;
    }

    th.mat-header-cell {
      font-weight: 600;
      color: #333;
    }
  `],
})
export class DoctorListComponent {
  readonly displayedColumns = ['name', 'specialty', 'clinic'];
  readonly doctors: Doctor[] = ALL_DOCTORS;

  clinicName(clinicId: number): string {
    return CLINIC_NAMES[clinicId] ?? `클리닉 #${clinicId}`;
  }
}
