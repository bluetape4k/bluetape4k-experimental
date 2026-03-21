import { Routes } from '@angular/router';
import { roleGuard } from '../../core/guards/role.guard';
import { AppointmentListComponent } from './appointment-list/appointment-list.component';
import { AppointmentDetailComponent } from './appointment-detail/appointment-detail.component';
import { AppointmentFormComponent } from './appointment-form/appointment-form.component';

export const APPOINTMENT_ROUTES: Routes = [
  { path: '', component: AppointmentListComponent },
  {
    path: 'new',
    component: AppointmentFormComponent,
    canActivate: [roleGuard],
    data: { requiredRoles: ['ROLE_ADMIN', 'ROLE_STAFF'] },
  },
  { path: ':id', component: AppointmentDetailComponent },
  {
    path: ':id/edit',
    component: AppointmentFormComponent,
    canActivate: [roleGuard],
    data: { requiredRoles: ['ROLE_ADMIN', 'ROLE_STAFF'] },
  },
];
