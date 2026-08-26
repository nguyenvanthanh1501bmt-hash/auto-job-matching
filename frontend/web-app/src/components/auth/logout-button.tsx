"use client";

import {useState} from "react";
import {useLocale} from "next-intl";

import {useRouter} from "@/i18n/navigation";
import {authService} from "@/services/auth.service";

export function LogoutButton() {
  const router = useRouter();
  const locale = useLocale();

  const [loading, setLoading] =
    useState(false);

  async function logout() {
    if (loading) {
      return;
    }

    setLoading(true);

    try {
      await authService.logout();
    } catch {
      /*
       * authService.logout() đã clear local session
       * trong finally, nên kể cả backend lỗi
       * vẫn cho user thoát khỏi app.
       */
    } finally {
      router.replace("/login");
      router.refresh();
    }
  }

  const label =
    locale === "vi"
      ? "Đăng xuất"
      : "Log out";

  const loadingLabel =
    locale === "vi"
      ? "Đang đăng xuất..."
      : "Logging out...";

  return (
    <button
      type="button"
      onClick={logout}
      disabled={loading}
      className="group inline-flex h-10 items-center gap-2.5 rounded-xl border border-black/[0.08] bg-white px-4 text-[13px] font-semibold text-[#333] shadow-[0_1px_3px_rgba(0,0,0,0.03)] transition-all duration-200 hover:border-black/[0.14] hover:bg-[#f7f7f5] hover:text-black disabled:cursor-not-allowed disabled:opacity-50"
    >
      {loading ? (
        <>
          <span className="size-3.5 animate-spin rounded-full border-2 border-black/15 border-t-black/70" />

          {loadingLabel}
        </>
      ) : (
        <>
          <svg
            viewBox="0 0 24 24"
            fill="none"
            className="size-4 text-[#777] transition-colors group-hover:text-[#222]"
            stroke="currentColor"
            strokeWidth="1.8"
            strokeLinecap="round"
            strokeLinejoin="round"
            aria-hidden="true"
          >
            <path d="M10 5H6.8A1.8 1.8 0 0 0 5 6.8v10.4A1.8 1.8 0 0 0 6.8 19H10" />

            <path d="M14 8l4 4-4 4" />

            <path d="M9 12h9" />
          </svg>

          {label}
        </>
      )}
    </button>
  );
}