import {
  getAuthSession
} from "@/lib/auth-storage";

import type {
  UserRole
} from "@/types/auth";

export type FrontendAccessErrorCode =
  | "AUTHENTICATION_REQUIRED"
  | "USER_ACCESS_REQUIRED"
  | "ADMIN_ACCESS_REQUIRED";

export class FrontendAccessError extends Error {
  readonly code: FrontendAccessErrorCode;
  readonly status: 401 | 403;

  constructor(
    code: FrontendAccessErrorCode,
    message: string,
    status: 401 | 403
  ) {
    super(message);

    this.name = "FrontendAccessError";
    this.code = code;
    this.status = status;
  }
}

function isKnownRole(
  value: unknown
): value is UserRole {
  return (
    value === "USER" ||
    value === "ADMIN"
  );
}

export function getCurrentRoles(): UserRole[] {
  // Đây là role check phía frontend để điều hướng/ẩn UI, không thay backend authority.
  const session = getAuthSession();

  if (!session) {
    return [];
  }

  const roles = session.user?.roles;

  if (!Array.isArray(roles)) {
    return [];
  }

  return roles.filter(isKnownRole);
}

export function isAuthenticated(): boolean {
  return getAuthSession() !== null;
}

export function hasRole(
  role: UserRole
): boolean {
  return getCurrentRoles().includes(role);
}

export function isAdmin(): boolean {
  return hasRole("ADMIN");
}

export function canAccessUserApi(): boolean {
  // USER flow accepts USER and ADMIN; backend Spring Security remains authoritative.
  const roles = getCurrentRoles();

  return (
    roles.includes("USER") ||
    roles.includes("ADMIN")
  );
}

export function canAccessAdminApi(): boolean {
  // Admin UI gating is only a UX check; every admin API still enforces ADMIN server-side.
  return isAdmin();
}

export function requireAuthenticated(): void {
  if (!isAuthenticated()) {
    throw new FrontendAccessError(
      "AUTHENTICATION_REQUIRED",
      "Authentication is required",
      401
    );
  }
}

export function requireUserAccess(): void {
  requireAuthenticated();

  if (!canAccessUserApi()) {
    throw new FrontendAccessError(
      "USER_ACCESS_REQUIRED",
      "USER or ADMIN role is required",
      403
    );
  }
}

export function requireAdminAccess(): void {
  // Some admin endpoints do not contain /admin in the URL but still require ADMIN.
  requireAuthenticated();

  if (!canAccessAdminApi()) {
    throw new FrontendAccessError(
      "ADMIN_ACCESS_REQUIRED",
      "ADMIN role is required",
      403
    );
  }
}

export function isFrontendAccessError(
  error: unknown
): error is FrontendAccessError {
  return error instanceof FrontendAccessError;
}