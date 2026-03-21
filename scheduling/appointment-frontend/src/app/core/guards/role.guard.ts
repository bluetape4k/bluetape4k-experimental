import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const roleGuard: CanActivateFn = (route) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  const requiredRoles: string[] = route.data['requiredRoles'] ?? [];

  if (requiredRoles.length === 0) {
    return true;
  }

  const userRoles = authService.roles();
  const hasRole = requiredRoles.some((role) => userRoles.includes(role));

  if (hasRole) {
    return true;
  }

  return router.createUrlTree(['/calendar']);
};
