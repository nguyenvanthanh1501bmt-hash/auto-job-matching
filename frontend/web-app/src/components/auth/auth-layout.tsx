"use client";

import type {ReactNode} from "react";
import {useTranslations} from "next-intl";

import {LanguageSwitcher} from "@/components/ui/language-switcher";
import {Link} from "@/i18n/navigation";
import type {AuthMode} from "@/types/auth";

import {AuthVisual} from "./auth-visual";

type Props = {
  mode: AuthMode;
  onModeChange: (mode: AuthMode) => void;
  loginForm: ReactNode;
  registerForm: ReactNode;
};

function Logo() {
  return (
    <Link
      href="/"
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

      <span className="text-[18px] font-bold tracking-[-0.045em] text-[#171717]">
        AutoJob
      </span>
    </Link>
  );
}

export function AuthLayout({
  mode,
  onModeChange,
  loginForm,
  registerForm
}: Props) {
  const t = useTranslations("auth");
  const isLogin = mode === "login";

  /*
   * AuthExperience đổi login/register bằng History API để giữ animation.
   * Truyền path theo mode giúp language switch không dùng pathname cũ của Next Router.
   */
  const authPathname = isLogin
    ? "/login"
    : "/register";

  return (
    <main
      className="relative min-h-screen overflow-hidden bg-[#f7f7f4] text-[#171717] selection:bg-[#171717] selection:text-white"
      style={{
        backgroundImage: `
          radial-gradient(
            circle at 18% 23%,
            rgba(255,255,255,.98),
            transparent 30%
          ),
          radial-gradient(
            circle at 85% 76%,
            rgba(255,255,255,.92),
            transparent 28%
          ),
          linear-gradient(
            rgba(0,0,0,.018) 1px,
            transparent 1px
          ),
          linear-gradient(
            90deg,
            rgba(0,0,0,.018) 1px,
            transparent 1px
          )
        `,
        backgroundSize:
          "auto, auto, 32px 32px, 32px 32px"
      }}
    >
      <div className="relative mx-auto flex min-h-screen max-w-[1440px] flex-col px-5 sm:px-8 lg:px-12 xl:px-16">
        <header className="flex h-[82px] shrink-0 items-center justify-between">
          <Logo />

          <LanguageSwitcher pathname={authPathname} />
        </header>

        {/* Desktop giữ product visual; mobile ưu tiên form. */}
        <div className="hidden flex-1 items-center lg:flex">
          <div className="mx-auto grid w-full max-w-[1220px] grid-cols-[minmax(0,1fr)_460px] items-center gap-14 xl:gap-20">
            <div
              className={`min-w-0 transform-gpu transition-[transform,opacity] duration-[600ms] ease-[cubic-bezier(0.22,1,0.36,1)] ${
                isLogin
                  ? "translate-x-0 opacity-100"
                  : "translate-x-2 opacity-[0.985]"
              }`}
            >
              <AuthVisual mode={mode} />
            </div>

            {/* Giữ frame cố định để chuyển mode không gây reflow từng frame. */}
            <div className="relative flex min-h-[640px] items-center justify-end">
              <div className="absolute right-1 top-[32px] flex items-center gap-2">
                <span className="size-1 rounded-full bg-[#d9ff75]" />

                <span className="font-mono text-[8px] tracking-[0.13em] text-black/25">
                  SECURE ACCESS
                </span>
              </div>

              <section className="relative h-[590px] w-[460px] transform-gpu overflow-hidden rounded-[28px] border border-black/[0.06] bg-white shadow-[0_24px_65px_rgba(0,0,0,0.06)]">
                <div
                  aria-hidden="true"
                  className="pointer-events-none absolute left-8 right-8 top-0 h-px bg-gradient-to-r from-transparent via-black/[0.13] to-transparent"
                />

                <div className="absolute right-5 top-5 z-20 flex items-center gap-2">
                  <span className="relative flex size-2">
                    <span className="absolute inset-0 animate-ping rounded-full bg-[#d9ff75] opacity-25" />
                    <span className="relative size-2 rounded-full bg-[#d9ff75]" />
                  </span>

                  <span className="font-mono text-[8px] uppercase tracking-[0.13em] text-[#aaa]">
                    live
                  </span>
                </div>

                {/*
                 * Giữ cả hai form mounted để không mất state.
                 * Chuyển mode chỉ dùng transform + opacity.
                 */}
                <div
                  className={`absolute inset-0 flex transform-gpu items-center justify-center p-9 transition-[opacity,transform] duration-[500ms] ease-[cubic-bezier(0.22,1,0.36,1)] xl:p-11 ${
                    isLogin
                      ? "translate-x-0 opacity-100 delay-[120ms]"
                      : "pointer-events-none -translate-x-3 opacity-0 delay-0"
                  }`}
                >
                  <div className="w-full">
                    {loginForm}
                  </div>
                </div>

                <div
                  className={`absolute inset-0 flex transform-gpu items-center justify-center p-9 transition-[opacity,transform] duration-[500ms] ease-[cubic-bezier(0.22,1,0.36,1)] xl:p-11 ${
                    !isLogin
                      ? "translate-x-0 opacity-100 delay-[120ms]"
                      : "pointer-events-none translate-x-3 opacity-0 delay-0"
                  }`}
                >
                  <div className="w-full">
                    {registerForm}
                  </div>
                </div>
              </section>
            </div>
          </div>
        </div>

        <div className="flex flex-1 items-center justify-center py-6 lg:hidden">
          <div className="w-full max-w-[460px]">
            <div className="mb-7">
              <div className="mb-3 flex items-center gap-2">
                <span className="size-1.5 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.06]" />

                <p className="text-[9px] font-bold uppercase tracking-[0.17em] text-[#888]">
                  {t("shell.badge")}
                </p>
              </div>

              <h2 className="max-w-[390px] text-[32px] font-semibold leading-[1.06] tracking-[-0.055em]">
                {t(
                  isLogin
                    ? "shell.login.title"
                    : "shell.register.title"
                )}
              </h2>
            </div>

            <div className="mb-3 grid grid-cols-2 rounded-[14px] border border-black/[0.04] bg-[#edede9] p-1">
              <button
                type="button"
                onClick={() => onModeChange("login")}
                className={`h-10 rounded-[11px] text-[12px] font-semibold transition-[background-color,color,box-shadow] duration-200 ${
                  isLogin
                    ? "bg-white text-[#171717] shadow-[0_2px_8px_rgba(0,0,0,0.06)]"
                    : "text-[#969690]"
                }`}
              >
                {t("login.title")}
              </button>

              <button
                type="button"
                onClick={() => onModeChange("register")}
                className={`h-10 rounded-[11px] text-[12px] font-semibold transition-[background-color,color,box-shadow] duration-200 ${
                  !isLogin
                    ? "bg-white text-[#171717] shadow-[0_2px_8px_rgba(0,0,0,0.06)]"
                    : "text-[#969690]"
                }`}
              >
                {t("register.title")}
              </button>
            </div>

            {/* Mobile cũng giữ chiều cao cố định để tránh layout animation nặng. */}
            <section className="relative h-[610px] overflow-hidden rounded-[26px] border border-black/[0.06] bg-white shadow-[0_20px_55px_rgba(0,0,0,0.055)]">
              <div
                className={`absolute inset-0 transform-gpu p-6 transition-[opacity,transform] duration-[480ms] ease-[cubic-bezier(0.22,1,0.36,1)] sm:p-8 ${
                  isLogin
                    ? "translate-x-0 opacity-100 delay-[100ms]"
                    : "pointer-events-none -translate-x-3 opacity-0 delay-0"
                }`}
              >
                {loginForm}
              </div>

              <div
                className={`absolute inset-0 transform-gpu p-6 transition-[opacity,transform] duration-[480ms] ease-[cubic-bezier(0.22,1,0.36,1)] sm:p-8 ${
                  !isLogin
                    ? "translate-x-0 opacity-100 delay-[100ms]"
                    : "pointer-events-none translate-x-3 opacity-0 delay-0"
                }`}
              >
                {registerForm}
              </div>
            </section>
          </div>
        </div>

        <footer className="shrink-0 py-5 text-center text-[10px] text-[#aaa9a4]">
          {t("footer")}
        </footer>
      </div>
    </main>
  );
}