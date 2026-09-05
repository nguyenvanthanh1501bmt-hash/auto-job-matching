"use client";

import {
  useLocale,
  useTranslations
} from "next-intl";

import type {
  MatchingSummaryProps
} from "@/types/matching-ui";

function normalizeScore(
  value: number
): number {
  const normalized =
    value <= 1
      ? value * 100
      : value;

  return Math.round(
    Math.min(
      Math.max(
        normalized,
        0
      ),
      100
    )
  );
}

function formatDate(
  value: string | null,
  locale: string
): string | null {
  if (!value) {
    return null;
  }

  const date =
    new Date(value);

  if (
    Number.isNaN(
      date.getTime()
    )
  ) {
    return null;
  }

  return new Intl.DateTimeFormat(
    locale,
    {
      month: "short",
      day: "2-digit",
      hour: "2-digit",
      minute: "2-digit"
    }
  ).format(date);
}

function getLatestGeneratedAt(
  values: string[]
): string | null {
  const validDates =
    values
      .map(
        (value) => ({
          value,
          timestamp:
            new Date(
              value
            ).getTime()
        })
      )
      .filter(
        (item) =>
          Number.isFinite(
            item.timestamp
          )
      )
      .sort(
        (
          first,
          second
        ) =>
          second.timestamp -
          first.timestamp
      );

  return (
    validDates[0]
      ?.value ??
    null
  );
}

function ScoreRing({
  value
}: {
  value: number;
}) {
  const circumference =
    2 *
    Math.PI *
    26;

  const dash =
    circumference *
    (
      value /
      100
    );

  return (
    <div className="relative flex size-[70px] shrink-0 items-center justify-center">
      <svg
        viewBox="0 0 64 64"
        className="absolute inset-0 size-full -rotate-90"
        aria-hidden="true"
      >
        <circle
          cx="32"
          cy="32"
          r="26"
          fill="none"
          stroke="rgba(0,0,0,0.055)"
          strokeWidth="4"
        />

        <circle
          cx="32"
          cy="32"
          r="26"
          fill="none"
          stroke="#a7c43e"
          strokeWidth="4"
          strokeLinecap="round"
          strokeDasharray={`${dash} ${circumference}`}
        />
      </svg>

      <div className="relative flex items-end">
        <span className="text-[19px] font-bold leading-none tracking-[-0.055em] text-[#262624]">
          {value}
        </span>

        <span className="mb-[1px] ml-0.5 text-[8px] font-bold text-black/28">
          %
        </span>
      </div>
    </div>
  );
}

function SparkIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-3.5"
      aria-hidden="true"
    >
      <path
        d="M10 2.5c.47 3.03 1.97 4.53 5 5-3.03.47-4.53 1.97-5 5-.47-3.03-1.97-4.53-5-5 3.03-.47 4.53-1.97 5-5Z"
        stroke="currentColor"
        strokeWidth="1.35"
        strokeLinejoin="round"
      />

      <path
        d="M15.5 12.5c.2 1.29.84 1.93 2.13 2.13-1.29.2-1.93.84-2.13 2.13-.2-1.29-.84-1.93-2.13-2.13 1.29-.2 1.93-.84 2.13-2.13Z"
        fill="currentColor"
      />
    </svg>
  );
}

export function MatchingSummary({
  matching
}: MatchingSummaryProps) {
  const t =
    useTranslations(
      "user.matches"
    );

  const locale =
    useLocale();

  const scores =
    matching.results.map(
      (result) =>
        normalizeScore(
          result.score.finalScore
        )
    );

  const topScore =
    scores.length > 0
      ? Math.max(
          ...scores
        )
      : 0;

  const averageScore =
    scores.length > 0
      ? Math.round(
          scores.reduce(
            (
              total,
              score
            ) =>
              total +
              score,
            0
          ) /
            scores.length
        )
      : 0;

  const strongMatches =
    matching.results.filter(
      (result) =>
        result.matchTier ===
        "STRONG"
    ).length;

  const rankingVersion =
    matching.rankingVersion
      ?.trim() ||
    matching.results[0]
      ?.versions
      .rankingVersion
      ?.trim() ||
    null;

  const latestGeneratedAt =
    getLatestGeneratedAt(
      matching.results.map(
        (result) =>
          result.generatedAt
      )
    );

  const updatedAt =
    formatDate(
      latestGeneratedAt,
      locale
    );

  const description =
    matching.reusedExisting
      ? t(
          "summary.cached"
        )
      : t(
          "summary.fresh"
        );

  return (
    <section className="relative mt-7 overflow-hidden rounded-[24px] border border-black/[0.055] bg-white shadow-[0_8px_30px_rgba(0,0,0,0.025)]">
      <div className="absolute inset-x-0 top-0 h-[3px] bg-gradient-to-r from-[#d9ff75] via-[#c4e55d] to-transparent" />

      <div className="pointer-events-none absolute -right-20 -top-24 size-[260px] rounded-full bg-[#d9ff75]/20 blur-3xl" />

      <div className="relative grid xl:grid-cols-[minmax(0,1fr)_520px]">
        <div className="flex min-h-[210px] flex-col justify-between p-6 sm:p-7 lg:p-8">
          <div>
            <div className="flex items-center gap-3">
              <span className="text-[#95af31]">
                <SparkIcon />
              </span>

              <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.16em] text-black/28">
                {t(
                  "summary.eyebrow"
                )}
              </p>

              <span className="h-px w-10 bg-black/[0.07]" />

              <span className="size-1.5 rounded-full bg-[#b4d34d]" />
            </div>

            <h2 className="mt-6 text-[28px] font-bold leading-none tracking-[-0.05em] text-[#242422] sm:text-[31px]">
              {t(
                "summary.title"
              )}
            </h2>

            <p className="mt-4 max-w-[500px] text-[11px] leading-5 text-black/40">
              {description}
            </p>
          </div>

          {rankingVersion ? (
            <div className="mt-7">
              <span className="inline-flex items-center gap-2 rounded-full border border-black/[0.055] bg-[#fafaf7] px-3 py-2 font-mono text-[7px] font-semibold uppercase tracking-[0.1em] text-black/35">
                <span className="size-1.5 rounded-full bg-[#b4d34d]" />

                {
                  rankingVersion
                }
              </span>
            </div>
          ) : null}
        </div>

        <div className="grid grid-cols-2 border-t border-black/[0.05] bg-[#fafaf7]/70 xl:border-l xl:border-t-0">
          <div className="flex min-h-[105px] items-center justify-between gap-4 border-b border-r border-black/[0.05] px-5 py-5 sm:px-6">
            <div>
              <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.14em] text-black/25">
                {t(
                  "summary.topScore"
                )}
              </p>

              <p className="mt-3 text-[25px] font-bold leading-none tracking-[-0.05em] text-[#282826]">
                {topScore}%
              </p>
            </div>

            <ScoreRing
              value={
                topScore
              }
            />
          </div>

          <div className="min-h-[105px] border-b border-black/[0.05] px-5 py-5 sm:px-6">
            <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.14em] text-black/25">
              {t(
                "summary.strongMatches"
              )}
            </p>

            <div className="mt-4 flex items-end justify-between gap-4">
              <p className="text-[27px] font-bold leading-none tracking-[-0.055em] text-[#282826]">
                {
                  strongMatches
                }
              </p>

              <span className="mb-1 flex h-5 items-center rounded-full border border-[#d7e99a] bg-[#f2ffd0] px-2 text-[6.5px] font-bold uppercase tracking-[0.08em] text-[#637326]">
                {t(
                  "tiers.STRONG"
                )}
              </span>
            </div>
          </div>

          <div className="min-h-[105px] border-r border-black/[0.05] px-5 py-5 sm:px-6">
            <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.14em] text-black/25">
              {t(
                "summary.averageScore"
              )}
            </p>

            <div className="mt-4 flex items-end gap-2">
              <p className="text-[27px] font-bold leading-none tracking-[-0.055em] text-[#282826]">
                {
                  averageScore
                }%
              </p>

              <span className="mb-1.5 size-1.5 rounded-full bg-[#b5d354]" />
            </div>
          </div>

          <div className="min-h-[105px] px-5 py-5 sm:px-6">
            <p className="font-mono text-[7px] font-semibold uppercase tracking-[0.14em] text-black/25">
              {t(
                "summary.updatedAt"
              )}
            </p>

            <p className="mt-4 text-[14px] font-bold leading-5 tracking-[-0.025em] text-[#30302d]">
              {updatedAt ??
                "—"}
            </p>
          </div>
        </div>
      </div>
    </section>
  );
}