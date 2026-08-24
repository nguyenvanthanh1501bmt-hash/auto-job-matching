import {
  apiClient,
  publicClient
} from "@/lib/api-client";

import {
  clearAuthSession,
  getRefreshToken,
  setAuthSession
} from "@/lib/auth-storage";

import type {
  AuthResponse,
  LoginRequest,
  MeResponse,
  RegisterRequest
} from "@/types/auth";

const AUTH_BASE_PATH = "/api/auth";

async function register(
  payload: RegisterRequest
): Promise<AuthResponse> {
  // Public auth response chứa session mới để lưu cho các protected request.
  const response =
    await publicClient.post<AuthResponse>(
      `${AUTH_BASE_PATH}/register`,
      payload
    );

  setAuthSession(response.data);

  return response.data;
}

async function login(
  payload: LoginRequest
): Promise<AuthResponse> {
  // Login dùng publicClient vì chưa có access token để gửi.
  const response =
    await publicClient.post<AuthResponse>(
      `${AUTH_BASE_PATH}/login`,
      payload
    );

  setAuthSession(response.data);

  return response.data;
}

async function refresh(): Promise<AuthResponse> {
  // Đây là refresh chủ động; interceptor có cơ chế single-flight riêng cho 401.
  const refreshToken = getRefreshToken();

  if (!refreshToken) {
    clearAuthSession();

    throw new Error(
      "Refresh token is not available"
    );
  }

  try {
    const response =
      await publicClient.post<AuthResponse>(
        `${AUTH_BASE_PATH}/refresh`,
        {
          refreshToken
        }
      );

    setAuthSession(response.data);

    return response.data;
  } catch (error) {
    clearAuthSession();

    throw error;
  }
}

async function logout(): Promise<void> {
  const refreshToken = getRefreshToken();

  try {
    if (refreshToken) {
      await publicClient.post(
        `${AUTH_BASE_PATH}/logout`,
        {
          refreshToken
        }
      );
    }
  } finally {
    /**
     * Kể cả backend đang down hoặc request logout fail,
     * browser vẫn phải xóa local session.
     */
    clearAuthSession();
  }
}

async function me(): Promise<MeResponse> {
  // /me dùng apiClient vì đây là endpoint protected và cần Bearer token.
  const response =
    await apiClient.get<MeResponse>(
      `${AUTH_BASE_PATH}/me`
    );

  return response.data;
}

export const authService = {
  register,
  login,
  refresh,
  logout,
  me
};