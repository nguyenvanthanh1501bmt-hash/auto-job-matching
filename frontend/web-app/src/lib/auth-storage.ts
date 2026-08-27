import type { AuthResponse } from "@/types/auth";

const AUTH_SESSION_STORAGE_KEY = "autojob.auth.session";

export const AUTH_SESSION_CHANGED_EVENT =
  "autojob:auth-session-changed";

// Cache để getAuthSession trả cùng reference khi localStorage chưa đổi.
let _cachedRaw: string | null = null;
let _cachedSession: AuthResponse | null = null;

function isBrowser(): boolean {
  return typeof window !== "undefined";
}

function notifyAuthSessionChanged(): void {
  if (!isBrowser()) {
    return;
  }

  window.dispatchEvent(
    new Event(AUTH_SESSION_CHANGED_EVENT)
  );
}

export function getAuthSession(): AuthResponse | null {
  // SSR không có localStorage; session chỉ được đọc ở browser.
  if (!isBrowser()) {
    return null;
  }

  const rawSession = window.localStorage.getItem(
    AUTH_SESSION_STORAGE_KEY
  );

  if (!rawSession) {
    _cachedRaw = null;
    _cachedSession = null;
    return null;
  }

  // Trả cached nếu raw string chưa đổi.
  if (rawSession === _cachedRaw) {
    return _cachedSession;
  }

  try {
    return JSON.parse(rawSession) as AuthResponse;
    _cachedSession = JSON.parse(rawSession) as AuthResponse;
    _cachedRaw = rawSession;
    return _cachedSession;
  } catch {
    // Session hỏng không được giữ lại để tránh auth state không hợp lệ.
    window.localStorage.removeItem(
      AUTH_SESSION_STORAGE_KEY
    );

    _cachedRaw = null;
    _cachedSession = null;
    notifyAuthSessionChanged();

    return null;
  }
}

export function setAuthSession(
  session: AuthResponse
): void {
  if (!isBrowser()) {
    return;
  }

  window.localStorage.setItem(
    AUTH_SESSION_STORAGE_KEY,
    JSON.stringify(session)
  );

  // Báo cho các phần UI đang theo dõi trạng thái đăng nhập.
  notifyAuthSessionChanged();
}

export function clearAuthSession(): void {
  if (!isBrowser()) {
    return;
  }

  window.localStorage.removeItem(
    AUTH_SESSION_STORAGE_KEY
  );

  // Logout hoặc refresh lỗi đều phải cập nhật UI ngay.
  notifyAuthSessionChanged();
}

export function getAccessToken(): string | null {
  return getAuthSession()?.accessToken ?? null;
}

export function getRefreshToken(): string | null {
  return getAuthSession()?.refreshToken ?? null;
}

export function hasAuthSession(): boolean {
  return getAuthSession() !== null;
}