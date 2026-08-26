"use client";

import type {ReactNode} from "react";
import {
  useLocale,
  useTranslations
} from "next-intl";

import {Link} from "@/i18n/navigation";

import type {AuthMode} from "@/types/auth";

type Props = {
  mode: AuthMode;

  onModeChange: (
    mode: AuthMode
  ) => void;

  loginForm: ReactNode;
  registerForm: ReactNode;
};

function Logo() {
  return (
    <Link
      href="/"
      className="group inline-flex items-center gap-3"
    >
      <div className="flex size-10 items-center justify-center rounded-[14px] bg-[#161616] text-white shadow-[0_5px_16px_rgba(0,0,0,0.12)] transition-transform duration-300 group-hover:-rotate-3 group-hover:scale-[1.03]">
        <svg
          viewBox="0 0 24 24"
          fill="none"
          className="size-[18px]"
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

      <span className="text-[19px] font-bold tracking-[-0.045em] text-[#161616]">
        AutoJob
      </span>
    </Link>
  );
}

export function AuthStage({
  mode,
  onModeChange,
  loginForm,
  registerForm
}: Props) {
  const t = useTranslations("auth");
  const locale = useLocale();

  const isLogin =
    mode === "login";

  const currentPath =
    isLogin
      ? "/login"
      : "/register";

  return (
    <main className="auth-page min-h-screen overflow-hidden bg-[#f7f7f4] text-[#181818] selection:bg-black selection:text-white">
      <div className="mx-auto flex min-h-screen max-w-[1380px] flex-col px-5 sm:px-8 lg:px-12">
        {/* HEADER */}
        <header className="flex h-[84px] shrink-0 items-center justify-between">
          <Logo />

          <div className="flex items-center gap-1 rounded-full border border-black/[0.06] bg-white/90 p-1 shadow-[0_5px_20px_rgba(0,0,0,0.035)] backdrop-blur-xl">
            <Link
              href={currentPath}
              locale="vi"
              className={`flex h-7 min-w-9 items-center justify-center rounded-full px-2 text-[10px] font-bold transition-all duration-300 ${
                locale === "vi"
                  ? "bg-[#181818] text-white shadow-sm"
                  : "text-[#a0a09b] hover:text-[#333]"
              }`}
            >
              VI
            </Link>

            <Link
              href={currentPath}
              locale="en"
              className={`flex h-7 min-w-9 items-center justify-center rounded-full px-2 text-[10px] font-bold transition-all duration-300 ${
                locale === "en"
                  ? "bg-[#181818] text-white shadow-sm"
                  : "text-[#a0a09b] hover:text-[#333]"
              }`}
            >
              EN
            </Link>
          </div>
        </header>

        {/* DESKTOP */}
        <div className="hidden flex-1 items-center justify-center py-7 lg:flex">
          <div className="relative h-[620px] w-full max-w-[1120px]">
            {/* FORM CARD */}
            <section
              className="absolute bottom-0 left-0 top-0 z-10 w-[calc(50%-10px)] transform-gpu will-change-transform transition-transform duration-[820ms] ease-[cubic-bezier(0.16,1,0.3,1)]"
              style={{
                transform: isLogin
                  ? "translate3d(0, 0, 0)"
                  : "translate3d(calc(100% + 20px), 0, 0)"
              }}
            >
              <div className="relative flex h-full overflow-hidden rounded-[30px] border border-black/[0.055] bg-white shadow-[0_30px_80px_rgba(0,0,0,0.075)]">
                <div
                  aria-hidden="true"
                  className="pointer-events-none absolute left-8 right-8 top-0 h-px bg-gradient-to-r from-transparent via-black/[0.1] to-transparent"
                />

                {/* LOGIN FORM */}
                <div
                  className={`absolute inset-0 flex items-center justify-center px-12 xl:px-16 transition-[opacity,transform] duration-500 ease-[cubic-bezier(0.16,1,0.3,1)] ${
                    isLogin
                      ? "translate-y-0 opacity-100 delay-[250ms]"
                      : "pointer-events-none -translate-y-3 opacity-0 delay-0"
                  }`}
                >
                  <div className="w-full max-w-[370px]">
                    {loginForm}
                  </div>
                </div>

                {/* REGISTER FORM */}
                <div
                  className={`absolute inset-0 flex items-center justify-center px-12 xl:px-16 transition-[opacity,transform] duration-500 ease-[cubic-bezier(0.16,1,0.3,1)] ${
                    !isLogin
                      ? "translate-y-0 opacity-100 delay-[250ms]"
                      : "pointer-events-none translate-y-3 opacity-0 delay-0"
                  }`}
                >
                  <div className="w-full max-w-[370px]">
                    {registerForm}
                  </div>
                </div>
              </div>
            </section>

            {/* STORY CARD */}
            <section
              className="absolute bottom-0 right-0 top-0 z-20 w-[calc(50%-10px)] transform-gpu will-change-transform transition-transform duration-[820ms] ease-[cubic-bezier(0.16,1,0.3,1)]"
              style={{
                transform: isLogin
                  ? "translate3d(0, 0, 0)"
                  : "translate3d(calc(-100% - 20px), 0, 0)"
              }}
            >
              <div className="relative h-full overflow-hidden rounded-[30px] border border-black/[0.045] bg-[#efefeb]">
                <StoryBackground />

                {/* LOGIN STORY */}
                <div
                  className={`absolute inset-0 flex flex-col justify-between p-11 xl:p-13 transition-[opacity,transform] duration-[460ms] ease-[cubic-bezier(0.16,1,0.3,1)] ${
                    isLogin
                      ? "translate-x-0 opacity-100 delay-[280ms]"
                      : "pointer-events-none translate-x-5 opacity-0"
                  }`}
                >
                  <StoryTop
                    index="01"
                    badge={t(
                      "shell.badge"
                    )}
                  />

                  <StoryContent
                    title={t(
                      "shell.login.title"
                    )}
                    description={t(
                      "shell.login.description"
                    )}
                    action={t(
                      "shell.login.switchAction"
                    )}
                    direction="right"
                    onClick={() =>
                      onModeChange(
                        "register"
                      )
                    }
                  />

                  <MatchingVisual />
                </div>

                {/* REGISTER STORY */}
                <div
                  className={`absolute inset-0 flex flex-col justify-between p-11 xl:p-13 transition-[opacity,transform] duration-[460ms] ease-[cubic-bezier(0.16,1,0.3,1)] ${
                    !isLogin
                      ? "translate-x-0 opacity-100 delay-[280ms]"
                      : "pointer-events-none -translate-x-5 opacity-0"
                  }`}
                >
                  <StoryTop
                    index="02"
                    badge={t(
                      "shell.badge"
                    )}
                  />

                  <StoryContent
                    title={t(
                      "shell.register.title"
                    )}
                    description={t(
                      "shell.register.description"
                    )}
                    action={t(
                      "shell.register.switchAction"
                    )}
                    direction="left"
                    onClick={() =>
                      onModeChange(
                        "login"
                      )
                    }
                  />

                  <MatchingVisual />
                </div>
              </div>
            </section>
          </div>
        </div>

        {/* MOBILE */}
        <div className="flex flex-1 items-center justify-center py-6 lg:hidden">
          <div className="w-full max-w-[460px]">
            <div className="mb-4 overflow-hidden rounded-[24px] border border-black/[0.05] bg-[#efefeb] p-6">
              <div className="mb-8 flex items-center justify-between">
                <div className="inline-flex items-center gap-2 text-[9px] font-bold uppercase tracking-[0.16em] text-[#777]">
                  <span className="size-1.5 rounded-full bg-[#181818]" />

                  {t(
                    "shell.badge"
                  )}
                </div>

                <span className="font-mono text-[10px] text-[#aaa]">
                  {isLogin
                    ? "01"
                    : "02"}
                </span>
              </div>

              <h2 className="max-w-[360px] text-[29px] font-semibold leading-[1.08] tracking-[-0.055em]">
                {t(
                  isLogin
                    ? "shell.login.title"
                    : "shell.register.title"
                )}
              </h2>
            </div>

            <div className="mb-3 grid grid-cols-2 rounded-[14px] bg-[#ecece8] p-1">
              <button
                type="button"
                onClick={() =>
                  onModeChange(
                    "login"
                  )
                }
                className={`h-10 rounded-[11px] text-[12px] font-semibold transition-all duration-300 ${
                  isLogin
                    ? "bg-white text-[#181818] shadow-[0_2px_8px_rgba(0,0,0,0.06)]"
                    : "text-[#8f8f89]"
                }`}
              >
                {t(
                  "login.title"
                )}
              </button>

              <button
                type="button"
                onClick={() =>
                  onModeChange(
                    "register"
                  )
                }
                className={`h-10 rounded-[11px] text-[12px] font-semibold transition-all duration-300 ${
                  !isLogin
                    ? "bg-white text-[#181818] shadow-[0_2px_8px_rgba(0,0,0,0.06)]"
                    : "text-[#8f8f89]"
                }`}
              >
                {t(
                  "register.title"
                )}
              </button>
            </div>

            <div className="relative overflow-hidden rounded-[26px] border border-black/[0.055] bg-white p-6 shadow-[0_20px_60px_rgba(0,0,0,0.055)] sm:p-8">
              <div
                key={mode}
                className="auth-form-enter"
              >
                {isLogin
                  ? loginForm
                  : registerForm}
              </div>
            </div>
          </div>
        </div>

        <footer className="shrink-0 py-5 text-center text-[10px] text-[#aaa9a4]">
          {t("footer")}
        </footer>
      </div>

      <style jsx global>{`
        .auth-page {
          background-image:
            radial-gradient(
              rgba(0, 0, 0, 0.035)
                0.7px,
              transparent 0.7px
            );

          background-size:
            22px 22px;
        }

        @keyframes auth-form-enter {
          from {
            opacity: 0;
            transform:
              translateY(10px)
              scale(0.995);
          }

          to {
            opacity: 1;
            transform:
              translateY(0)
              scale(1);
          }
        }

        .auth-form-enter {
          animation:
            auth-form-enter
            460ms
            cubic-bezier(
              0.16,
              1,
              0.3,
              1
            )
            both;
        }

        @media (
          prefers-reduced-motion:
          reduce
        ) {
          .auth-form-enter {
            animation: none;
          }
        }
      `}</style>
    </main>
  );
}

function StoryTop({
  badge,
  index
}: {
  badge: string;
  index: string;
}) {
  return (
    <div className="relative z-10 flex items-center justify-between">
      <div className="inline-flex items-center gap-2 rounded-full border border-black/[0.055] bg-white/75 px-3 py-2 text-[9px] font-bold uppercase tracking-[0.16em] text-[#74746f] shadow-[0_2px_6px_rgba(0,0,0,0.025)] backdrop-blur-md">
        <span className="size-1.5 rounded-full bg-[#181818]" />

        {badge}
      </div>

      <span className="font-mono text-[10px] tracking-[0.08em] text-[#aaa]">
        {index} / 02
      </span>
    </div>
  );
}

function StoryContent({
  title,
  description,
  action,
  direction,
  onClick
}: {
  title: string;
  description: string;
  action: string;

  direction:
    | "left"
    | "right";

  onClick: () => void;
}) {
  return (
    <div className="relative z-10 max-w-[410px]">
      <div className="mb-5 flex items-center gap-3">
        <div className="flex size-8 items-center justify-center rounded-full border border-black/[0.07] bg-white/75">
          <div className="size-2 rounded-full bg-[#181818]" />
        </div>

        <div>
          <p className="text-[10px] font-bold uppercase tracking-[0.15em] text-[#999]">
            AutoJob
          </p>

          <p className="mt-0.5 text-[10px] text-[#aaa]">
            Profile → Match → Job
          </p>
        </div>
      </div>

      <h2 className="text-[42px] font-semibold leading-[1.04] tracking-[-0.06em] text-[#181818] xl:text-[46px]">
        {title}
      </h2>

      <p className="mt-5 max-w-[375px] text-[13px] leading-6 text-[#74746f]">
        {description}
      </p>

      <button
        type="button"
        onClick={onClick}
        className="group mt-8 inline-flex h-[46px] items-center gap-3 rounded-full bg-[#181818] px-5 text-[11px] font-semibold text-white shadow-[0_8px_20px_rgba(0,0,0,0.1)] transition-[transform,box-shadow,background-color] duration-300 hover:-translate-y-px hover:bg-black hover:shadow-[0_12px_28px_rgba(0,0,0,0.15)] active:translate-y-0 active:scale-[0.98]"
      >
        {direction ===
          "left" && (
          <span className="transition-transform duration-300 group-hover:-translate-x-1">
            ←
          </span>
        )}

        {action}

        {direction ===
          "right" && (
          <span className="transition-transform duration-300 group-hover:translate-x-1">
            →
          </span>
        )}
      </button>
    </div>
  );
}

function MatchingVisual() {
  return (
    <div
      aria-hidden="true"
      className="relative z-10"
    >
      <div className="grid grid-cols-[1fr_74px_1fr] items-center gap-3">
        {/* CV */}
        <div className="rounded-[18px] border border-black/[0.05] bg-white/65 p-4 backdrop-blur-sm">
          <div className="mb-4 flex items-center justify-between">
            <div className="flex size-7 items-center justify-center rounded-[9px] bg-[#181818] text-[8px] font-bold text-white">
              CV
            </div>

            <div className="size-1.5 rounded-full bg-black/10" />
          </div>

          <div className="space-y-2">
            <div className="h-1.5 w-[75%] rounded-full bg-black/[0.12]" />

            <div className="h-1.5 w-full rounded-full bg-black/[0.06]" />

            <div className="h-1.5 w-[62%] rounded-full bg-black/[0.06]" />
          </div>
        </div>

        {/* AI */}
        <div className="relative flex justify-center">
          <div className="absolute left-[-18px] right-[-18px] top-1/2 h-px bg-black/[0.07]" />

          <div className="relative flex size-[66px] flex-col items-center justify-center rounded-full border border-black/[0.065] bg-[#f8f8f5] shadow-[0_6px_18px_rgba(0,0,0,0.05)]">
            <span className="text-[17px] font-semibold tracking-[-0.04em]">
              92
            </span>

            <span className="text-[7px] font-bold tracking-[0.1em] text-[#aaa]">
              MATCH
            </span>
          </div>
        </div>

        {/* JOB */}
        <div className="rounded-[18px] border border-black/[0.05] bg-white/65 p-4 backdrop-blur-sm">
          <div className="mb-4 flex items-center justify-between">
            <div className="flex size-7 items-center justify-center rounded-[9px] border border-black/[0.07] bg-white text-[8px] font-bold text-[#555]">
              JOB
            </div>

            <div className="size-1.5 rounded-full bg-black/10" />
          </div>

          <div className="space-y-2">
            <div className="h-1.5 w-[55%] rounded-full bg-black/[0.12]" />

            <div className="h-1.5 w-[90%] rounded-full bg-black/[0.06]" />

            <div className="h-1.5 w-[70%] rounded-full bg-black/[0.06]" />
          </div>
        </div>
      </div>
    </div>
  );
}

function StoryBackground() {
  return (
    <div
      aria-hidden="true"
      className="pointer-events-none absolute inset-0 overflow-hidden"
    >
      <div className="absolute -right-[140px] -top-[160px] size-[390px] rounded-full border border-black/[0.025]" />

      <div className="absolute -right-[70px] -top-[90px] size-[250px] rounded-full border border-black/[0.025]" />

      <div className="absolute -bottom-[160px] -left-[140px] size-[380px] rounded-full border border-black/[0.022]" />

      <div className="absolute left-[46px] top-[145px] size-1 rounded-full bg-black/10" />

      <div className="absolute right-[70px] top-[280px] size-1.5 rounded-full bg-black/[0.08]" />
    </div>
  );
}