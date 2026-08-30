"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState
} from "react";
import {NextIntlClientProvider} from "next-intl";

import enMessages from "../../../messages/en.json";
import viMessages from "../../../messages/vi.json";

const SUPPORTED_LOCALES = ["vi", "en"] as const;

export type AppLocale =
  (typeof SUPPORTED_LOCALES)[number];

type AppIntlContextValue = {
  locale: AppLocale;
  changeLocale: (locale: AppLocale) => void;
};

type Props = {
  initialLocale: string;
  children: React.ReactNode;
};

const AppIntlContext =
  createContext<AppIntlContextValue | null>(null);

function isAppLocale(
  value: string
): value is AppLocale {
  return SUPPORTED_LOCALES.includes(
    value as AppLocale
  );
}

function replaceLocalePrefix(
  pathname: string,
  locale: AppLocale
) {
  const normalizedPathname =
    pathname.startsWith("/")
      ? pathname
      : `/${pathname}`;

  const segments =
    normalizedPathname.split("/");

  if (
    segments.length > 1 &&
    isAppLocale(segments[1] ?? "")
  ) {
    segments[1] = locale;

    return segments.join("/") || `/${locale}`;
  }

  return `/${locale}${
    normalizedPathname === "/"
      ? ""
      : normalizedPathname
  }`;
}

export function AppIntlProvider({
  initialLocale,
  children
}: Props) {
  const [locale, setLocale] =
    useState<AppLocale>(() =>
      isAppLocale(initialLocale)
        ? initialLocale
        : "vi"
    );

  /*
   * Nếu user thực sự navigate sang một route locale khác
   * bằng Next Router thì provider vẫn sync lại theo route.
   *
   * Khi chỉ bấm VI / EN thì initialLocale không đổi,
   * vì LanguageSwitcher không navigate.
   */
  useEffect(() => {
    if (
      isAppLocale(initialLocale) &&
      initialLocale !== locale
    ) {
      setLocale(initialLocale);
    }
  }, [initialLocale]);

  const changeLocale = useCallback(
    (nextLocale: AppLocale) => {
      setLocale(nextLocale);
    },
    []
  );

  const messages =
    locale === "en"
      ? enMessages
      : viMessages;

  const contextValue = useMemo(
    () => ({
      locale,
      changeLocale
    }),
    [locale, changeLocale]
  );

  return (
    <AppIntlContext.Provider
      value={contextValue}
    >
      <NextIntlClientProvider
        locale={locale}
        messages={messages}
      >
        {children}
      </NextIntlClientProvider>
    </AppIntlContext.Provider>
  );
}

export function useAppIntl() {
  const context = useContext(AppIntlContext);

  if (!context) {
    throw new Error(
      "useAppIntl must be used inside AppIntlProvider"
    );
  }

  return context;
}

export function syncLocaleInBrowserUrl(
  locale: AppLocale,
  pathname?: string
) {
  try {
    const url = new URL(
      window.location.href
    );

    const nextPathname =
      pathname
        ? replaceLocalePrefix(
            pathname,
            locale
          )
        : replaceLocalePrefix(
            url.pathname,
            locale
          );

    /*
     * Chỉ đổi URL trên address bar.
     * Không navigate, không remount component,
     * không reset state và không đổi scroll.
     */
    window.history.replaceState(
      window.history.state,
      "",
      `${nextPathname}${url.search}${url.hash}`
    );

    document.documentElement.lang = locale;
  } catch {
    /*
     * Nếu browser URL sync lỗi thì translation
     * vẫn được đổi bình thường.
     */
  }
}