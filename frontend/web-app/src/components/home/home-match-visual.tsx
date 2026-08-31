"use client";

import {useTranslations} from "next-intl";

function CheckIcon() {
  return (
    <svg
      viewBox="0 0 16 16"
      fill="none"
      className="size-3"
      aria-hidden="true"
    >
      <path
        d="m3.25 8.1 2.8 2.8 6.7-6.3"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function SparkIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4"
      aria-hidden="true"
    >
      <path
        d="M10 2.8c.45 3.9 2.35 5.8 6.2 6.2-3.85.45-5.75 2.35-6.2 6.2-.45-3.85-2.35-5.75-6.2-6.2C7.65 8.6 9.55 6.7 10 2.8Z"
        stroke="currentColor"
        strokeWidth="1.35"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function HomeMatchVisual() {
  const t = useTranslations("home.preview");

  const skills = [
    t("skillOne"),
    t("skillTwo"),
    t("skillThree")
  ];

  return (
    <div className="home-rail-root relative mx-auto w-full max-w-[590px] lg:ml-auto">
      <div className="pointer-events-none absolute left-[14%] top-[18%] size-48 rounded-full bg-[#d9ff75]/15 blur-[80px]" />

      <div className="relative overflow-hidden rounded-[30px] border border-black/[0.055] bg-white/80 p-4 shadow-[0_24px_70px_rgba(0,0,0,0.05)] backdrop-blur-sm sm:p-5">
        {/* HEADER */}
        <div className="flex items-center justify-between px-1 pb-5">
          <div className="flex items-center gap-2">
            <span className="relative flex size-2">
              <span className="absolute inset-0 animate-ping rounded-full bg-[#d9ff75] opacity-30" />

              <span className="relative size-2 rounded-full bg-[#d9ff75]" />
            </span>

            <span className="font-mono text-[8px] tracking-[0.15em] text-black/32">
              {t("status")}
            </span>
          </div>

          <span className="font-mono text-[8px] tracking-[0.15em] text-black/18">
            AUTOJOB / MATCH ENGINE
          </span>
        </div>

        <div className="rounded-[24px] border border-black/[0.045] bg-[#f7f7f4] p-4 sm:p-5">
          <div className="grid items-stretch gap-3 md:grid-cols-[1fr_112px_1fr]">
            {/* PROFILE */}
            <div className="home-rail-profile flex min-h-[210px] flex-col rounded-[20px] border border-black/[0.05] bg-white p-4">
              <div className="flex items-center justify-between">
                <span className="font-mono text-[7px] tracking-[0.13em] text-black/24">
                  PROFILE / 01
                </span>

                <span className="flex items-center gap-1.5 rounded-full bg-[#f4f4ef] px-2 py-1">
                  <span className="size-1.5 rounded-full bg-[#d9ff75]" />

                  <span className="font-mono text-[6px] tracking-[0.09em] text-black/32">
                    READY
                  </span>
                </span>
              </div>

              <div className="mt-5 flex items-center gap-3">
                <div className="flex size-11 shrink-0 items-center justify-center rounded-[13px] bg-[#171717] text-[10px] font-bold text-white">
                  CV
                </div>

                <div className="min-w-0">
                  <p className="truncate text-[13px] font-semibold tracking-[-0.025em] text-[#20201e]">
                    {t("profileName")}
                  </p>

                  <p className="mt-1 truncate text-[8px] text-black/35">
                    {t("profileRole")}
                  </p>
                </div>
              </div>

              <div className="mt-auto space-y-2 pt-5">
                {skills.map((skill, index) => (
                  <div
                    key={skill}
                    className="home-rail-profile-skill flex items-center gap-2"
                    style={{
                      animationDelay: `${index * 180}ms`
                    }}
                  >
                    <span className="home-rail-profile-check flex size-5 shrink-0 items-center justify-center rounded-full border border-black/[0.055] bg-[#fafaf7] text-black/35">
                      <CheckIcon />
                    </span>

                    <span className="text-[8px] font-medium text-black/42">
                      {skill}
                    </span>

                    <span className="ml-auto h-[3px] w-10 overflow-hidden rounded-full bg-black/[0.05]">
                      <span
                        className="home-rail-profile-bar block h-full origin-left rounded-full bg-[#d9ff75]"
                        style={{
                          animationDelay: `${index * 180}ms`
                        }}
                      />
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {/* AI */}
            <div className="relative flex min-h-[150px] items-center justify-center md:min-h-[210px]">
              <div className="absolute bottom-0 left-1/2 top-0 w-px -translate-x-1/2 bg-black/[0.055] md:hidden" />

              <div className="absolute left-0 right-0 top-1/2 hidden h-px -translate-y-1/2 bg-black/[0.055] md:block" />

              <div className="absolute left-0 right-0 top-1/2 hidden h-[2px] -translate-y-1/2 overflow-hidden md:block">
                <div className="home-rail-track-light h-full w-12 rounded-full bg-[#d9ff75] shadow-[0_0_14px_rgba(217,255,117,0.8)]" />
              </div>

              <div className="home-rail-orb relative z-10 flex size-[88px] items-center justify-center rounded-full bg-[#171717] text-white shadow-[0_16px_35px_rgba(0,0,0,0.14)]">
                <span className="absolute -inset-[6px] rounded-full border border-black/[0.06]" />

                {/* SCANNING STATE */}
                <div className="home-rail-scanning absolute inset-0 flex flex-col items-center justify-center">
                  <div className="text-[#d9ff75]">
                    <SparkIcon />
                  </div>

                  <span className="mt-2 font-mono text-[6px] tracking-[0.12em] text-white/38">
                    ANALYZING
                  </span>

                  <div className="mt-2 flex gap-1">
                    <span className="home-rail-dot home-rail-dot-1 size-1 rounded-full bg-[#d9ff75]" />
                    <span className="home-rail-dot home-rail-dot-2 size-1 rounded-full bg-[#d9ff75]" />
                    <span className="home-rail-dot home-rail-dot-3 size-1 rounded-full bg-[#d9ff75]" />
                  </div>
                </div>

                {/* SCORE STATE */}
                <div className="home-rail-result absolute inset-0 flex flex-col items-center justify-center">
                  <span className="font-mono text-[6px] tracking-[0.11em] text-white/38">
                    AI MATCH
                  </span>

                  <div className="mt-1 flex items-end">
                    <span className="text-[27px] font-semibold leading-none tracking-[-0.075em]">
                      {t("score")}
                    </span>

                    <span className="mb-0.5 ml-0.5 text-[7px] text-[#d9ff75]">
                      %
                    </span>
                  </div>

                  <span className="mt-1 text-[6px] text-white/35">
                    {t("signal")}
                  </span>
                </div>
              </div>
            </div>

            {/* JOB AREA */}
            <div className="relative min-h-[210px]">
              {/* empty area */}
              <div className="home-rail-job-empty absolute inset-0 rounded-[20px] border border-dashed border-black/[0.055] bg-white/25" />

              {/* actual result */}
              <div className="home-rail-job absolute inset-0 flex flex-col rounded-[20px] border border-black/[0.05] bg-white p-4 shadow-[0_12px_35px_rgba(0,0,0,0.035)]">
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[7px] tracking-[0.13em] text-black/24">
                    {t("job")}
                  </span>

                  <span className="size-1.5 rounded-full bg-[#d9ff75]" />
                </div>

                <div className="mt-5">
                  <p className="text-[15px] font-semibold leading-tight tracking-[-0.035em] text-[#20201e]">
                    {t("jobTitle")}
                  </p>

                  <p className="mt-1.5 text-[8px] text-black/35">
                    {t("jobCompany")}
                  </p>
                </div>

                <div className="mt-5 rounded-[13px] bg-[#f6f6f2] px-3 py-3">
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-[6px] tracking-[0.11em] text-black/25">
                      MATCH SCORE
                    </span>

                    <span className="text-[13px] font-semibold tracking-[-0.04em] text-[#20201e]">
                      {t("score")}%
                    </span>
                  </div>

                  <div className="mt-2 h-1 overflow-hidden rounded-full bg-black/[0.055]">
                    <div className="home-rail-job-progress h-full origin-left rounded-full bg-[#d9ff75]" />
                  </div>
                </div>

                <div className="mt-auto flex items-center justify-between border-t border-black/[0.045] pt-4">
                  <span className="text-[7px] text-black/32">
                    {t("jobMeta")}
                  </span>

                  <span className="flex items-center gap-1.5 text-[7px] font-semibold text-black/45">
                    <span className="size-1.5 rounded-full bg-[#d9ff75]" />

                    {t("signal")}
                  </span>
                </div>
              </div>
            </div>
          </div>

          {/* RESULTS / SIGNALS */}
          <div className="mt-3 min-h-[45px]">
            <div className="grid gap-2 sm:grid-cols-3">
              {skills.map((skill, index) => (
                <div
                  key={`signal-${skill}`}
                  className="home-rail-signal flex items-center gap-2 rounded-[12px] border border-black/[0.045] bg-white px-3 py-2.5 shadow-[0_5px_18px_rgba(0,0,0,0.02)]"
                  style={{
                    animationDelay: `${index * 160}ms`
                  }}
                >
                  <span className="flex size-5 shrink-0 items-center justify-center rounded-full bg-[#171717] text-[#d9ff75]">
                    <CheckIcon />
                  </span>

                  <span className="truncate text-[7px] font-medium text-black/40">
                    {skill}
                  </span>

                  <span className="ml-auto font-mono text-[6px] text-black/22">
                    OK
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>

        <div className="flex items-center justify-between px-1 pt-4">
          <span className="home-rail-status-processing max-w-[74%] truncate text-[8px] text-black/28">
            {t("processing")}
          </span>

          <span className="home-rail-status-complete max-w-[74%] truncate text-[8px] font-medium text-black/34">
            {t("signal")}
          </span>

          <span className="font-mono text-[7px] tracking-[0.1em] text-black/18">
            03 / 03
          </span>
        </div>
      </div>

      <style>{`
        /*
         * Timeline 8 giây:
         *
         * 0 - 2.8s  : đọc CV
         * 2.8 - 4s  : AI matching
         * ~4s       : score xuất hiện
         * ~4.6s     : job xuất hiện
         * ~5.2s     : signals lần lượt xuất hiện
         * 7.2 - 8s  : reset
         */

        @keyframes home-rail-enter {
          from {
            opacity: 0;
            transform: translate3d(0, 16px, 0) scale(.985);
          }

          to {
            opacity: 1;
            transform: translate3d(0, 0, 0) scale(1);
          }
        }

        .home-rail-root {
          animation:
            home-rail-enter
            760ms
            320ms
            cubic-bezier(.22, 1, .36, 1)
            both;
        }

        /* ===========================
           PROFILE
        =========================== */

        @keyframes home-rail-profile {
          0%,
          100% {
            transform: translate3d(0, 0, 0);
          }

          7%,
          28% {
            transform: translate3d(0, -3px, 0);
          }

          35% {
            transform: translate3d(0, 0, 0);
          }
        }

        .home-rail-profile {
          animation:
            home-rail-profile
            8s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        @keyframes home-rail-profile-skill {
          0%,
          6% {
            opacity: .35;
            transform: translate3d(-3px, 0, 0);
          }

          13%,
          35% {
            opacity: 1;
            transform: translate3d(0, 0, 0);
          }

          45%,
          90% {
            opacity: .65;
          }

          100% {
            opacity: .35;
          }
        }

        .home-rail-profile-skill {
          animation:
            home-rail-profile-skill
            8s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        @keyframes home-rail-profile-check {
          0%,
          7% {
            color: rgba(0,0,0,.2);
            background: #fafaf7;
          }

          13%,
          30% {
            color: #171717;
            background: rgba(217,255,117,.28);
          }

          40%,
          100% {
            color: rgba(0,0,0,.35);
            background: #fafaf7;
          }
        }

        .home-rail-profile-check {
          animation:
            home-rail-profile-check
            8s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        @keyframes home-rail-profile-bar {
          0%,
          7% {
            transform: scaleX(.04);
            opacity: .18;
          }

          20%,
          88% {
            transform: scaleX(1);
            opacity: 1;
          }

          100% {
            transform: scaleX(.04);
            opacity: .18;
          }
        }

        .home-rail-profile-bar {
          animation:
            home-rail-profile-bar
            8s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        /* ===========================
           DATA TRAVEL
        =========================== */

        @keyframes home-rail-track-light {
          0%,
          29% {
            opacity: 0;
            transform: translate3d(-55px, 0, 0);
          }

          34% {
            opacity: 1;
          }

          50% {
            opacity: 1;
            transform: translate3d(150px, 0, 0);
          }

          54%,
          100% {
            opacity: 0;
            transform: translate3d(210px, 0, 0);
          }
        }

        .home-rail-track-light {
          animation:
            home-rail-track-light
            8s
            cubic-bezier(.4, 0, .2, 1)
            infinite;
        }

        /* ===========================
           AI SCANNING
        =========================== */

        @keyframes home-rail-orb {
          0%,
          24% {
            transform: scale(.97);
          }

          33%,
          47% {
            transform: scale(1.055);
          }

          54%,
          88% {
            transform: scale(1);
          }

          100% {
            transform: scale(.97);
          }
        }

        .home-rail-orb {
          animation:
            home-rail-orb
            8s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        @keyframes home-rail-scanning {
          0%,
          24% {
            opacity: .35;
          }

          31%,
          48% {
            opacity: 1;
          }

          53%,
          94% {
            opacity: 0;
            transform: scale(.94);
          }

          100% {
            opacity: .35;
            transform: scale(1);
          }
        }

        .home-rail-scanning {
          animation:
            home-rail-scanning
            8s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        @keyframes home-rail-result {
          0%,
          48% {
            opacity: 0;
            transform: translate3d(0, 5px, 0) scale(.94);
          }

          54%,
          90% {
            opacity: 1;
            transform: translate3d(0, 0, 0) scale(1);
          }

          96%,
          100% {
            opacity: 0;
            transform: translate3d(0, 3px, 0) scale(.96);
          }
        }

        .home-rail-result {
          opacity: 0;

          animation:
            home-rail-result
            8s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        @keyframes home-rail-dot {
          0%,
          100% {
            opacity: .2;
            transform: translate3d(0, 0, 0);
          }

          50% {
            opacity: 1;
            transform: translate3d(0, -3px, 0);
          }
        }

        .home-rail-dot {
          animation:
            home-rail-dot
            720ms
            ease-in-out
            infinite;
        }

        .home-rail-dot-2 {
          animation-delay: 120ms;
        }

        .home-rail-dot-3 {
          animation-delay: 240ms;
        }

        /* ===========================
           EMPTY JOB AREA
        =========================== */

        @keyframes home-rail-job-empty {
          0%,
          49% {
            opacity: 1;
          }

          56%,
          91% {
            opacity: 0;
          }

          97%,
          100% {
            opacity: 1;
          }
        }

        .home-rail-job-empty {
          animation:
            home-rail-job-empty
            8s
            linear
            infinite;
        }

        /* ===========================
           JOB REVEAL
        =========================== */

        @keyframes home-rail-job {
          0%,
          51% {
            opacity: 0;
            transform:
              translate3d(8px, 0, 0)
              scale(.975);
          }

          58% {
            opacity: 1;
            transform:
              translate3d(-2px, 0, 0)
              scale(1.008);
          }

          64%,
          90% {
            opacity: 1;
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }

          96%,
          100% {
            opacity: 0;
            transform:
              translate3d(5px, 0, 0)
              scale(.98);
          }
        }

        .home-rail-job {
          opacity: 0;

          animation:
            home-rail-job
            8s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        @keyframes home-rail-job-progress {
          0%,
          55% {
            transform: scaleX(.03);
          }

          68%,
          92% {
            transform: scaleX(.94);
          }

          100% {
            transform: scaleX(.03);
          }
        }

        .home-rail-job-progress {
          animation:
            home-rail-job-progress
            8s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        /* ===========================
           BOTTOM SIGNALS
        =========================== */

        @keyframes home-rail-signal {
          0%,
          61% {
            opacity: 0;
            transform:
              translate3d(0, 8px, 0)
              scale(.98);
          }

          69% {
            opacity: 1;
            transform:
              translate3d(0, -1px, 0)
              scale(1.005);
          }

          75%,
          90% {
            opacity: 1;
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }

          96%,
          100% {
            opacity: 0;
            transform:
              translate3d(0, 5px, 0)
              scale(.985);
          }
        }

        .home-rail-signal {
          opacity: 0;

          animation:
            home-rail-signal
            8s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        /* ===========================
           STATUS
        =========================== */

        @keyframes home-rail-status-processing {
          0%,
          52% {
            opacity: 1;
          }

          58%,
          93% {
            opacity: 0;
          }

          100% {
            opacity: 1;
          }
        }

        .home-rail-status-processing {
          animation:
            home-rail-status-processing
            8s
            linear
            infinite;
        }

        @keyframes home-rail-status-complete {
          0%,
          52% {
            opacity: 0;
          }

          60%,
          92% {
            opacity: 1;
          }

          98%,
          100% {
            opacity: 0;
          }
        }

        .home-rail-status-complete {
          position: absolute;
          left: 1.25rem;

          opacity: 0;

          animation:
            home-rail-status-complete
            8s
            linear
            infinite;
        }
      `}</style>
    </div>
  );
}