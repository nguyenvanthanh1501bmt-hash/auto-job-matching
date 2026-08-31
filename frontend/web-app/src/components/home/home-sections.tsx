"use client";

import {useTranslations} from "next-intl";

import {Link} from "@/i18n/navigation";

const STEP_KEYS = [
  "one",
  "two",
  "three"
] as const;

const FEATURE_KEYS = [
  "profile",
  "matching",
  "focus"
] as const;

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

export function HomeSections({
  primaryHref,
  signedIn
}: Props) {
  const t =
    useTranslations("home");

  return (
    <>
      <section
        id="how-it-works"
        className="scroll-mt-20 border-t border-black/[0.055] py-24 sm:py-28"
      >
        <div className="grid gap-12 lg:grid-cols-[0.82fr_1.18fr] lg:gap-20">
          <div>
            <div className="flex items-center gap-2">
              <span className="size-1.5 rounded-full bg-[#d9ff75]" />

              <span className="text-[9px] font-bold uppercase tracking-[0.18em] text-[#85857f]">
                {t(
                  "steps.eyebrow"
                )}
              </span>
            </div>

            <h2 className="mt-5 max-w-[460px] text-[38px] font-semibold leading-[1.02] tracking-[-0.06em] sm:text-[46px]">
              {t(
                "steps.title"
              )}
            </h2>

            <p className="mt-5 max-w-[460px] text-[13px] leading-6 text-black/42">
              {t(
                "steps.description"
              )}
            </p>
          </div>

          <div className="grid gap-3 md:grid-cols-3">
            {STEP_KEYS.map(
              (key) => (
                <article
                  key={key}
                  className="group rounded-[24px] border border-black/[0.055] bg-white p-6 transition-transform duration-300 hover:-translate-y-1 sm:p-7"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-[9px] tracking-[0.15em] text-black/25">
                      {t(
                        `steps.${key}.number`
                      )}
                    </span>

                    <span className="size-2 rounded-full bg-[#d9ff75] opacity-60 transition-opacity group-hover:opacity-100" />
                  </div>

                  <h3 className="mt-12 text-[20px] font-semibold leading-[1.08] tracking-[-0.045em]">
                    {t(
                      `steps.${key}.title`
                    )}
                  </h3>

                  <p className="mt-3 text-[12px] leading-6 text-black/42">
                    {t(
                      `steps.${key}.description`
                    )}
                  </p>
                </article>
              )
            )}
          </div>
        </div>
      </section>

      <section
        id="features"
        className="scroll-mt-20 border-t border-black/[0.055] py-24 sm:py-28"
      >
        <div className="mx-auto max-w-[720px] text-center">
          <div className="flex items-center justify-center gap-2">
            <span className="size-1.5 rounded-full bg-[#d9ff75]" />

            <span className="text-[9px] font-bold uppercase tracking-[0.18em] text-[#85857f]">
              {t(
                "features.eyebrow"
              )}
            </span>
          </div>

          <h2 className="mt-5 text-[38px] font-semibold leading-[1.02] tracking-[-0.06em] sm:text-[48px]">
            {t(
              "features.title"
            )}
          </h2>

          <p className="mx-auto mt-5 max-w-[560px] text-[13px] leading-6 text-black/42">
            {t(
              "features.description"
            )}
          </p>
        </div>

        <div className="mt-12 grid gap-3 lg:grid-cols-3">
          {FEATURE_KEYS.map(
            (key) => {
              const featured =
                key ===
                "matching";

              return (
                <article
                  key={key}
                  className={`rounded-[26px] border p-6 sm:p-7 ${
                    featured
                      ? "border-[#171717] bg-[#171717] text-white"
                      : "border-black/[0.055] bg-white"
                  }`}
                >
                  <div className="flex items-center gap-2">
                    <span className="size-1.5 rounded-full bg-[#d9ff75]" />

                    <span
                      className={`font-mono text-[8px] tracking-[0.14em] ${
                        featured
                          ? "text-white/38"
                          : "text-black/28"
                      }`}
                    >
                      {t(
                        `features.${key}.label`
                      )}
                    </span>
                  </div>

                  <h3 className="mt-9 text-[22px] font-semibold leading-[1.07] tracking-[-0.05em]">
                    {t(
                      `features.${key}.title`
                    )}
                  </h3>

                  <p
                    className={`mt-3 text-[12px] leading-6 ${
                      featured
                        ? "text-white/48"
                        : "text-black/42"
                    }`}
                  >
                    {t(
                      `features.${key}.description`
                    )}
                  </p>

                  <div
                    className={`mt-10 flex items-end justify-between border-t pt-5 ${
                      featured
                        ? "border-white/10"
                        : "border-black/[0.05]"
                    }`}
                  >
                    <span
                      className={`font-mono text-[8px] tracking-[0.12em] ${
                        featured
                          ? "text-white/30"
                          : "text-black/24"
                      }`}
                    >
                      {t(
                        `features.${key}.metric`
                      )}
                    </span>

                    <span className="text-[38px] font-semibold leading-none tracking-[-0.08em]">
                      {t(
                        `features.${key}.metricValue`
                      )}
                    </span>
                  </div>
                </article>
              );
            }
          )}
        </div>
      </section>

      <section className="border-t border-black/[0.055] py-24 sm:py-28">
        <div className="relative overflow-hidden rounded-[30px] border border-black/[0.055] bg-white px-6 py-12 shadow-[0_24px_65px_rgba(0,0,0,0.045)] sm:px-10 lg:px-14 lg:py-14">
          <div className="absolute -right-16 -top-20 size-64 rounded-full bg-[#d9ff75]/25 blur-3xl" />

          <div className="relative grid items-end gap-10 lg:grid-cols-[1fr_auto]">
            <div>
              <div className="flex items-center gap-2">
                <span className="size-1.5 rounded-full bg-[#d9ff75]" />

                <span className="text-[9px] font-bold uppercase tracking-[0.18em] text-[#85857f]">
                  {t(
                    "cta.eyebrow"
                  )}
                </span>
              </div>

              <h2 className="mt-5 max-w-[720px] text-[36px] font-semibold leading-[1.02] tracking-[-0.058em] sm:text-[46px]">
                {t(
                  "cta.title"
                )}
              </h2>

              <p className="mt-4 max-w-[650px] text-[13px] leading-6 text-black/42">
                {t(
                  "cta.description"
                )}
              </p>
            </div>

            <div className="flex flex-col gap-3 sm:flex-row">
              <Link
                href={
                  primaryHref
                }
                className="inline-flex h-12 items-center justify-center gap-2 rounded-full bg-[#171717] px-6 text-[11px] font-semibold text-white transition-transform hover:-translate-y-0.5"
              >
                {signedIn
                  ? t(
                      "cta.primarySignedIn"
                    )
                  : t(
                      "cta.primary"
                    )}

                <ArrowIcon />
              </Link>

              {!signedIn && (
                <Link
                  href="/login"
                  className="inline-flex h-12 items-center justify-center rounded-full border border-black/[0.07] bg-[#f7f7f4] px-6 text-[11px] font-semibold text-black/60 transition-colors hover:bg-[#eeeeea]"
                >
                  {t(
                    "cta.secondary"
                  )}
                </Link>
              )}
            </div>
          </div>
        </div>
      </section>
    </>
  );
}