export type UserRole = "USER" | "ADMIN";

export type AuthUser = {
  id: string;
  email: string;
  displayName: string | null;
  roles: UserRole[];
  createdAt: string;
};

export type LoginRequest = {
  email: string;
  password: string;
};

export type RegisterRequest = {
  email: string;
  password: string;
  displayName: string;
};

export type RefreshRequest = {
  refreshToken: string;
};

export type LogoutRequest = {
  refreshToken: string;
};

export type AuthResponse = {
  tokenType: string;
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  user: AuthUser;
};

export type MeResponse = {
  authenticated: boolean;
  userId: string | null;
  email: string | null;
  roles: UserRole[] | null;
};