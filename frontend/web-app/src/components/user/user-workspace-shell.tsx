"use client";

import {
  useEffect,
  useState,
  type ReactNode
} from "react";
import {useTranslations} from "next-intl";

import {LanguageSwitcher} from "@/components/ui/language-switcher";
import {LogoutButton} from "@/components/ui/logout-button";
import {
  Link,
  usePathname,
  useRouter
} from "@/i18n/navigation";
import {getAuthSession} from "@/lib/auth-storage";

type Props = {
  children: ReactNode;
};

type NavigationItem = {
  key: "jobs" | "cv" | "matches";
  index: string;
  href: "/jobs" | "/cv" | "/matches";
};

const NAVIGATION_ITEMS: NavigationItem[] = [
  {
    key: "jobs",
    index: "01",
    href: "/jobs"
  },
  {
    key: "cv",
    index: "02",
    href: "/cv"
  },
  {
    key: "matches",
    index: "03",
    href: "/matches"
  }
];

function getActiveNavigation(
  pathname: string
): NavigationItem["key"] {
  if (
    pathname === "/cv" ||
    pathname.startsWith("/cv/")
  ) {
    return "cv";
  }

  if (
    pathname === "/matches" ||
    pathname.startsWith("/matches/")
  ) {
    return "matches";
  }

  return "jobs";
}

function AutoJobLogo() {
  const t =
    useTranslations(
      "user.shell"
    );

  return (
    <Link
      href="/jobs"
      className="group inline-flex items-center gap-3"
    >
      <div className="relative flex size-9 items-center justify-center overflow-hidden rounded-[11px] bg-[#171717] text-white">
        <span className="absolute inset-x-0 bottom-0 h-px bg-[#d9ff75] opacity-0 transition-opacity duration-300 group-hover:opacity-100" />

        <svg
          viewBox="0 0 24 24"
          fill="none"
          className="size-[17px] transition-transform duration-300 group-hover:-rotate-3 group-hover:scale-105"
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
          {t("workspace")}
        </p>
      </div>
    </Link>
  );
}

function WorkspaceLoading() {
  const t =
    useTranslations(
      "user.shell"
    );

  return (
    <div className="flex min-h-screen items-center justify-center bg-[#f7f7f4]">
      <div className="flex items-center gap-3 rounded-[14px] border border-black/[0.055] bg-white px-5 py-4 shadow-[0_6px_24px_rgba(0,0,0,0.035)]">
        <span className="size-4 animate-spin rounded-full border-2 border-black/10 border-t-black/60" />

        <p className="text-[12px] font-medium text-black/45">
          {t(
            "checkingAccess"
          )}
        </p>
      </div>
    </div>
  );
}

export function UserWorkspaceShell({
  children
}: Props) {
  const t =
    useTranslations(
      "user.shell"
    );

  const pathname =
    usePathname();

  const router =
    useRouter();

  const [
    checkedSession,
    setCheckedSession
  ] = useState(false);

  const [
    displayName,
    setDisplayName
  ] = useState("");

  useEffect(() => {
    const session =
      getAuthSession();

    if (!session) {
      setCheckedSession(true);

      router.replace(
        "/login"
      );

      return;
    }

    setDisplayName(
      session.user.displayName?.trim() ||
        session.user.email
    );

    setCheckedSession(true);
  }, [router]);

  if (
    !checkedSession ||
    !displayName
  ) {
    return (
      <WorkspaceLoading />
    );
  }

  const activeNavigation =
    getActiveNavigation(
      pathname
    );

  return (
    <main
      className="min-h-screen overflow-x-clip bg-[#f7f7f4] text-[#171717] selection:bg-[#171717] selection:text-white"
      style={{
        backgroundImage: `
          radial-gradient(
            circle at 12% 0%,
            rgba(255, 255, 255, .96),
            transparent 27%
          ),
          radial-gradient(
            circle at 92% 28%,
            rgba(255, 255, 255, .72),
            transparent 25%
          )
        `
      }}
    >
      <header className="sticky top-0 z-50 border-b border-black/[0.045] bg-[#f7f7f4]/90 backdrop-blur-xl">
        <div className="mx-auto flex h-[82px] max-w-[1440px] items-center justify-between px-5 sm:px-8 lg:px-12 xl:px-16">
          <AutoJobLogo />

          <div className="flex items-center gap-2.5 sm:gap-3">
            <LanguageSwitcher
              pathname={
                pathname
              }
            />

            <div className="hidden items-center gap-3 rounded-xl border border-black/[0.065] bg-white px-3.5 py-2 shadow-[0_2px_10px_rgba(0,0,0,0.025)] md:flex">
              <span className="relative flex size-2 shrink-0">
                <span className="absolute inset-0 animate-ping rounded-full bg-[#d9ff75] opacity-25" />

                <span className="relative size-2 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />
              </span>

              <div className="min-w-0">
                <p className="max-w-[190px] truncate text-[11px] font-semibold leading-4 text-[#343432]">
                  {displayName}
                </p>

                <p className="font-mono text-[8px] uppercase tracking-[0.13em] text-[#aaa]">
                  {t(
                    "activeAccount"
                  )}
                </p>
              </div>
            </div>

            <LogoutButton />
          </div>
        </div>
      </header>

      <div className="mx-auto grid max-w-[1440px] gap-7 px-5 py-7 sm:px-8 lg:grid-cols-[220px_minmax(0,1fr)] lg:gap-10 lg:px-12 lg:py-9 xl:grid-cols-[240px_minmax(0,1fr)] xl:px-16">
        <aside className="min-w-0 lg:border-r lg:border-black/[0.045] lg:pr-8">
          <div className="lg:sticky lg:top-[110px]">
            <div className="mb-6 hidden items-center gap-3 lg:flex">
              <span className="font-mono text-[9px] font-medium uppercase tracking-[0.16em] text-[#aaa]">
                {t(
                  "navigation"
                )}
              </span>

              <span className="h-px flex-1 bg-black/[0.07]" />

              <span className="size-1.5 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.06]" />
            </div>

            <nav className="-mx-5 flex gap-1.5 overflow-x-auto px-5 pb-2 sm:-mx-8 sm:px-8 lg:mx-0 lg:block lg:space-y-2 lg:overflow-visible lg:px-0 lg:pb-0">
              {NAVIGATION_ITEMS.map(
                (item) => {
                  const active =
                    item.key ===
                    activeNavigation;

                  return (
                    <Link
                      key={
                        item.key
                      }
                      href={
                        item.href
                      }
                      className={`group relative flex h-12 shrink-0 items-center rounded-[13px] border px-4 transition-all duration-200 lg:h-[52px] lg:w-full ${
                        active
                          ? "border-black/[0.065] bg-white text-[#242422] shadow-[0_4px_16px_rgba(0,0,0,0.035)]"
                          : "border-transparent text-[#70706a] hover:border-black/[0.04] hover:bg-white/60 hover:text-[#222]"
                      }`}
                    >
                      <span
                        className={`w-7 shrink-0 font-mono text-[9px] font-medium ${
                          active
                            ? "text-[#999]"
                            : "text-[#b5b5af]"
                        }`}
                      >
                        {
                          item.index
                        }
                      </span>

                      <span className="text-[14px] font-semibold tracking-[-0.02em] lg:text-[15px]">
                        {t(
                          `nav.${item.key}`
                        )}
                      </span>

                      <span
                        className={`ml-auto size-1.5 shrink-0 rounded-full transition-all ${
                          active
                            ? "scale-100 bg-[#d9ff75] opacity-100 ring-1 ring-black/[0.05]"
                            : "scale-75 bg-black/10 opacity-0 group-hover:opacity-100"
                        }`}
                      />
                    </Link>
                  );
                }
              )}
            </nav>

            <div className="mt-9 hidden lg:block">
              <div className="rounded-[15px] border border-black/[0.045] bg-white/50 px-4 py-4">
                <div className="flex items-center gap-2.5">
                  <span className="size-1.5 shrink-0 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.06]" />

                  <span className="font-mono text-[8px] font-medium uppercase tracking-[0.13em] text-[#999]">
                    {t(
                      "aiStatus"
                    )}
                  </span>
                </div>

                <p className="mt-2.5 text-[11px] leading-[18px] text-[#92928c]">
                  {t(
                    "aiDescription"
                  )}
                </p>
              </div>
            </div>
          </div>
        </aside>

        <section className="min-w-0">
          {children}
        </section>
      </div>
    </main>
  );
}