"use client";

import {useTranslations} from "next-intl";

import {HomeMatchVisual} from "@/components/home/home-match-visual";
import {AnimatedText} from "@/components/ui/animated-text";
import {Link} from "@/i18n/navigation";

type PrimaryHref =
  | "/register"
  | "/jobs"
  | "/admin";

type Props = {
  primaryHref: PrimaryHref;
  signedIn: boolean;
};

function ArrowIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4"
      aria-hidden="true"
    >
      <path
        d="M4.5 10h10.25M11 6.25 14.75 10 11 13.75"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="none"
      className="size-3"
      aria-hidden="true"
    >
      <path
        d="m3.25 8.15 2.85 2.8 6.65-6.3"
        stroke="currentColor"
        strokeWidth="1.55"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function HomeHero({
  primaryHref,
  signedIn
}: Props) {
  const t = useTranslations("home");

  const titleLead =
    t("hero.titleLead");

  const titleAccent =
    t("hero.titleAccent");

  const fullTitle =
    `${titleLead} ${titleAccent}`;

  return (
    <section className="grid min-h-[calc(100vh-82px)] items-center gap-14 py-14 lg:grid-cols-[minmax(0,0.97fr)_minmax(470px,1.03fr)] lg:gap-14 lg:py-16 xl:gap-20">
      <div className="max-w-[650px]">
        <div className="aj-home-copy aj-home-copy-1 flex items-center gap-3">
          <span className="h-px w-7 bg-black/20" />

          <span className="text-[9px] font-bold uppercase tracking-[0.2em] text-[#82827c]">
            {t("hero.eyebrow")}
          </span>

          <span className="size-1.5 rounded-full bg-[#d9ff75]" />
        </div>

        <h1
          aria-label={fullTitle}
          className="mt-7 text-[48px] font-semibold leading-[0.96] tracking-[-0.067em] sm:text-[64px] lg:text-[68px] xl:text-[76px]"
        >
          <AnimatedText
            key={fullTitle}
            segments={[
              {
                text: titleLead
              },
              {
                text: titleAccent,
                breakBefore: true,
                className:
                  "mt-2 text-black/28"
              }
            ]}
          />
        </h1>

        <p className="aj-home-copy aj-home-copy-2 mt-7 max-w-[540px] text-[14px] leading-7 text-black/45 sm:text-[15px]">
          {t("hero.description")}
        </p>

        <div className="aj-home-copy aj-home-copy-3 mt-9 flex flex-col gap-3 sm:flex-row">
          <Link
            href={primaryHref}
            className="group inline-flex h-12 items-center justify-center gap-2 rounded-full bg-[#171717] px-6 text-[11px] font-semibold text-white shadow-[0_10px_26px_rgba(0,0,0,0.12)] transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-[0_14px_30px_rgba(0,0,0,0.14)] active:translate-y-0"
          >
            {signedIn
              ? t("hero.primarySignedIn")
              : t("hero.primary")}

            <span className="transition-transform duration-200 group-hover:translate-x-0.5">
              <ArrowIcon />
            </span>
          </Link>

          <a
            href="#how-it-works"
            className="inline-flex h-12 items-center justify-center rounded-full border border-black/[0.07] bg-white px-6 text-[11px] font-semibold text-black/65 shadow-[0_2px_10px_rgba(0,0,0,0.025)] transition-[background-color,transform] duration-200 hover:-translate-y-px hover:bg-[#f1f1ee]"
          >
            {t("hero.secondary")}
          </a>
        </div>

        <div className="aj-home-copy aj-home-copy-4 mt-9 flex flex-wrap gap-x-5 gap-y-3 text-[9px] font-medium text-black/38">
          {[
            t("hero.trustOne"),
            t("hero.trustTwo"),
            t("hero.trustThree")
          ].map((item) => (
            <span
              key={item}
              className="group flex items-center gap-2"
            >
              <span className="flex size-5 items-center justify-center rounded-full border border-black/[0.06] bg-white transition-transform duration-200 group-hover:-translate-y-px">
                <CheckIcon />
              </span>

              {item}
            </span>
          ))}
        </div>
      </div>

      <HomeMatchVisual />

      <style>{`
        @keyframes aj-home-copy-enter {
          from {
            opacity: 0;
            transform: translate3d(0, 9px, 0);
          }

          to {
            opacity: 1;
            transform: translate3d(0, 0, 0);
          }
        }

        .aj-home-copy {
          opacity: 0;
          animation:
            aj-home-copy-enter
            680ms
            cubic-bezier(.22, 1, .36, 1)
            both;
        }

        .aj-home-copy-1 {
          animation-delay: 80ms;
        }

        .aj-home-copy-2 {
          animation-delay: 620ms;
        }

        .aj-home-copy-3 {
          animation-delay: 760ms;
        }

        .aj-home-copy-4 {
          animation-delay: 900ms;
        }
      `}</style>
    </section>
  );
}