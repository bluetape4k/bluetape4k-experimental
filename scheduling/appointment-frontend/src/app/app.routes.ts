import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'calendar', pathMatch: 'full' },
  {
    path: 'calendar',
    loadChildren: () => import('./features/calendar/calendar.routes').then(m => m.CALENDAR_ROUTES),
  },
  {
    path: 'appointments',
    loadChildren: () => import('./features/appointments/appointments.routes').then(m => m.APPOINTMENT_ROUTES),
  },
  {
    path: 'management',
    loadChildren: () => import('./features/management/management.routes').then(m => m.MANAGEMENT_ROUTES),
  },
];
