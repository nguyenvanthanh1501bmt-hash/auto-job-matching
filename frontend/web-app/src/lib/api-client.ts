import axios, {
  type AxiosError,
  type InternalAxiosRequestConfig
} from "axios";

import {
  clearAuthSession,
  getAccessToken,
  getRefreshToken,
  setAuthSession
} from "@/lib/auth-storage";

import type { AuthResponse } from "@/types/auth";

const baseURL =
  process.env.NEXT_PUBLIC_API_BASE_URL;

if (!baseURL) {
  throw new Error(
    "NEXT_PUBLIC_API_BASE_URL is not configured"
  );
}

const API_TIMEOUT_MS = 30_000;

type RetryableRequestConfig =
  InternalAxiosRequestConfig & {
    _retry?: boolean;
  };

export const publicClient = axios.create({
  // Dùng cho auth/public requests để không tự gắn Bearer token.
  baseURL,
  timeout: API_TIMEOUT_MS,
  headers: {
    Accept: "application/json"
  }
});

export const apiClient = axios.create({
  // Dùng cho protected requests; interceptor bên dưới sẽ gắn access token.
  baseURL,
  timeout: API_TIMEOUT_MS,
  headers: {
    Accept: "application/json"
  }
});

/**
 * Promise dùng chung khi access token hết hạn.
 *
 * Nếu 10 request cùng lúc nhận 401 thì chỉ có
 * 1 request refresh token được gửi lên backend.
 *
 * Các request còn lại sẽ chờ cùng promise này.
 */
let refreshPromise: Promise<AuthResponse> | null =
  null;

async function performTokenRefresh(): Promise<AuthResponse> {
  const refreshToken = getRefreshToken();

  if (!refreshToken) {
    clearAuthSession();

    throw new Error(
      "Refresh token is not available"
    );
  }

  try {
    // publicClient tránh để request refresh lại kích hoạt interceptor 401.
    const response =
      await publicClient.post<AuthResponse>(
        "/api/auth/refresh",
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

function refreshAuthSession(): Promise<AuthResponse> {
  // Một promise dùng chung để nhiều request 401 không refresh đồng thời.
  if (!refreshPromise) {
    refreshPromise = performTokenRefresh().finally(
      () => {
        refreshPromise = null;
      }
    );
  }

  return refreshPromise;
}

/**
 * Tự động gắn access token vào mọi protected request.
 *
 * Authorization: Bearer <accessToken>
 */
apiClient.interceptors.request.use(
  (config) => {
    const accessToken = getAccessToken();

    if (accessToken) {
      // Backend đọc token theo chuẩn Authorization Bearer.
      config.headers.set(
        "Authorization",
        `Bearer ${accessToken}`
      );
    }

    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

/**
 * Khi protected API trả về 401:
 *
 * 1. Lấy refresh token.
 * 2. Gọi POST /api/auth/refresh.
 * 3. Backend rotate refresh token.
 * 4. Lưu access + refresh token mới.
 * 5. Retry request ban đầu.
 *
 * _retry ngăn request lặp vô hạn.
 */
apiClient.interceptors.response.use(
  (response) => response,

  async (error: AxiosError) => {
    const originalRequest =
      error.config as
        | RetryableRequestConfig
        | undefined;

    const status = error.response?.status;

    if (
      status !== 401 ||
      !originalRequest ||
      originalRequest._retry
    ) {
      return Promise.reject(error);
    }

    const refreshToken = getRefreshToken();

    if (!refreshToken) {
      clearAuthSession();

      return Promise.reject(error);
    }

    originalRequest._retry = true;

    try {
      // Refresh token được rotate và lưu trước khi retry request ban đầu.
      const session =
        await refreshAuthSession();

      const tokenType =
        session.tokenType || "Bearer";

      originalRequest.headers.set(
        "Authorization",
        `${tokenType} ${session.accessToken}`
      );

      return apiClient(originalRequest);
    } catch (refreshError) {
      return Promise.reject(refreshError);
    }
  }
);