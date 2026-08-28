import type {
  AuthResponse
} from "@/types/auth";

const AUTH_SESSION_STORAGE_KEY =
  "autojob.auth.session";

export const AUTH_SESSION_CHANGED_EVENT =
  "autojob:auth-session-changed";

/*
 * Cache theo raw string thay vì chỉ cache parsed object.
 * Cách này giúp nhận biết localStorage có thực sự thay đổi hay không.
 */
let cachedRawSession:
  | string
  | null = null;

let cachedSession:
  | AuthResponse
  | null = null;

function isBrowser(): boolean {
  return (
    typeof window !==
    "undefined"
  );
}

function notifyAuthSessionChanged(): void {
  if (!isBrowser()) {
    return;
  }

  window.dispatchEvent(
    new Event(
      AUTH_SESSION_CHANGED_EVENT
    )
  );
}

export function getAuthSession():
  | AuthResponse
  | null {
  // SSR không có localStorage nên session chỉ được đọc ở browser.
  if (!isBrowser()) {
    return null;
  }

  const rawSession =
    window.localStorage.getItem(
      AUTH_SESSION_STORAGE_KEY
    );

  if (!rawSession) {
    cachedRawSession =
      null;

    cachedSession =
      null;

    return null;
  }

  if (
    rawSession ===
    cachedRawSession
  ) {
    return cachedSession;
  }

  try {
    const parsedSession =
      JSON.parse(
        rawSession
      ) as AuthResponse;

    /*
     * Cache phải được cập nhật trước khi return.
     * Bản cũ return ngay sau JSON.parse nên phần cache phía sau
     * không bao giờ được thực thi.
     */
    cachedRawSession =
      rawSession;

    cachedSession =
      parsedSession;

    return parsedSession;
  } catch {
    /*
     * Session không parse được không nên tiếp tục tồn tại.
     * Nếu giữ lại, interceptor có thể liên tục gửi token lỗi.
     */
    window.localStorage.removeItem(
      AUTH_SESSION_STORAGE_KEY
    );

    cachedRawSession =
      null;

    cachedSession =
      null;

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

  const rawSession =
    JSON.stringify(
      session
    );

  window.localStorage.setItem(
    AUTH_SESSION_STORAGE_KEY,
    rawSession
  );

  /*
   * Đồng bộ cache ngay tại nguồn ghi để lần đọc tiếp theo
   * không cần parse lại cùng một JSON.
   */
  cachedRawSession =
    rawSession;

  cachedSession =
    session;

  notifyAuthSessionChanged();
}

export function clearAuthSession(): void {
  if (!isBrowser()) {
    return;
  }

  window.localStorage.removeItem(
    AUTH_SESSION_STORAGE_KEY
  );

  cachedRawSession =
    null;

  cachedSession =
    null;

  notifyAuthSessionChanged();
}

export function getAccessToken():
  | string
  | null {
  return (
    getAuthSession()
      ?.accessToken ??
    null
  );
}

export function getRefreshToken():
  | string
  | null {
  return (
    getAuthSession()
      ?.refreshToken ??
    null
  );
}

export function hasAuthSession(): boolean {
  return (
    getAuthSession() !==
    null
  );
}