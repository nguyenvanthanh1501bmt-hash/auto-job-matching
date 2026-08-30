"use client";

import {
  syncLocaleInBrowserUrl,
  type AppLocale,
  useAppIntl
} from "@/components/providers/app-intl-provider";

const SUPPORTED_LOCALES = ["vi", "en"] as const;

type Props = {
  pathname?: string;
};

export function LanguageSwitcher({
  pathname
}: Props) {
  const {
    locale,
    changeLocale
  } = useAppIntl();

  function selectLocale(
    nextLocale: AppLocale
  ) {
    if (nextLocale === locale) {
      return;
    }

    /*
     * QUAN TRỌNG:
     *
     * Không dùng router.replace() ở đây.
     *
     * Nếu router.replace locale:
     * /vi/admin -> /en/admin
     *
     * Next sẽ thực hiện navigation và một phần tree
     * có thể remount, dẫn tới:
     * - scroll nhảy
     * - section reset
     * - details collapse
     * - local state reset
     *
     * Ở đây chỉ đổi messages trong provider.
     * Toàn bộ page hiện tại vẫn giữ nguyên DOM/state.
     */
    changeLocale(nextLocale);

    /*
     * URL vẫn đổi:
     *
     * /vi/admin
     *      ↓
     * /en/admin
     *
     * nhưng chỉ bằng History API, không navigation.
     */
    syncLocaleInBrowserUrl(
      nextLocale,
      pathname
    );
  }

  return (
    <div className="flex items-center rounded-full border border-black/[0.06] bg-white p-[3px] shadow-[0_2px_10px_rgba(0,0,0,0.035)]">
      {SUPPORTED_LOCALES.map((item) => {
        const active = locale === item;

        return (
          <button
            key={item}
            type="button"
            aria-pressed={active}
            onClick={() =>
              selectLocale(item)
            }
            className={`flex h-7 min-w-9 items-center justify-center rounded-full px-2 text-[10px] font-bold transition-colors duration-200 ${
              active
                ? "bg-[#171717] text-white"
                : "text-[#aaa] hover:text-[#444]"
            }`}
          >
            {item.toUpperCase()}
          </button>
        );
      })}
    </div>
  );
}