"use client";

import {
  useLocale,
  useTranslations
} from "next-intl";

import {
  ArrowRightIcon,
  ExternalLinkIcon,
  formatJobDate,
  JobMetaPill
} from "@/components/jobs/job-display";

import type {
  MatchTier
} from "@/types/matching";

import type {
  MatchResultCardProps
} from "@/types/matching-ui";

const TIER_STYLES: Record<
  MatchTier,
  string
> = {
  STRONG:
    "border-[#cce76e] bg-[#efffc3] text-[#35420f]",

  STRETCH:
    "border-[#e9d99f] bg-[#fff7d9] text-[#6b5715]",

  POSSIBLE:
    "border-[#cfd9df] bg-[#f2f6f8] text-[#43525a]",

  EXPLORE:
    "border-black/[0.07] bg-[#f4f4f0] text-[#686862]"
};

function toPercent(
  value: number
): number {
  if (!Number.isFinite(value)) {
    return 0;
  }

  return Math.round(
    Math.min(
      Math.max(value, 0),
      1
    ) * 100
  );
}

export function MatchResultCard({
  item,
  onOpen
}: MatchResultCardProps) {
  const t =
    useTranslations(
      "user.matches"
    );

  const tJobs =
    useTranslations(
      "user.jobs"
    );

  const locale =
    useLocale();

  const job =
    item.job;

  const finalScore =
    toPercent(
      item.score.finalScore
    );

  const location =
    job.locationText?.trim() ||
    job.locations
      .filter(Boolean)
      .join(" · ");

  const postedAt =
    formatJobDate(
      job.postedAt,
      locale
    );

  const deadlineAt =
    formatJobDate(
      job.deadlineAt,
      locale
    );

  const jobType =
    job.jobType &&
    job.jobType !== "UNKNOWN"
      ? tJobs(
          `jobTypes.${job.jobType}`
        )
      : null;

  const visibleMatchedSkills =
    item.matchedSkills.slice(
      0,
      6
    );

  const hiddenMatchedSkills =
    Math.max(
      item.matchedSkills.length -
        visibleMatchedSkills.length,
      0
    );

  const visibleMissingSkills =
    item.missingSkills.slice(
      0,
      4
    );

  const hiddenMissingSkills =
    Math.max(
      item.missingSkills.length -
        visibleMissingSkills.length,
      0
    );

  const applyUrl =
    job.applyUrl?.trim() ||
    job.detailUrl?.trim() ||
    null;

  const scoreRows = [
    {
      label: t(
        "score.semantic"
      ),

      value:
        item.score
          .semanticScore
    },

    {
      label: t(
        "score.skills"
      ),

      value:
        item.score
          .skillScore
    },

    {
      label: t(
        "score.seniority"
      ),

      value:
        item.score
          .seniorityScore
    },

    {
      label: t(
        "score.location"
      ),

      value:
        item.score
          .locationScore
    },

    {
      label: t(
        "score.freshness"
      ),

      value:
        item.score
          .freshnessScore
    }
  ];

  return (
    <article className="overflow-hidden rounded-[22px] border border-black/[0.055] bg-white shadow-[0_5px_22px_rgba(0,0,0,0.028)] transition-[transform,border-color,box-shadow] duration-200 hover:-translate-y-0.5 hover:border-black/[0.095] hover:shadow-[0_12px_34px_rgba(0,0,0,0.045)]">
      <div className="grid xl:grid-cols-[minmax(0,1fr)_285px]">
        <div className="min-w-0 p-5 sm:p-6">
          <div className="flex items-start justify-between gap-5">
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className="rounded-full border border-black/[0.06] bg-[#f7f7f3] px-2.5 py-1 font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/42">
                  {t(
                    "labels.rank",
                    {
                      rank:
                        item.rank
                    }
                  )}
                </span>

                <span
                  className={`rounded-full border px-2.5 py-1 text-[8px] font-semibold ${
                    TIER_STYLES[
                      item.matchTier
                    ]
                  }`}
                >
                  {t(
                    `tiers.${item.matchTier}`
                  )}
                </span>

                {job.sourceCode?.trim() ? (
                  <span className="font-mono text-[8px] font-medium uppercase tracking-[0.12em] text-black/28">
                    {job.sourceCode}
                  </span>
                ) : null}
              </div>

              <h2 className="mt-4 text-[20px] font-bold leading-[1.22] tracking-[-0.04em] text-[#222220] sm:text-[23px]">
                {job.title?.trim() ||
                  tJobs(
                    "untitled"
                  )}
              </h2>

              {job.companyName?.trim() ? (
                <p className="mt-2 text-[12px] font-semibold text-black/48">
                  {job.companyName}
                </p>
              ) : null}
            </div>

            <div className="relative flex size-[72px] shrink-0 items-center justify-center rounded-full border border-black/[0.065] bg-[#fafaf6]">
              <div className="absolute inset-[5px] rounded-full border border-black/[0.045]" />

              <div className="relative text-center">
                <p className="text-[23px] font-bold leading-none tracking-[-0.055em] text-[#20201e]">
                  {finalScore}
                </p>

                <p className="mt-1 font-mono text-[7px] font-semibold uppercase tracking-[0.1em] text-black/28">
                  {t(
                    "score.label"
                  )}
                </p>
              </div>
            </div>
          </div>

          {location ||
          jobType ||
          postedAt ||
          deadlineAt ? (
            <div className="mt-5 flex flex-wrap gap-2">
              {location ? (
                <JobMetaPill>
                  {location}
                </JobMetaPill>
              ) : null}

              {jobType ? (
                <JobMetaPill>
                  {jobType}
                </JobMetaPill>
              ) : null}

              {postedAt ? (
                <JobMetaPill>
                  {tJobs(
                    "postedShort",
                    {
                      date:
                        postedAt
                    }
                  )}
                </JobMetaPill>
              ) : null}

              {deadlineAt ? (
                <JobMetaPill>
                  {tJobs(
                    "deadlineShort",
                    {
                      date:
                        deadlineAt
                    }
                  )}
                </JobMetaPill>
              ) : null}
            </div>
          ) : null}

          {job.salaryText?.trim() ? (
            <p className="mt-4 text-[11px] font-semibold text-[#55554f]">
              {job.salaryText}
            </p>
          ) : null}

          {visibleMatchedSkills.length >
          0 ? (
            <div className="mt-6">
              <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-black/28">
                {t(
                  "labels.matchedSkills"
                )}
              </p>

              <div className="mt-2 flex flex-wrap gap-1.5">
                {visibleMatchedSkills.map(
                  (skill) => (
                    <span
                      key={skill}
                      className="rounded-[8px] border border-[#dbeaa5] bg-[#f3ffd6] px-2.5 py-1 text-[9px] font-medium text-[#4d5b20]"
                    >
                      {skill}
                    </span>
                  )
                )}

                {hiddenMatchedSkills >
                0 ? (
                  <span className="rounded-[8px] bg-[#f3f3ef] px-2.5 py-1 text-[9px] font-medium text-black/35">
                    {t(
                      "labels.moreSkills",
                      {
                        count:
                          hiddenMatchedSkills
                      }
                    )}
                  </span>
                ) : null}
              </div>
            </div>
          ) : null}

          {visibleMissingSkills.length >
          0 ? (
            <div className="mt-4">
              <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-black/28">
                {t(
                  "labels.missingSkills"
                )}
              </p>

              <div className="mt-2 flex flex-wrap gap-1.5">
                {visibleMissingSkills.map(
                  (skill) => (
                    <span
                      key={skill}
                      className="rounded-[8px] border border-black/[0.055] bg-[#f6f6f2] px-2.5 py-1 text-[9px] font-medium text-black/42"
                    >
                      {skill}
                    </span>
                  )
                )}

                {hiddenMissingSkills >
                0 ? (
                  <span className="rounded-[8px] bg-[#f3f3ef] px-2.5 py-1 text-[9px] font-medium text-black/35">
                    {t(
                      "labels.moreSkills",
                      {
                        count:
                          hiddenMissingSkills
                      }
                    )}
                  </span>
                ) : null}
              </div>
            </div>
          ) : null}

          {item.explanations.length >
          0 ? (
            <div className="mt-6 rounded-[15px] border border-black/[0.045] bg-[#fafaf7] px-4 py-4">
              <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-black/28">
                {t(
                  "labels.whyMatch"
                )}
              </p>

              <ul className="mt-2.5 space-y-2">
                {item.explanations
                  .slice(
                    0,
                    3
                  )
                  .map(
                    (
                      explanation,
                      index
                    ) => (
                      <li
                        key={`${index}-${explanation}`}
                        className="flex gap-2.5 text-[10px] leading-[17px] text-black/47"
                      >
                        <span className="mt-[6px] size-1 shrink-0 rounded-full bg-[#a8c747]" />

                        <span>
                          {explanation}
                        </span>
                      </li>
                    )
                  )}
              </ul>
            </div>
          ) : null}

          <div className="mt-6 flex flex-wrap items-center gap-2 border-t border-black/[0.05] pt-5">
            <button
              type="button"
              onClick={() =>
                onOpen(
                  item.normalizedJobId
                )
              }
              className="inline-flex h-10 items-center gap-2 rounded-full bg-[#171717] px-[18px] text-[10px] font-semibold text-white shadow-[0_4px_14px_rgba(0,0,0,0.08)] transition-[transform,box-shadow] hover:-translate-y-0.5 hover:shadow-[0_7px_18px_rgba(0,0,0,0.11)]"
            >
              {t(
                "actions.viewJob"
              )}

              <ArrowRightIcon />
            </button>

            {applyUrl ? (
              <a
                href={applyUrl}
                target="_blank"
                rel="noreferrer"
                className="inline-flex h-10 items-center gap-2 rounded-full border border-black/[0.065] bg-white px-[18px] text-[10px] font-semibold text-[#444] transition-colors hover:border-black/[0.12] hover:text-black"
              >
                {tJobs(
                  "detail.apply"
                )}

                <ExternalLinkIcon />
              </a>
            ) : null}
          </div>
        </div>

        <aside className="border-t border-black/[0.05] bg-[#fafaf7] p-5 sm:p-6 xl:border-l xl:border-t-0">
          <div className="flex items-center justify-between gap-4">
            <div>
              <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-black/28">
                {t(
                  "score.overall"
                )}
              </p>

              <p className="mt-1.5 text-[25px] font-bold tracking-[-0.05em] text-[#252523]">
                {finalScore}%
              </p>
            </div>

            <span className="size-2 rounded-full bg-[#d9ff75] ring-4 ring-[#d9ff75]/20" />
          </div>

          <div className="mt-6 space-y-4">
            {scoreRows.map(
              (row) => {
                const percent =
                  toPercent(
                    row.value
                  );

                return (
                  <div
                    key={
                      row.label
                    }
                  >
                    <div className="mb-1.5 flex items-center justify-between gap-3">
                      <span className="text-[9px] font-medium text-black/38">
                        {row.label}
                      </span>

                      <span className="font-mono text-[8px] font-semibold text-black/33">
                        {percent}%
                      </span>
                    </div>

                    <div className="h-1.5 overflow-hidden rounded-full bg-black/[0.055]">
                      <div
                        className="h-full rounded-full bg-[#1f1f1d]"
                        style={{
                          width:
                            `${percent}%`
                        }}
                      />
                    </div>
                  </div>
                );
              }
            )}
          </div>
        </aside>
      </div>
    </article>
  );
}