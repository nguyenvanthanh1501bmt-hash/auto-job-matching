"use client";

import {useSyncExternalStore} from "react";
import {useTranslations} from "next-intl";

import {HomeHero} from "@/components/home/home-hero";
import {HomeSections} from "@/components/home/home-sections";
import {Footer} from "@/components/layout/footer";
import {Navbar} from "@/components/layout/navbar";
import {
  AUTH_SESSION_CHANGED_EVENT,
  getAuthSession
} from "@/lib/auth-storage";

function subscribeToAuthSession(
  onStoreChange: () => void
) {
  window.addEventListener(
    AUTH_SESSION_CHANGED_EVENT,
    onStoreChange
  );

  window.addEventListener(
    "storage",
    onStoreChange
  );

  return () => {
    window.removeEventListener(
      AUTH_SESSION_CHANGED_EVENT,
      onStoreChange
    );

    window.removeEventListener(
      "storage",
      onStoreChange
    );
  };
}

function getServerAuthSession() {
  return null;
}

export function HomeLanding() {
  const t = useTranslations("home");

  const session = useSyncExternalStore(
    subscribeToAuthSession,
    getAuthSession,
    getServerAuthSession
  );

  const isAdmin =
    session?.user.roles.includes(
      "ADMIN"
    ) ?? false;

  const workspacePath = session
    ? isAdmin
      ? "/admin" as const
      : "/jobs" as const
    : null;

  const primaryHref =
    workspacePath ??
    "/register";

  return (
    <main
      className="min-h-screen overflow-x-clip bg-[#f7f7f4] text-[#171717] selection:bg-[#171717] selection:text-white"
      style={{
        backgroundImage: `
          radial-gradient(
            circle at 16% 12%,
            rgba(255,255,255,.98),
            transparent 25%
          ),
          radial-gradient(
            circle at 82% 32%,
            rgba(255,255,255,.88),
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
      <Navbar
        pathname="/"
        items={[
          {
            href: "#how-it-works",
            label: t(
              "nav.howItWorks"
            )
          },
          {
            href: "#features",
            label: t(
              "nav.features"
            )
          }
        ]}
        action={{
          href:
            workspacePath ??
            "/login",
          label:
            workspacePath
              ? t(
                  "nav.workspace"
                )
              : t(
                  "nav.signIn"
                ),
          showArrow:
            Boolean(
              workspacePath
            )
        }}
      />

      <div className="mx-auto max-w-[1440px] px-5 sm:px-8 lg:px-12 xl:px-16">
        <HomeHero
          primaryHref={
            primaryHref
          }
          signedIn={
            Boolean(session)
          }
        />

        <HomeSections
          primaryHref={
            primaryHref
          }
          signedIn={
            Boolean(session)
          }
        />

        <Footer
          tagline={t(
            "footer.tagline"
          )}
          notice={t(
            "footer.privacy"
          )}
          copyright={t(
            "footer.copyright"
          )}
        />
      </div>
    </main>
  );
}