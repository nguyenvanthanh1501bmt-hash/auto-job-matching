"use client";

import {
  useState,
  type ReactNode
} from "react";
import {
  useLocale,
  useTranslations
} from "next-intl";

import {Link} from "@/i18n/navigation";

type AuthShellProps = {
  children: ReactNode;
  mode: "login" | "register";
};

type AuthPasswordFieldProps = {
  id: string;
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  placeholder?: string;
  autoComplete:
    | "current-password"
    | "new-password";
  disabled?: boolean;
  hint?: string;
};

function Logo() {
  const t = useTranslations("app");

  return (
    <Link
      href="/"
      className="inline-flex items-center gap-3"
    >
      <div className="flex size-10 items-center justify-center rounded-xl bg-[#111111] text-white">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          className="size-5"
          aria-hidden="true"
        >
          <path
            d="M5 17.5 10.2 6.8c.7-1.4 2.7-1.4 3.4 0L19 17.5"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
          />

          <path
            d="M7.8 13h8.4"
            stroke="currentColor"
            strokeWidth="2.2"
            strokeLinecap="round"
          />
        </svg>
      </div>

      <span className="text-[19px] font-bold tracking-[-0.04em] text-[#151515]">
        {t("name")}
      </span>
    </Link>
  );
}

export function AuthShell({
  children,
  mode
}: AuthShellProps) {
  const t = useTranslations("auth");
  const commonT =
    useTranslations("common");
  const locale = useLocale();

  const currentPath =
    mode === "login"
      ? "/login"
      : "/register";

  const switchPath =
    mode === "login"
      ? "/register"
      : "/login";

  const shellKey =
    mode === "login"
      ? "shell.login"
      : "shell.register";

  return (
    <main className="min-h-screen bg-[#f7f7f5] text-[#171717]">
      <div className="mx-auto flex min-h-screen max-w-[1440px] flex-col">
        <header className="flex h-20 items-center justify-between px-6 sm:px-10 lg:px-16">
          <Logo />

          <div
            aria-label={commonT(
              "language"
            )}
            className="flex items-center gap-2 rounded-full border border-black/[0.08] bg-white p-1 shadow-[0_1px_2px_rgba(0,0,0,0.03)]"
          >
            <Link
              href={currentPath}
              locale="vi"
              className={`rounded-full px-3 py-1.5 text-xs font-semibold transition ${
                locale === "vi"
                  ? "bg-[#171717] text-white"
                  : "text-[#777] hover:text-[#171717]"
              }`}
            >
              VI
            </Link>

            <Link
              href={currentPath}
              locale="en"
              className={`rounded-full px-3 py-1.5 text-xs font-semibold transition ${
                locale === "en"
                  ? "bg-[#171717] text-white"
                  : "text-[#777] hover:text-[#171717]"
              }`}
            >
              EN
            </Link>
          </div>
        </header>

        <div className="grid flex-1 lg:grid-cols-[1fr_560px]">
          <section className="hidden items-center px-16 pb-24 lg:flex xl:px-24">
            <div className="max-w-[610px]">
              <div className="mb-8 flex items-center gap-3">
                <span className="h-px w-8 bg-[#171717]" />

                <span className="text-xs font-bold uppercase tracking-[0.18em] text-[#6d6d68]">
                  {t("shell.badge")}
                </span>
              </div>

              <h1 className="max-w-[600px] text-[56px] font-semibold leading-[1.02] tracking-[-0.055em] text-[#171717] xl:text-[68px]">
                {t(`${shellKey}.title`)}
              </h1>

              <p className="mt-7 max-w-[520px] text-[17px] leading-8 text-[#6d6d68]">
                {t(
                  `${shellKey}.description`
                )}
              </p>

              <div className="mt-10 max-w-[520px] rounded-[22px] border border-black/[0.08] bg-white/70 p-5">
                <p className="text-sm leading-6 text-[#6d6d68]">
                  {t(
                    `${shellKey}.switchHint`
                  )}
                </p>

                <Link
                  href={switchPath}
                  className="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-[#171717] underline decoration-black/20 underline-offset-4 transition hover:decoration-black"
                >
                  {t(
                    `${shellKey}.switchAction`
                  )}

                  <span aria-hidden="true">
                    →
                  </span>
                </Link>
              </div>
            </div>
          </section>

          <section className="flex items-center justify-center px-5 py-10 sm:px-8 lg:pr-16 xl:pr-24">
            <div className="w-full max-w-[500px] rounded-[28px] border border-black/[0.07] bg-white p-6 shadow-[0_24px_80px_rgba(0,0,0,0.07)] sm:p-10">
              {children}
            </div>
          </section>
        </div>

        <footer className="px-6 pb-7 text-center text-[11px] text-[#999] sm:px-10 lg:px-16 lg:text-left">
          {t("footer")}
        </footer>
      </div>
    </main>
  );
}

export function AuthPasswordField({
  id,
  label,
  value,
  onChange,
  error,
  placeholder,
  autoComplete,
  disabled,
  hint
}: AuthPasswordFieldProps) {
  const t = useTranslations("auth");

  const [visible, setVisible] =
    useState(false);

  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;

  return (
    <div>
      <label
        htmlFor={id}
        className="mb-2 block text-[13px] font-semibold text-[#333]"
      >
        {label}
      </label>

      <div className="relative">
        <input
          id={id}
          name={id}
          type={
            visible
              ? "text"
              : "password"
          }
          value={value}
          onChange={(event) =>
            onChange(
              event.target.value
            )
          }
          autoComplete={autoComplete}
          placeholder={placeholder}
          disabled={disabled}
          aria-invalid={Boolean(
            error
          )}
          aria-describedby={
            error
              ? errorId
              : hint
                ? hintId
                : undefined
          }
          className={`h-[52px] w-full rounded-[14px] border bg-[#fafafa] px-4 pr-12 text-[15px] outline-none transition placeholder:text-[#aaa] disabled:cursor-not-allowed disabled:opacity-60 ${
            error
              ? "border-red-300 focus:border-red-400 focus:ring-4 focus:ring-red-50"
              : "border-black/[0.09] hover:border-black/[0.16] focus:border-[#171717] focus:bg-white focus:ring-4 focus:ring-black/[0.04]"
          }`}
        />

        <button
          type="button"
          onClick={() =>
            setVisible(
              (current) => !current
            )
          }
          disabled={disabled}
          aria-label={
            visible
              ? t("password.hide")
              : t("password.show")
          }
          className="absolute right-2 top-1/2 flex size-9 -translate-y-1/2 items-center justify-center rounded-lg text-[#999] transition hover:bg-black/[0.04] hover:text-[#222] disabled:cursor-not-allowed disabled:opacity-60"
        >
          {visible ? (
            <svg
              viewBox="0 0 24 24"
              fill="none"
              className="size-[18px]"
              stroke="currentColor"
              strokeWidth="1.8"
              strokeLinecap="round"
              aria-hidden="true"
            >
              <path d="m4 4 16 16" />

              <path d="M10.7 10.8a2 2 0 0 0 2.5 2.5" />

              <path d="M9.5 5.4A9 9 0 0 1 12 5c5.5 0 9 7 9 7a14 14 0 0 1-2.2 3.2" />

              <path d="M6.2 6.2C4.1 7.7 3 10 3 12c0 0 3.5 7 9 7a9 9 0 0 0 3-.5" />
            </svg>
          ) : (
            <svg
              viewBox="0 0 24 24"
              fill="none"
              className="size-[18px]"
              stroke="currentColor"
              strokeWidth="1.8"
              aria-hidden="true"
            >
              <path d="M3 12s3.5-7 9-7 9 7 9 7-3.5 7-9 7-9-7-9-7Z" />

              <circle
                cx="12"
                cy="12"
                r="2.5"
              />
            </svg>
          )}
        </button>
      </div>

      {error ? (
        <p
          id={errorId}
          className="mt-1.5 text-xs font-medium text-red-500"
        >
          {error}
        </p>
      ) : hint ? (
        <p
          id={hintId}
          className="mt-1.5 text-xs leading-5 text-[#8a8a84]"
        >
          {hint}
        </p>
      ) : null}
    </div>
  );
}