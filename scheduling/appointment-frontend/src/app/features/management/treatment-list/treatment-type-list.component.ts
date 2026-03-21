import { Component } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { TreatmentType } from '../../../core/models';

// All mock treatment types across all clinics for display
const ALL_TREATMENTS: TreatmentType[] = [
  { id: 1, clinicId: 1, name: '기본 진료', durationMinutes: 30 },
  { id: 2, clinicId: 1, name: '스케일링', durationMinutes: 60 },
  { id: 3, clinicId: 2, name: '예방 접종', durationMinutes: 15 },
];

const CLINIC_NAMES: Record<number, string> = {
  1: '서울 메인 클리닉',
  2: '부산 해운대 클리닉',
};

@Component({
  selector: 'app-treatment-type-list',
  standalone: true,
  imports: [MatTableModule, MatCardModule, MatIconModule],
  template: `
    <div class="page-container">
      <h1 class="page-title">진료 유형</h1>

      <mat-card>
        <mat-card-content>
          <table mat-table [dataSource]="treatments" class="full-width">
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>진료명</th>
              <td mat-cell *matCellDef="let t">{{ t.name }}</td>
            </ng-container>

            <ng-container matColumnDef="duration">
              <th mat-header-cell *matHeaderCellDef>기본 소요 시간</th>
              <td mat-cell *matCellDef="let t">{{ t.durationMinutes }}분</td>
            </ng-container>

            <ng-container matColumnDef="clinic">
              <th mat-header-cell *matHeaderCellDef>클리닉</th>
              <td mat-cell *matCellDef="let t">{{ clinicName(t.clinicId) }}</td>
            </ng-container>

            <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
            <tr mat-row *matRowDef="let row; columns: displayedColumns;"></tr>

            <tr class="mat-row" *matNoDataRow>
              <td class="mat-cell no-data" [attr.colspan]="displayedColumns.length">
                등록된 진료 유형이 없습니다.
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
export class TreatmentTypeListComponent {
  readonly displayedColumns = ['name', 'duration', 'clinic'];
  readonly treatments: TreatmentType[] = ALL_TREATMENTS;

  clinicName(clinicId: number): string {
    return CLINIC_NAMES[clinicId] ?? `클리닉 #${clinicId}`;
  }
}
