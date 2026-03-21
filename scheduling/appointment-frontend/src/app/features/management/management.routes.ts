import { Routes } from '@angular/router';

export const MANAGEMENT_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./dashboard/management-dashboard.component').then(
        m => m.ManagementDashboardComponent
      ),
  },
  {
    path: 'clinics',
    loadComponent: () =>
      import('./clinic-list/clinic-list.component').then(
        m => m.ClinicListComponent
      ),
  },
  {
    path: 'doctors',
    loadComponent: () =>
      import('./doctor-list/doctor-list.component').then(
        m => m.DoctorListComponent
      ),
  },
  {
    path: 'treatments',
    loadComponent: () =>
      import('./treatment-list/treatment-type-list.component').then(
        m => m.TreatmentTypeListComponent
      ),
  },
];
