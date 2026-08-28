"use client";

import {useTranslations} from "next-intl";

import {LogoutButton} from "@/components/ui/logout-button";
import {LanguageSwitcher} from "@/components/ui/language-switcher";
import {Link} from "@/i18n/navigation";
import type {AdminHeaderProps} from "@/types/admin-ui";

function AdminLogo() {
  const t = useTranslations("admin.header");

  return (
    <Link
      href="/admin"
      className="group inline-flex items-center gap-3"
    >
      <div className="relative flex size-9 items-center justify-center overflow-hidden rounded-[11px] bg-[#171717] text-white">
        <div className="absolute inset-x-0 bottom-0 h-px bg-[#d9ff75] opacity-0 transition-opacity duration-300 group-hover:opacity-100" />

        <svg
          viewBox="0 0 24 24"
          fill="none"
          className="size-[17px] transition-transform duration-300 ease-out group-hover:-rotate-3 group-hover:scale-105"
          aria-hidden="true"
        >
          <path
            d="M5 17.5 10.2 6.8c.7-1.4 2.7-1.4 3.4 0L19 17.5"
            stroke="currentColor"
            strokeWidth="2.15"
            strokeLinecap="round"
          />

          <path
            d="M7.8 13h8.4"
            stroke="currentColor"
            strokeWidth="2.15"
            strokeLinecap="round"
          />
        </svg>
      </div>

      <div>
        <p className="text-[18px] font-bold leading-none tracking-[-0.045em] text-[#171717]">
          AutoJob
        </p>

        <p className="mt-1 font-mono text-[8px] uppercase tracking-[0.15em] text-black/30">
          {t("console")}
        </p>
      </div>
    </Link>
  );
}

export function AdminHeader({
  adminName,
  locale
}: AdminHeaderProps) {
  const t = useTranslations("admin.header");

  return (
    <header className="sticky top-0 z-40 border-b border-black/[0.045] bg-[#f7f7f4]/90 backdrop-blur-xl">
      <div className="mx-auto flex h-[82px] max-w-[1440px] items-center justify-between px-5 sm:px-8 lg:px-12 xl:px-16">
        <AdminLogo />

        <div className="flex items-center gap-3">
          <LanguageSwitcher />

          <div className="hidden items-center gap-3 rounded-xl border border-black/[0.065] bg-white px-3.5 py-2 shadow-[0_2px_10px_rgba(0,0,0,0.025)] md:flex">
            <span className="relative flex size-2 shrink-0">
              <span className="absolute inset-0 animate-ping rounded-full bg-[#d9ff75] opacity-25" />
              <span className="relative size-2 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />
            </span>

            <div className="min-w-0">
              <p className="max-w-[190px] truncate text-[11px] font-semibold leading-4 text-[#343432]">
                {adminName}
              </p>

              <p className="font-mono text-[8px] uppercase tracking-[0.13em] text-[#aaa]">
                {t("access")} · {locale.toUpperCase()}
              </p>
            </div>
          </div>

          <LogoutButton />
        </div>
      </div>
    </header>
  );
}