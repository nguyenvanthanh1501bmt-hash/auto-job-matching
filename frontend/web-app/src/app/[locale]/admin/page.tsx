"use client";

import {useEffect, useState} from "react";
import {useLocale, useTranslations} from "next-intl";

import {AdminDashboard} from "@/components/admin/admin-dashboard";
import {useRouter} from "@/i18n/navigation";
import {getAuthSession} from "@/lib/auth-storage";
import type {AdminAccessState} from "@/types/admin-ui";

function LoadingScreen() {
  const t = useTranslations("admin.access");

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#f7f7f4] text-[#171717]">
      <div className="flex items-center gap-3 text-[13px] font-medium text-[#777]">
        <span className="size-4 animate-spin rounded-full border-2 border-black/10 border-t-black/60" />

        {t("checking")}
      </div>
    </main>
  );
}

export default function AdminPage() {
  const router = useRouter();
  const locale = useLocale();

  /*
   * SSR và lần render đầu tiên ở browser bắt buộc phải cùng render LoadingScreen.
   * Auth session nằm trong browser storage nên chỉ được đọc sau khi component mount.
   */
  const [hydrated, setHydrated] = useState(false);
  const [accessState, setAccessState] =
    useState<AdminAccessState>("checking");

  const [adminName, setAdminName] =
    useState("Admin");

  useEffect(() => {
    const session = getAuthSession();

    if (!session) {
      setHydrated(true);
      setAccessState("redirecting");
      router.replace("/login");
      return;
    }

    if (!session.user.roles.includes("ADMIN")) {
      setHydrated(true);
      setAccessState("redirecting");
      router.replace("/jobs");
      return;
    }

    /*
     * Guard phía client chỉ phục vụ UX.
     * Backend vẫn phải kiểm tra quyền ADMIN cho mọi admin API.
     */
    setAdminName(
      session.user.displayName?.trim() ||
        session.user.email
    );

    setAccessState("allowed");
    setHydrated(true);
  }, [router]);

  if (!hydrated || accessState !== "allowed") {
    return <LoadingScreen />;
  }

  return (
    <AdminDashboard
      adminName={adminName}
      locale={locale}
    />
  );
}