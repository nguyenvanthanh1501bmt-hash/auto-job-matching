"use client";

import {useTranslations} from "next-intl";

import type {AuthMode} from "@/types/auth";

type Props = {
  mode: AuthMode;
};

const FLOW_DURATION = "7s";

export function AuthVisual({
  mode
}: Props) {
  const t = useTranslations("auth");

  const isLogin =
    mode === "login";

  return (
    <section className="relative max-w-[660px]">
      <div className="relative z-20">
        <div className="mb-7 flex items-center gap-3">
          <span className="h-px w-7 bg-black/20" />

          <span className="text-[9px] font-bold uppercase tracking-[0.2em] text-[#82827c]">
            {t("shell.badge")}
          </span>

          <span className="size-1.5 rounded-full bg-[#d9ff75]" />
        </div>

        {/* Giữ frame ổn định khi đổi Login/Register. */}
        <div className="relative min-h-[195px]">
          <div
            className={`absolute inset-x-0 top-0 transform-gpu transition-[opacity,transform] duration-[520ms] ease-[cubic-bezier(0.22,1,0.36,1)] ${
              isLogin
                ? "translate-x-0 opacity-100 delay-[100ms]"
                : "-translate-x-3 opacity-0"
            }`}
          >
            <HeroCopy
              title={t("shell.login.title")}
              description={t("shell.login.description")}
              active={isLogin}
            />
          </div>

          <div
            className={`absolute inset-x-0 top-0 transform-gpu transition-[opacity,transform] duration-[520ms] ease-[cubic-bezier(0.22,1,0.36,1)] ${
              !isLogin
                ? "translate-x-0 opacity-100 delay-[100ms]"
                : "translate-x-3 opacity-0"
            }`}
          >
            <HeroCopy
              title={t("shell.register.title")}
              description={t("shell.register.description")}
              active={!isLogin}
            />
          </div>
        </div>
      </div>

      {/*
       * Một timeline duy nhất:
       *
       * 0%    -> 18%   Profile active + Profile -> Match
       * 18%   -> 46%   Match active + xử lý
       * 43%   -> 46%   Reveal 94
       * 46.5% -> 66%   Match -> Job
       * 66%   -> 80%   Job active + fill kết quả
       * 80%   -> 100%  Giữ rồi reset
       */}
      <div
        key={mode}
        className="aj-canvas-enter relative mt-7 h-[310px] max-w-[620px]"
      >
        <span className="absolute left-0 top-0 font-mono text-[8px] tracking-[0.13em] text-black/25">
          PROFILE / 01
        </span>

        <span className="absolute right-[12px] top-[15px] font-mono text-[8px] tracking-[0.13em] text-black/25">
          JOB / 03
        </span>

        <span className="absolute bottom-[10px] left-[260px] font-mono text-[8px] tracking-[0.13em] text-black/20">
          AI MATCH / 94
        </span>

        {/*
         * Stage animation nằm ngoài rotation để không ghi đè transform.
         * Wrapper absolute vẫn giữ nguyên tọa độ connector.
         */}
        <div className="absolute left-[10px] top-[42px] z-20 w-[218px]">
          <div className="aj-stage-profile">
            <div className="origin-center -rotate-[1.4deg]">
              <ProfileCard />
            </div>
          </div>
        </div>

        <div className="absolute left-[267px] top-[112px] z-30">
          <div className="aj-stage-match">
            <MatchOrb />
          </div>
        </div>

        <div className="absolute right-[5px] top-[105px] z-20 w-[224px]">
          <div className="aj-stage-job">
            <div className="origin-center rotate-[1.4deg]">
              <JobCard />
            </div>
          </div>
        </div>

        {/* Path và packet dùng chung SVG để không lệch coordinate. */}
        <svg
          viewBox="0 0 620 310"
          fill="none"
          className="pointer-events-none absolute inset-0 z-10 h-full w-full"
          aria-hidden="true"
        >
          <path
            d="M228 126 C244 126 252 151 267 158"
            stroke="rgba(0,0,0,.085)"
            strokeWidth="1"
          />

          <path
            d="M359 158 C372 158 381 168 391 171"
            stroke="rgba(0,0,0,.085)"
            strokeWidth="1"
          />

          <path
            d="M228 126 C243 126 251 150 263 158"
            pathLength="1"
            stroke="#d9ff75"
            strokeWidth="2.4"
            strokeLinecap="round"
            strokeDasharray="1"
            strokeDashoffset="1"
          >
            <animate
              attributeName="stroke-dashoffset"
              dur={FLOW_DURATION}
              repeatCount="indefinite"
              values="1;1;0;0;0"
              keyTimes="0;0.02;0.18;0.21;1"
            />

            <animate
              attributeName="opacity"
              dur={FLOW_DURATION}
              repeatCount="indefinite"
              values="0;1;1;0;0"
              keyTimes="0;0.02;0.18;0.21;1"
            />
          </path>

          <circle
            r="4"
            fill="#d9ff75"
          >
            <animateMotion
              dur={FLOW_DURATION}
              repeatCount="indefinite"
              calcMode="linear"
              keyPoints="0;1;1"
              keyTimes="0;0.18;1"
              path="M228 126 C243 126 251 150 263 158"
            />

            <animate
              attributeName="opacity"
              dur={FLOW_DURATION}
              repeatCount="indefinite"
              values="0;1;1;0;0"
              keyTimes="0;0.015;0.17;0.18;1"
            />
          </circle>

          <path
            d="M363 158 C374 158 381 168 387 171"
            pathLength="1"
            stroke="#d9ff75"
            strokeWidth="2.4"
            strokeLinecap="round"
            strokeDasharray="1"
            strokeDashoffset="1"
          >
            <animate
              attributeName="stroke-dashoffset"
              dur={FLOW_DURATION}
              repeatCount="indefinite"
              values="1;1;0;0;0"
              keyTimes="0;0.465;0.66;0.69;1"
            />

            <animate
              attributeName="opacity"
              dur={FLOW_DURATION}
              repeatCount="indefinite"
              values="0;0;1;1;0;0"
              keyTimes="0;0.46;0.465;0.66;0.69;1"
            />
          </path>

          <circle
            r="4"
            fill="#d9ff75"
          >
            <animateMotion
              dur={FLOW_DURATION}
              repeatCount="indefinite"
              calcMode="linear"
              keyPoints="0;0;1;1"
              keyTimes="0;0.465;0.66;1"
              path="M363 158 C374 158 381 168 387 171"
            />

            <animate
              attributeName="opacity"
              dur={FLOW_DURATION}
              repeatCount="indefinite"
              values="0;0;1;1;0;0"
              keyTimes="0;0.46;0.465;0.65;0.66;1"
            />
          </circle>
        </svg>
      </div>

      <style>{`
        /* =========================================
           HERO ENTRANCE + CONTINUOUS WAVE
        ========================================== */

        /*
         * Entrance chỉ chạy một lần.
         * Sau đó ký tự giữ nguyên và wrapper con đảm nhiệm wave.
         */
        @keyframes aj-hero-char-enter {
          0% {
            opacity: 0;
            transform:
              translate3d(0, 16px, 0)
              scale(.97);
          }

          70% {
            opacity: 1;
            transform:
              translate3d(0, -1px, 0)
              scale(1.008);
          }

          100% {
            opacity: 1;
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }
        }

        .aj-hero-char-enter {
          opacity: 0;
          transform:
            translate3d(0, 16px, 0)
            scale(.97);
        }

        .aj-hero-char-enter-active {
          animation:
            aj-hero-char-enter
            560ms
            cubic-bezier(.22, 1, .36, 1)
            both;
        }

        /*
         * Sau entrance, từng ký tự vẫn nhảy riêng lẻ.
         * Tất cả ký tự dùng chung một chu kỳ loop; phần lớn chu kỳ là đứng yên.
         * Chu kỳ được tính ngắn hơn toàn bộ hàng chữ để lượt mới bắt đầu
         * khi khoảng 4 ký tự cuối của lượt trước vẫn đang chạy.
         */
        @keyframes aj-hero-char-wave {
          0% {
            transform: translate3d(0, 0, 0);
            animation-timing-function: cubic-bezier(.22, 1, .36, 1);
          }

          8% {
            transform: translate3d(0, -6px, 0);
            animation-timing-function: cubic-bezier(.22, 1, .36, 1);
          }

          14% {
            transform: translate3d(0, 1px, 0);
            animation-timing-function: cubic-bezier(.22, 1, .36, 1);
          }

          20%,
          100% {
            transform: translate3d(0, 0, 0);
          }
        }

        .aj-hero-char-wave {
          transform-origin: center bottom;
          will-change: transform;
        }

        .aj-hero-char-wave-active {
          animation-name: aj-hero-char-wave;
          animation-timing-function: linear;
          animation-iteration-count: infinite;
          animation-fill-mode: both;
        }

        @keyframes aj-hero-description {
          from {
            opacity: 0;
            transform: translate3d(0, 8px, 0);
          }

          to {
            opacity: 1;
            transform: translate3d(0, 0, 0);
          }
        }

        .aj-hero-description {
          opacity: 0;
          transform: translate3d(0, 8px, 0);
        }

        .aj-hero-description-active {
          animation:
            aj-hero-description
            680ms
            cubic-bezier(.22, 1, .36, 1)
            both;
        }

        /* =========================================
           CANVAS ENTRANCE
        ========================================== */

        @keyframes aj-canvas-enter {
          from {
            opacity: .72;
            transform:
              translate3d(0, 6px, 0)
              scale(.992);
          }

          to {
            opacity: 1;
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }
        }

        .aj-canvas-enter {
          animation:
            aj-canvas-enter
            680ms
            cubic-bezier(.22, 1, .36, 1)
            both;
        }

        /* =========================================
           STAGE SPOTLIGHT
        ========================================== */

        @keyframes aj-stage-profile {
          0% {
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }

          4% {
            transform:
              translate3d(0, -4px, 0)
              scale(1.018);
          }

          13% {
            transform:
              translate3d(0, -4px, 0)
              scale(1.018);
          }

          18% {
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }

          100% {
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }
        }

        .aj-stage-profile {
          transform-origin: center;
          will-change: transform;

          animation:
            aj-stage-profile
            7s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        @keyframes aj-stage-match {
          0%,
          17.9% {
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }

          22% {
            transform:
              translate3d(0, -4px, 0)
              scale(1.035);
          }

          41% {
            transform:
              translate3d(0, -4px, 0)
              scale(1.035);
          }

          46% {
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }

          100% {
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }
        }

        .aj-stage-match {
          transform-origin: center;
          will-change: transform;

          animation:
            aj-stage-match
            7s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        @keyframes aj-stage-job {
          0%,
          65.9% {
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }

          70% {
            transform:
              translate3d(0, -4px, 0)
              scale(1.018);
          }

          77% {
            transform:
              translate3d(0, -4px, 0)
              scale(1.018);
          }

          82% {
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }

          100% {
            transform:
              translate3d(0, 0, 0)
              scale(1);
          }
        }

        .aj-stage-job {
          transform-origin: center;
          will-change: transform;

          animation:
            aj-stage-job
            7s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        /* =========================================
           MATCH RING
        ========================================== */

        @keyframes aj-charge-ring {
          0%,
          17.9% {
            stroke-dashoffset: 1;
            opacity: 0;
          }

          18% {
            stroke-dashoffset: 1;
            opacity: 1;
          }

          46% {
            stroke-dashoffset: 0;
            opacity: 1;
          }

          49% {
            stroke-dashoffset: 0;
            opacity: .25;
          }

          53%,
          100% {
            stroke-dashoffset: 0;
            opacity: 0;
          }
        }

        .aj-charge-ring {
          stroke-dasharray: 1;
          stroke-dashoffset: 1;
          opacity: 0;

          animation:
            aj-charge-ring
            7s
            cubic-bezier(.4, 0, .2, 1)
            infinite;
        }

        /* =========================================
           MATCHING STATE
        ========================================== */

        @keyframes aj-matching-state {
          0%,
          17.9% {
            opacity: 0;
            transform: translate3d(0, 2px, 0);
          }

          18% {
            opacity: 1;
            transform: translate3d(0, 0, 0);
          }

          40% {
            opacity: 1;
            transform: translate3d(0, 0, 0);
          }

          43% {
            opacity: 0;
            transform: translate3d(0, -2px, 0);
          }

          100% {
            opacity: 0;
            transform: translate3d(0, -2px, 0);
          }
        }

        .aj-matching-state {
          opacity: 0;

          animation:
            aj-matching-state
            7s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        @keyframes aj-processing-bar {
          0%,
          17.9% {
            opacity: 0;
            transform: scaleX(0);
          }

          18% {
            opacity: 1;
            transform: scaleX(0);
          }

          42% {
            opacity: 1;
            transform: scaleX(1);
          }

          43% {
            opacity: 0;
            transform: scaleX(1);
          }

          100% {
            opacity: 0;
            transform: scaleX(1);
          }
        }

        .aj-processing-bar {
          transform-origin: left;

          animation:
            aj-processing-bar
            7s
            cubic-bezier(.4, 0, .2, 1)
            infinite;
        }

        /* =========================================
           SCORE 94
        ========================================== */

        @keyframes aj-score-result {
          0%,
          42% {
            opacity: 0;
            transform: translate3d(0, 4px, 0);
          }

          46% {
            opacity: 1;
            transform: translate3d(0, 0, 0);
          }

          88% {
            opacity: 1;
            transform: translate3d(0, 0, 0);
          }

          94% {
            opacity: 0;
            transform: translate3d(0, -2px, 0);
          }

          100% {
            opacity: 0;
            transform: translate3d(0, -2px, 0);
          }
        }

        .aj-score-result {
          opacity: 0;

          animation:
            aj-score-result
            7s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        /* =========================================
           JOB / SKILLS
        ========================================== */

        @keyframes aj-progress-fill {
          0%,
          65.9% {
            transform: scaleX(0);
          }

          66% {
            transform: scaleX(0);
          }

          80%,
          100% {
            transform: scaleX(1);
          }
        }

        .aj-progress-fill {
          transform-origin: left;

          animation:
            aj-progress-fill
            7s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }

        @keyframes aj-progress-signal {
          0%,
          65.9% {
            left: 0%;
            opacity: 0;
          }

          66% {
            left: 0%;
            opacity: 1;
          }

          78% {
            left: calc(89% - 4px);
            opacity: 1;
          }

          80% {
            left: calc(89% - 4px);
            opacity: 0;
          }

          100% {
            left: calc(89% - 4px);
            opacity: 0;
          }
        }

        .aj-progress-signal {
          will-change: left, opacity;

          animation:
            aj-progress-signal
            7s
            cubic-bezier(.4, 0, .2, 1)
            infinite;
        }

        @keyframes aj-skill-count {
          0%,
          65.9% {
            opacity: .28;
          }

          72% {
            opacity: .55;
          }

          80%,
          100% {
            opacity: 1;
          }
        }

        .aj-skill-count {
          animation:
            aj-skill-count
            7s
            ease
            infinite;
        }

        @keyframes aj-fit-badge {
          0%,
          65.9% {
            opacity: 0;
            transform: translate3d(0, 3px, 0);
          }

          66% {
            opacity: 0;
            transform: translate3d(0, 3px, 0);
          }

          72% {
            opacity: 1;
            transform: translate3d(0, 0, 0);
          }

          92% {
            opacity: 1;
            transform: translate3d(0, 0, 0);
          }

          97%,
          100% {
            opacity: 0;
            transform: translate3d(0, -2px, 0);
          }
        }

        .aj-fit-badge {
          opacity: 0;

          animation:
            aj-fit-badge
            7s
            cubic-bezier(.22, 1, .36, 1)
            infinite;
        }
      `}</style>
    </section>
  );
}

function HeroCopy({
  title,
  description,
  active
}: {
  title: string;
  description: string;
  active: boolean;
}) {
  const characters =
    Array.from(title);

  const animatedCharacters =
    characters.filter(
      (character) =>
        character !== " "
    ).length;

  const descriptionDelay =
    Math.min(
      animatedCharacters * 28 + 300,
      1100
    );

  /*
   * Chờ toàn bộ title entrance xong rồi mới cho từng chữ nhảy.
   * Mỗi chữ lệch nhau 105ms để vẫn thấy rõ từng ký tự.
   * loopDelay là khoảng nghỉ thêm giữa các vòng.
   */
  const waveCharacterDelay = 105;
  const overlapCharacters = 0.1;
  const loopDelay = 1500;

  const waveStartDelay =
    animatedCharacters * 28 + 620;

  const waveCycleDuration =
    Math.max(
      900,
      (animatedCharacters - overlapCharacters) *
        waveCharacterDelay + loopDelay
    );

  /*
   * Quan trọng: group theo WORD trước khi render character.
   * Như vậy browser chỉ được xuống dòng giữa các word,
   * không thể bẻ "better-fit" thành "better-f" / "it".
   */
  const titleParts = title.split(/(\s+)/);
  let characterIndex = 0;

  return (
    <>
      <h1
        aria-label={title}
        className="max-w-[590px] text-[50px] font-semibold leading-[1.01] tracking-[-0.065em] text-[#151515] xl:text-[58px]"
      >
        {titleParts.map((part, partIndex) => {
          if (/^\s+$/.test(part)) {
            return (
              <span
                key={`space-${partIndex}`}
                aria-hidden="true"
              >
                {part}
              </span>
            );
          }

          return (
            <span
              key={`word-${partIndex}`}
              aria-hidden="true"
              className="inline-block whitespace-nowrap"
            >
              {Array.from(part).map((character, index) => {
                const currentIndex = characterIndex++;

                return (
                  <span
                    key={`${character}-${partIndex}-${index}`}
                    className={`aj-hero-char-enter inline-block ${
                      active
                        ? "aj-hero-char-enter-active"
                        : ""
                    }`}
                    style={{
                      animationDelay:
                        `${currentIndex * 28}ms`
                    }}
                  >
                    <span
                      className={`aj-hero-char-wave inline-block ${
                        active
                          ? "aj-hero-char-wave-active"
                          : ""
                      }`}
                      style={{
                        animationDelay:
                          `${
                            waveStartDelay +
                            currentIndex * waveCharacterDelay
                          }ms`,
                        animationDuration:
                          `${waveCycleDuration}ms`
                      }}
                    >
                      {character}
                    </span>
                  </span>
                );
              })}
            </span>
          );
        })}
      </h1>

      <p
        className={`aj-hero-description mt-6 max-w-[500px] text-[13px] leading-7 text-[#74746f] ${
          active
            ? "aj-hero-description-active"
            : ""
        }`}
        style={{
          animationDelay:
            `${descriptionDelay}ms`
        }}
      >
        {description}
      </p>
    </>
  );
}

function ProfileCard() {
  return (
    <div className="rounded-[22px] border border-black/[0.06] bg-white p-4 shadow-[0_12px_34px_rgba(0,0,0,0.05)]">
      <div className="mb-4 flex items-start justify-between">
        <div className="flex items-center gap-3">
          <div className="flex size-9 items-center justify-center rounded-[11px] bg-[#f0f0ec] text-[9px] font-bold text-[#65655f]">
            NA
          </div>

          <div>
            <p className="text-[11px] font-semibold tracking-[-0.02em] text-[#292927]">
              Nguyen Van An
            </p>

            <p className="mt-0.5 text-[8px] text-[#999]">
              Backend Engineer
            </p>
          </div>
        </div>

        <div className="flex items-center gap-1.5">
          <span className="size-1.5 rounded-full bg-[#d9ff75]" />

          <span className="text-[7px] font-bold uppercase tracking-[0.1em] text-[#aaa]">
            ready
          </span>
        </div>
      </div>

      <div className="mb-4 flex flex-wrap gap-1.5">
        <Skill>
          TypeScript
        </Skill>

        <Skill>
          Java
        </Skill>

        <Skill>
          PostgreSQL
        </Skill>

        <Skill>
          Spring
        </Skill>
      </div>

      <div className="border-t border-black/[0.05] pt-3">
        <div className="flex items-center justify-between">
          <span className="font-mono text-[7px] uppercase tracking-[0.12em] text-[#aaa]">
            profile signal
          </span>

          <span className="text-[8px] font-semibold text-[#777]">
            12 skills
          </span>
        </div>
      </div>
    </div>
  );
}

function Skill({
  children
}: {
  children: string;
}) {
  return (
    <span className="rounded-full border border-black/[0.055] bg-[#f8f8f5] px-2 py-1 text-[7px] font-medium text-[#73736e]">
      {children}
    </span>
  );
}

function MatchOrb() {
  return (
    <div className="relative flex size-[92px] items-center justify-center rounded-full border border-black/[0.06] bg-white shadow-[0_14px_35px_rgba(0,0,0,0.065)]">
      <div className="absolute inset-[6px] rounded-full border border-dashed border-black/[0.11]" />

      {/* Ring lime chạy đồng bộ với trạng thái MATCHING. */}
      <svg
        viewBox="0 0 92 92"
        fill="none"
        className="pointer-events-none absolute inset-0 size-full -rotate-90"
        aria-hidden="true"
      >
        <circle
          cx="46"
          cy="46"
          r="39"
          pathLength="1"
          stroke="#d9ff75"
          strokeWidth="2"
          strokeLinecap="round"
          className="aj-charge-ring"
        />
      </svg>

      <div className="absolute inset-[14px] rounded-full border border-black/[0.04]" />

      <div className="relative h-[42px] w-[66px]">
        <div className="aj-matching-state absolute inset-0 flex flex-col items-center justify-center text-center">
          <span className="text-[6px] font-bold uppercase tracking-[0.14em] text-[#999]">
            matching
          </span>

          <div className="mt-2 h-[2px] w-7 overflow-hidden rounded-full bg-black/[0.06]">
            <div className="aj-processing-bar h-full w-full rounded-full bg-[#d9ff75]" />
          </div>
        </div>

        <div className="aj-score-result absolute inset-0 flex flex-col items-center justify-center text-center">
          <p className="text-[26px] font-semibold leading-none tracking-[-0.07em] text-[#171717]">
            94
          </p>

          <p className="mt-1 text-[7px] font-bold uppercase tracking-[0.13em] text-[#999]">
            AI match
          </p>
        </div>
      </div>
    </div>
  );
}

function JobCard() {
  return (
    <div className="rounded-[22px] border border-black/[0.06] bg-white p-4 shadow-[0_12px_34px_rgba(0,0,0,0.05)]">
      <div className="mb-4 flex items-start justify-between">
        <div className="flex size-9 items-center justify-center rounded-[11px] bg-[#171717] text-[9px] font-bold text-white">
          J
        </div>

        <div className="aj-fit-badge rounded-full bg-[#d9ff75] px-2 py-1 text-[7px] font-bold text-[#34342f]">
          94% FIT
        </div>
      </div>

      <div className="mb-4">
        <p className="text-[11px] font-semibold tracking-[-0.02em] text-[#292927]">
          Backend Engineer
        </p>

        <p className="mt-1 text-[8px] text-[#999]">
          Remote · Full-time
        </p>
      </div>

      <div className="space-y-3 border-t border-black/[0.05] pt-3">
        <div>
          <div className="flex items-center justify-between">
            <span className="text-[7px] text-[#999]">
              Skills matched
            </span>

            <span className="aj-skill-count text-[8px] font-semibold text-[#444]">
              8 / 9
            </span>
          </div>

          <div className="relative mt-2 h-[4px] overflow-hidden rounded-full bg-black/[0.055]">
            <div className="aj-progress-fill absolute inset-y-0 left-0 w-[89%] rounded-full bg-[#171717]" />

            <span className="aj-progress-signal absolute top-0 size-[4px] rounded-full bg-[#d9ff75]" />
          </div>
        </div>

        <div className="flex items-center justify-between">
          <span className="font-mono text-[7px] uppercase tracking-[0.11em] text-[#aaa]">
            ranked by AI
          </span>

          <span className="size-1.5 rounded-full bg-[#d9ff75]" />
        </div>
      </div>
    </div>
  );
}