"use client";

import {useLocale} from "next-intl";

import {usePathname, useRouter} from "@/i18n/navigation";

const SUPPORTED_LOCALES = ["vi", "en"] as const;

type AppLocale = (typeof SUPPORTED_LOCALES)[number];

type Props = {
  pathname?: string;
};

export function LanguageSwitcher({pathname}: Props) {
  const locale = useLocale();
  const router = useRouter();
  const currentPathname = usePathname();

  /*
   * Mặc định giữ nguyên page hiện tại khi đổi locale.
   * Auth có thể truyền pathname riêng vì login/register sync URL bằng History API.
   */
  const targetPathname = pathname ?? currentPathname;

  function changeLocale(nextLocale: AppLocale) {
    if (nextLocale === locale) {
      return;
    }

    router.replace(targetPathname, {
      locale: nextLocale
    });
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
            onClick={() => changeLocale(item)}
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