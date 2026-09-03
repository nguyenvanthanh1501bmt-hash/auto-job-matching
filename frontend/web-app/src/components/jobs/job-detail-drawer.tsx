"use client";

import {useEffect} from "react";
import {
  useLocale,
  useTranslations
} from "next-intl";

import {
  ExternalLinkIcon,
  formatJobDate,
  getJobExperience,
  getStructuredJobSalary,
  JobMetaPill
} from "@/components/jobs/job-display";

import {useNormalizedJob} from "@/hooks/use-jobs";

import {
  getApiErrorMessage
} from "@/lib/api-error";

type Props = {
  jobId: string | null;
  onClose: () => void;
};

function CloseIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4"
      aria-hidden="true"
    >
      <path
        d="m5.5 5.5 9 9m0-9-9 9"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  );
}

function DetailItem({
  label,
  value
}: {
  label: string;
  value: React.ReactNode;
}) {
  if (
    value === null ||
    value === undefined ||
    value === ""
  ) {
    return null;
  }

  return (
    <div className="rounded-[15px] border border-black/[0.05] bg-white px-4 py-3.5">
      <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/28">
        {label}
      </p>

      <div className="mt-1.5 text-[11px] font-medium leading-5 text-black/55">
        {value}
      </div>
    </div>
  );
}

function TextSection({
  title,
  content
}: {
  title: string;
  content: string | null;
}) {
  if (!content?.trim()) {
    return null;
  }

  return (
    <section className="border-t border-black/[0.055] pt-6">
      <h3 className="text-[13px] font-bold tracking-[-0.025em] text-[#292927]">
        {title}
      </h3>

      <p className="mt-3 whitespace-pre-line text-[12px] leading-6 text-black/52">
        {content}
      </p>
    </section>
  );
}

export function JobDetailDrawer({
  jobId,
  onClose
}: Props) {
  const t =
    useTranslations(
      "user.jobs"
    );

  const locale =
    useLocale();

  const query =
    useNormalizedJob(
      jobId
    );

  useEffect(() => {
    if (!jobId) {
      return;
    }

    function handleKeyDown(
      event: KeyboardEvent
    ) {
      if (
        event.key ===
        "Escape"
      ) {
        onClose();
      }
    }

    const oldOverflow =
      document.body.style
        .overflow;

    document.body.style.overflow =
      "hidden";

    document.addEventListener(
      "keydown",
      handleKeyDown
    );

    return () => {
      document.body.style.overflow =
        oldOverflow;

      document.removeEventListener(
        "keydown",
        handleKeyDown
      );
    };
  }, [
    jobId,
    onClose
  ]);

  if (!jobId) {
    return null;
  }

  const job =
    query.data;

  const jobType =
    job &&
    job.jobType !==
      "UNKNOWN"
      ? t(
          `jobTypes.${job.jobType}`
        )
      : null;

  const seniority =
    job &&
    job.seniority !==
      "UNKNOWN"
      ? t(
          `seniority.${job.seniority}`
        )
      : null;

  const experience =
    job
      ? getJobExperience(
          job
        )
      : null;

  const experienceText =
    experience?.type ===
    "range"
      ? t(
          "experience.range",
          {
            min:
              experience.min,
            max:
              experience.max
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

  const locations =
    job?.locations
      .filter(Boolean)
      .join(" · ") ||
    null;

  const locationText =
    job?.locationText?.trim() &&
    job.locationText.trim() !==
      locations
      ? job.locationText.trim()
      : null;

  const structuredSalary =
    job
      ? getStructuredJobSalary(
          job,
          locale
        )
      : null;

  const salaryText =
    job?.salaryText?.trim() ||
    null;

  const postedAt =
    job
      ? formatJobDate(
          job.postedAt,
          locale
        )
      : null;

  const deadlineAt =
    job
      ? formatJobDate(
          job.deadlineAt,
          locale
        )
      : null;

  const detailUrl =
    job?.detailUrl?.trim() ||
    null;

  const applyUrl =
    job?.applyUrl?.trim() ||
    detailUrl;

  const hasLongContent =
    Boolean(
      job?.descriptionText?.trim() ||
        job?.requirementsText?.trim() ||
        job?.benefitsText?.trim()
    );

  return (
    <div className="fixed inset-0 z-[200]">
      <button
        type="button"
        aria-label={t(
          "detail.close"
        )}
        onClick={onClose}
        className="absolute inset-0 bg-black/20 backdrop-blur-[2px]"
      />

      <aside className="absolute inset-y-0 right-0 flex w-full max-w-[720px] flex-col border-l border-black/[0.06] bg-[#f7f7f4] shadow-[-18px_0_60px_rgba(0,0,0,0.08)]">
        <header className="flex h-[76px] shrink-0 items-center justify-between border-b border-black/[0.055] px-5 sm:px-7">
          <div>
            <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.14em] text-black/30">
              {t(
                "detail.eyebrow"
              )}
            </p>

            <p className="mt-1 text-[11px] text-black/38">
              {t(
                "detail.subtitle"
              )}
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label={t(
              "detail.close"
            )}
            className="flex size-9 items-center justify-center rounded-full border border-black/[0.065] bg-white text-black/45 shadow-[0_2px_8px_rgba(0,0,0,0.025)] transition-colors hover:text-black"
          >
            <CloseIcon />
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto">
          {query.isLoading ? (
            <div className="flex min-h-[360px] items-center justify-center">
              <div className="flex items-center gap-3 text-[12px] text-black/42">
                <span className="size-4 animate-spin rounded-full border-2 border-black/10 border-t-black/60" />

                {t(
                  "detail.loading"
                )}
              </div>
            </div>
          ) : null}

          {query.isError ? (
            <div className="p-5 sm:p-7">
              <div className="rounded-[18px] border border-red-950/10 bg-white p-5">
                <p className="text-[12px] font-bold text-[#333]">
                  {t(
                    "detail.loadError"
                  )}
                </p>

                <p className="mt-2 text-[11px] leading-5 text-black/45">
                  {getApiErrorMessage(
                    query.error
                  )}
                </p>

                <button
                  type="button"
                  onClick={() =>
                    query.refetch()
                  }
                  className="mt-4 h-9 rounded-full bg-[#171717] px-4 text-[10px] font-semibold text-white"
                >
                  {t(
                    "tryAgain"
                  )}
                </button>
              </div>
            </div>
          ) : null}

          {job ? (
            <div className="px-5 py-7 sm:px-8 sm:py-9">
              <div className="flex flex-wrap items-center gap-2">
                {job.sourceCode?.trim() ? (
                  <span className="rounded-full bg-[#ecece7] px-2.5 py-1 font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/45">
                    {job.sourceCode}
                  </span>
                ) : null}

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
              </div>

              <h1 className="mt-5 max-w-[620px] text-[30px] font-bold leading-[1.06] tracking-[-0.055em] text-[#20201e] sm:text-[38px]">
                {job.title?.trim() ||
                  t("untitled")}
              </h1>

              {job.companyName?.trim() ? (
                <p className="mt-3 text-[14px] font-semibold text-black/48">
                  {job.companyName}
                </p>
              ) : null}

              <div className="mt-7 grid gap-3 sm:grid-cols-2">
                <DetailItem
                  label={t(
                    "fields.locations"
                  )}
                  value={locations}
                />

                <DetailItem
                  label={t(
                    "fields.locationText"
                  )}
                  value={
                    locationText
                  }
                />

                <DetailItem
                  label={t(
                    "fields.jobType"
                  )}
                  value={jobType}
                />

                <DetailItem
                  label={t(
                    "fields.seniority"
                  )}
                  value={
                    seniority
                  }
                />

                <DetailItem
                  label={t(
                    "fields.experience"
                  )}
                  value={
                    experienceText
                  }
                />

                <DetailItem
                  label={t(
                    "fields.salary"
                  )}
                  value={
                    salaryText ||
                    structuredSalary ? (
                      <div className="space-y-1">
                        {salaryText ? (
                          <p>
                            {salaryText}
                          </p>
                        ) : null}

                        {structuredSalary &&
                        structuredSalary !==
                          salaryText ? (
                          <p className="text-black/38">
                            {
                              structuredSalary
                            }
                          </p>
                        ) : null}
                      </div>
                    ) : null
                  }
                />

                <DetailItem
                  label={t(
                    "fields.postedAt"
                  )}
                  value={
                    postedAt
                  }
                />

                <DetailItem
                  label={t(
                    "fields.deadlineAt"
                  )}
                  value={
                    deadlineAt
                  }
                />

                <DetailItem
                  label={t(
                    "fields.applyType"
                  )}
                  value={
                    job.applyType !==
                    "UNKNOWN"
                      ? t(
                          `applyTypes.${job.applyType}`
                        )
                      : null
                  }
                />
              </div>

              {job.skills.filter(
                Boolean
              ).length > 0 ? (
                <section className="mt-7">
                  <h3 className="text-[13px] font-bold tracking-[-0.025em] text-[#292927]">
                    {t(
                      "fields.skills"
                    )}
                  </h3>

                  <div className="mt-3 flex flex-wrap gap-2">
                    {job.skills
                      .filter(
                        Boolean
                      )
                      .map(
                        (
                          skill
                        ) => (
                          <span
                            key={skill}
                            className="rounded-[9px] bg-[#ecece7] px-3 py-1.5 text-[10px] font-semibold text-[#565651]"
                          >
                            {skill}
                          </span>
                        )
                      )}
                  </div>
                </section>
              ) : null}

              <div className="mt-8 space-y-7">
                <TextSection
                  title={t(
                    "fields.description"
                  )}
                  content={
                    job.descriptionText
                  }
                />

                <TextSection
                  title={t(
                    "fields.requirements"
                  )}
                  content={
                    job.requirementsText
                  }
                />

                <TextSection
                  title={t(
                    "fields.benefits"
                  )}
                  content={
                    job.benefitsText
                  }
                />
              </div>

              {!hasLongContent ? (
                <div className="mt-8 rounded-[16px] border border-black/[0.05] bg-white p-5 text-[11px] leading-5 text-black/42">
                  {t(
                    "detail.noLongContent"
                  )}
                </div>
              ) : null}

              {detailUrl ? (
                <div className="mt-8 border-t border-black/[0.055] pt-6">
                  <a
                    href={detailUrl}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-2 text-[11px] font-semibold text-black/55 transition-colors hover:text-black"
                  >
                    {t(
                      "detail.viewSource"
                    )}

                    <ExternalLinkIcon />
                  </a>
                </div>
              ) : null}

              <div className="h-24" />
            </div>
          ) : null}
        </div>

        {job ? (
          <footer className="shrink-0 border-t border-black/[0.055] bg-[#f7f7f4]/95 p-4 backdrop-blur-xl sm:px-7 sm:py-5">
            <div className="flex items-center justify-between gap-4">
              <div className="hidden min-w-0 sm:block">
                <p className="font-mono text-[8px] font-semibold uppercase tracking-[0.12em] text-black/28">
                  {t(
                    "detail.applicationSource"
                  )}
                </p>

                <p className="mt-1 truncate text-[10px] text-black/40">
                  {applyUrl
                    ? t(
                        "detail.applicationAvailable"
                      )
                    : t(
                        "detail.applicationUnavailable"
                      )}
                </p>
              </div>

              {applyUrl ? (
                <a
                  href={applyUrl}
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex h-11 w-full items-center justify-center gap-2 rounded-full bg-[#171717] px-5 text-[11px] font-semibold text-white shadow-[0_6px_20px_rgba(0,0,0,0.1)] transition-transform hover:-translate-y-0.5 sm:w-auto"
                >
                  {t(
                    "detail.apply"
                  )}

                  <ExternalLinkIcon />
                </a>
              ) : (
                <button
                  type="button"
                  disabled
                  className="h-11 w-full cursor-not-allowed rounded-full bg-black/8 px-5 text-[11px] font-semibold text-black/30 sm:w-auto"
                >
                  {t(
                    "detail.noApplyLink"
                  )}
                </button>
              )}
            </div>
          </footer>
        ) : null}
      </aside>
    </div>
  );
}