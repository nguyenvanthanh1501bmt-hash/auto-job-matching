"use client";

import {
  useLocale,
  useTranslations
} from "next-intl";

import {
  ArrowRightIcon,
  getJobExperience,
  getStructuredJobSalary,
  JobMetaPill
} from "@/components/jobs/job-display";

import type {
  NormalizedJobSummary
} from "@/types/job";

type Props = {
  job: NormalizedJobSummary;
  onOpen: (jobId: string) => void;
};

export function JobCard({
  job,
  onOpen
}: Props) {
  const t =
    useTranslations(
      "user.jobs"
    );

  const locale =
    useLocale();

  const jobType =
    job.jobType !== "UNKNOWN"
      ? t(
          `jobTypes.${job.jobType}`
        )
      : null;

  const seniority =
    job.seniority !== "UNKNOWN"
      ? t(
          `seniority.${job.seniority}`
        )
      : null;

  const experience =
    getJobExperience(job);

  const experienceText =
    experience?.type === "range"
      ? t(
          "experience.range",
          {
            min: experience.min,
            max: experience.max
          }
        )
      : experience?.type ===
          "minimum"
        ? t(
            "experience.minimum",
            {
              value:
                experience.value
            }
          )
        : experience?.type ===
            "maximum"
          ? t(
              "experience.maximum",
              {
                value:
                  experience.value
              }
            )
          : null;

  const postedAt =
    job.postedAt
      ? new Intl.DateTimeFormat(
          locale === "vi"
            ? "vi-VN"
            : "en-US",
          {
            day: "2-digit",
            month: "short",
            year: "numeric"
          }
        ).format(
          new Date(
            job.postedAt
          )
        )
      : null;

  const deadlineAt =
    job.deadlineAt
      ? new Intl.DateTimeFormat(
          locale === "vi"
            ? "vi-VN"
            : "en-US",
          {
            day: "2-digit",
            month: "short",
            year: "numeric"
          }
        ).format(
          new Date(
            job.deadlineAt
          )
        )
      : null;

  const structuredSalary =
    getStructuredJobSalary(
      job,
      locale
    );

  const salary =
    job.salaryText?.trim() ||
    structuredSalary;

  const location =
    job.locations
      .filter(Boolean)
      .join(" · ");

  const skills =
    job.skills.filter(Boolean);

  const visibleSkills =
    skills.slice(0, 5);

  const hiddenSkillCount =
    Math.max(
      skills.length -
        visibleSkills.length,
      0
    );

  return (
    <article className="group flex h-full flex-col rounded-[20px] border border-black/[0.055] bg-white p-5 shadow-[0_4px_18px_rgba(0,0,0,0.025)] transition-[transform,border-color,box-shadow] duration-200 hover:-translate-y-0.5 hover:border-black/[0.095] hover:shadow-[0_10px_30px_rgba(0,0,0,0.045)] sm:p-6">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="mb-3 flex flex-wrap items-center gap-2">
            {job.sourceCode?.trim() ? (
              <span className="rounded-full bg-[#f2f2ee] px-2.5 py-1 font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/45">
                {job.sourceCode}
              </span>
            ) : null}

            {postedAt ? (
              <span className="text-[10px] text-black/32">
                {t(
                  "postedShort",
                  {
                    date:
                      postedAt
                  }
                )}
              </span>
            ) : null}
          </div>

          <h2 className="line-clamp-2 text-[19px] font-bold leading-[1.25] tracking-[-0.035em] text-[#20201e] sm:text-[21px]">
            {job.title?.trim() ||
              t("untitled")}
          </h2>

          {job.companyName?.trim() ? (
            <p className="mt-2 truncate text-[12px] font-semibold text-black/48">
              {job.companyName}
            </p>
          ) : null}
        </div>

        <div className="flex size-10 shrink-0 items-center justify-center rounded-[13px] border border-black/[0.055] bg-[#fafaf7] text-[13px] font-bold tracking-[-0.04em] text-black/55">
          {(
            job.companyName?.trim() ||
            job.title?.trim() ||
            "A"
          )
            .charAt(0)
            .toUpperCase()}
        </div>
      </div>

      {location ||
      jobType ||
      seniority ||
      experienceText ||
      deadlineAt ? (
        <div className="mt-5 space-y-3">
          {location ? (
            <p className="truncate text-[11px] leading-5 text-black/45">
              {location}
            </p>
          ) : null}

          <div className="flex flex-wrap gap-2">
            {jobType ? (
              <JobMetaPill>
                {jobType}
              </JobMetaPill>
            ) : null}

            {seniority ? (
              <JobMetaPill>
                {seniority}
              </JobMetaPill>
            ) : null}

            {experienceText ? (
              <JobMetaPill>
                {experienceText}
              </JobMetaPill>
            ) : null}

            {deadlineAt ? (
              <JobMetaPill>
                {t(
                  "deadlineShort",
                  {
                    date:
                      deadlineAt
                  }
                )}
              </JobMetaPill>
            ) : null}
          </div>
        </div>
      ) : null}

      {visibleSkills.length >
      0 ? (
        <div className="mt-5">
          <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-black/28">
            {t(
              "fields.skills"
            )}
          </p>

          <div className="mt-2 flex flex-wrap gap-1.5">
            {visibleSkills.map(
              (skill) => (
                <span
                  key={skill}
                  className="rounded-[8px] bg-[#f3f3ef] px-2.5 py-1 text-[9px] font-medium text-[#595954]"
                >
                  {skill}
                </span>
              )
            )}

            {hiddenSkillCount >
            0 ? (
              <span className="rounded-[8px] bg-[#f3f3ef] px-2.5 py-1 text-[9px] font-medium text-black/35">
                +{hiddenSkillCount}
              </span>
            ) : null}
          </div>
        </div>
      ) : null}

      <div className="mt-auto pt-6">
        <div className="mb-4 h-px bg-black/[0.05]" />

        <div className="flex items-end justify-between gap-4">
          <div className="min-w-0">
            {salary ? (
              <>
                <p className="font-mono text-[8px] font-medium uppercase tracking-[0.12em] text-black/28">
                  {t(
                    "fields.salary"
                  )}
                </p>

                <p className="mt-1 line-clamp-2 text-[11px] font-semibold leading-4 text-[#55554f]">
                  {salary}
                </p>
              </>
            ) : null}
          </div>

          <button
            type="button"
            onClick={() =>
              onOpen(job.id)
            }
            className="inline-flex h-9 shrink-0 items-center gap-2 rounded-full bg-[#171717] px-4 text-[10px] font-semibold text-white shadow-[0_4px_14px_rgba(0,0,0,0.08)] transition-[transform,box-shadow] duration-200 hover:-translate-y-0.5 hover:shadow-[0_7px_18px_rgba(0,0,0,0.11)]"
          >
            {t("viewJob")}

            <ArrowRightIcon />
          </button>
        </div>
      </div>
    </article>
  );
}