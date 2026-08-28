"use client";

import type {
  ReactNode
} from "react";

import {
  useTranslations
} from "next-intl";

import type {
  AdminJobRowProps,
  PipelineStageStatus,
  RawJobSummary
} from "@/types/admin-job";

import {
  formatDate
} from "./admin-ui";

import {
  CopyableId,
  JobPipelineCell
} from "./job-pipeline-ui";

const PIPELINE_STATUS_KEY = {
  NOT_CREATED: "pipeline.status.NOT_CREATED",
  OUTDATED: "pipeline.status.OUTDATED",
  PROCESSING: "pipeline.status.PROCESSING",
  READY: "pipeline.status.READY",
  FAILED: "pipeline.status.FAILED"
} as const satisfies Record<PipelineStageStatus, string>;

function ChevronIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4 transition-transform duration-200 group-open:rotate-180"
      aria-hidden="true"
    >
      <path
        d="m6 8 4 4 4-4"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function RefreshIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4 shrink-0"
      aria-hidden="true"
    >
      <path
        d="M15.2 7.1A5.8 5.8 0 1 0 15.5 12"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
      />

      <path
        d="M15.2 3.8v3.6h-3.6"
        stroke="currentColor"
        strokeWidth="1.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function VectorIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4 shrink-0"
      aria-hidden="true"
    >
      <circle
        cx="5"
        cy="10"
        r="1.5"
        stroke="currentColor"
        strokeWidth="1.4"
      />

      <circle
        cx="15"
        cy="6"
        r="1.5"
        stroke="currentColor"
        strokeWidth="1.4"
      />

      <circle
        cx="15"
        cy="14"
        r="1.5"
        stroke="currentColor"
        strokeWidth="1.4"
      />

      <path
        d="m6.4 9.4 7.2-2.8M6.4 10.6l7.2 2.8"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
      />
    </svg>
  );
}

function CompactPipelineStatus({
  status
}: {
  status: PipelineStageStatus;
}) {
  const t =
    useTranslations(
      "admin.jobs"
    );

  const statusKey =
    PIPELINE_STATUS_KEY[status];

  const styles = {
    NOT_CREATED:
      "border-black/[0.06] bg-white text-[#888881]",
    OUTDATED:
      "border-amber-200/80 bg-amber-50 text-amber-700",
    PROCESSING:
      "border-sky-200/80 bg-sky-50 text-sky-700",
    READY:
      "border-[#d8ed9f] bg-[#f9ffe9] text-[#59623b]",
    FAILED:
      "border-red-200/80 bg-red-50 text-red-700"
  }[status];

  const dotStyles = {
    NOT_CREATED:
      "bg-black/20",
    OUTDATED:
      "bg-amber-400",
    PROCESSING:
      "bg-sky-400",
    READY:
      "bg-[#cfff54]",
    FAILED:
      "bg-red-400"
  }[status];

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-1.5 py-[3px] font-mono text-[8px] font-semibold leading-none tracking-[0.02em] ${styles}`}
    >
      <span
        className={`size-1 shrink-0 rounded-full ${dotStyles}`}
      />

      {t(statusKey)}
    </span>
  );
}

function PipelineSummary({
  job
}: {
  job: RawJobSummary;
}) {
  const t =
    useTranslations(
      "admin.jobs"
    );

  return (
    <div className="w-full min-w-0 max-w-[220px]">
      <div className="relative min-h-[30px] pr-[86px]">
        <div className="flex items-center gap-2">
          <span className="size-1.5 shrink-0 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.06]" />

          <span className="font-mono text-[10px] font-semibold tracking-[0.07em] text-[#55554f]">
            {t(
              "pipeline.normalizationShort"
            )}
          </span>
        </div>

        <div className="absolute right-0 top-0">
          <CompactPipelineStatus
            status={
              job.normalizationStatus
            }
          />
        </div>
      </div>

      <div className="ml-[3px] h-3 w-px bg-black/[0.09]" />

      <div className="relative min-h-[30px] pr-[86px]">
        <div className="flex items-center gap-2">
          <span className="size-1.5 shrink-0 rounded-full bg-black/15" />

          <span className="font-mono text-[10px] font-semibold tracking-[0.07em] text-[#55554f]">
            {t(
              "pipeline.embeddingShort"
            )}
          </span>
        </div>

        <div className="absolute right-0 top-0">
          <CompactPipelineStatus
            status={
              job.embeddingStatus
            }
          />
        </div>
      </div>
    </div>
  );
}

function RawMetadata({
  label,
  children
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="min-w-0">
      <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.075em] text-[#74746e]">
        {label}
      </p>

      <div className="mt-1.5 min-w-0 text-[13px] font-medium leading-5 text-[#454540]">
        {children}
      </div>
    </div>
  );
}

function getNormalizeActionKey(
  status: PipelineStageStatus,
  pending: boolean
) {
  if (pending) {
    return "actions.normalizing" as const;
  }

  if (
    status ===
    "NOT_CREATED"
  ) {
    return "actions.normalize" as const;
  }

  if (
    status ===
    "FAILED"
  ) {
    return "actions.retryNormalize" as const;
  }

  return "actions.renormalize" as const;
}

function getEmbeddingActionKey(
  status: PipelineStageStatus,
  pending: boolean
) {
  if (pending) {
    return "actions.embedding" as const;
  }

  if (
    status ===
    "READY"
  ) {
    return "actions.reembed" as const;
  }

  if (
    status ===
    "FAILED"
  ) {
    return "actions.retryEmbed" as const;
  }

  if (
    status ===
    "PROCESSING"
  ) {
    return "actions.processing" as const;
  }

  return "actions.embed" as const;
}

export function AdminJobRow({
  job,
  index,
  locale,
  isNormalizing,
  isEmbedding,
  pending,
  onNormalize,
  onEmbed
}: AdminJobRowProps) {
  const t =
    useTranslations(
      "admin.jobs"
    );

  const canEmbed =
    job.normalizationStatus ===
      "READY" &&
    Boolean(
      job.normalizedJobId
    ) &&
    job.embeddingStatus !==
      "PROCESSING";

  const skills =
    job.skills ?? [];

  return (
    <details className="group">
      <summary className="cursor-pointer list-none px-5 py-5 transition-colors hover:bg-[#fafaf8]/80 group-open:bg-[#fafaf8]/75 sm:px-6 [&::-webkit-details-marker]:hidden xl:grid xl:grid-cols-[30px_minmax(0,1.55fr)_94px_minmax(0,0.9fr)_220px_125px_34px] xl:items-center xl:gap-4">
        <div className="xl:hidden">
          <div className="flex items-start gap-4">
            <span className="pt-0.5 font-mono text-[11px] font-medium text-[#74746e]">
              {String(
                index + 1
              ).padStart(
                2,
                "0"
              )}
            </span>

            <div className="min-w-0 flex-1">
              <h3 className="truncate text-[15px] font-semibold tracking-[-0.025em] text-[#20201e]">
                {job.title ||
                  t(
                    "rawJobs.untitled"
                  )}
              </h3>

              <p className="mt-1 truncate text-[13px] font-medium text-[#60605a]">
                {job.companyName ||
                  t(
                    "rawJobs.unknownCompany"
                  )}
              </p>

              <div className="mt-4">
                <PipelineSummary
                  job={job}
                />
              </div>
            </div>

            <span className="mt-1 flex size-8 shrink-0 items-center justify-center rounded-full border border-black/[0.07] bg-white text-[#555]">
              <ChevronIcon />
            </span>
          </div>
        </div>

        <span className="hidden font-mono text-[11px] font-medium text-[#74746e] xl:block">
          {String(
            index + 1
          ).padStart(
            2,
            "0"
          )}
        </span>

        <div className="hidden min-w-0 xl:block">
          <h3
            className="truncate text-[15px] font-semibold tracking-[-0.025em] text-[#20201e]"
            title={
              job.title ??
              undefined
            }
          >
            {job.title ||
              t(
                "rawJobs.untitled"
              )}
          </h3>

          <p
            className="mt-1 truncate text-[12px] font-medium text-[#60605a]"
            title={
              job.companyName ??
              undefined
            }
          >
            {job.companyName ||
              t(
                "rawJobs.unknownCompany"
              )}
          </p>
        </div>

        <div className="hidden min-w-0 xl:block">
          <span className="inline-flex max-w-full rounded-full border border-black/[0.07] bg-white px-2.5 py-1.5 font-mono text-[10px] font-semibold text-[#4f4f49]">
            <span className="truncate">
              {job.sourceCode ||
                "—"}
            </span>
          </span>
        </div>

        <p
          className="hidden min-w-0 truncate text-[12px] font-medium leading-5 text-[#565650] xl:block"
          title={
            job.locationText ??
            undefined
          }
        >
          {job.locationText ||
            "—"}
        </p>

        <div className="hidden min-w-0 xl:block">
          <PipelineSummary
            job={job}
          />
        </div>

        <p className="hidden text-[11px] font-medium leading-[18px] text-[#5f5f59] xl:block">
          {formatDate(
            job.collectedAt,
            locale
          )}
        </p>

        <span className="hidden size-8 items-center justify-center rounded-full border border-black/[0.07] bg-white text-[#555] transition group-hover:border-black/[0.12] group-hover:text-[#222] xl:flex">
          <ChevronIcon />
        </span>
      </summary>

      <div className="border-t border-black/[0.055] bg-[#fafaf7] px-5 py-6 sm:px-6">
        <div className="grid gap-7 xl:grid-cols-[minmax(0,1.35fr)_minmax(330px,0.95fr)_220px] xl:gap-0 xl:divide-x xl:divide-black/[0.065]">
          <section className="min-w-0 xl:pr-7">
            <div className="flex items-center justify-between gap-4">
              <p className="font-mono text-[11px] font-semibold uppercase tracking-[0.1em] text-[#55554f]">
                {t(
                  "rawJobs.details.rawJob"
                )}
              </p>

              <span className="font-mono text-[11px] font-medium text-[#5d5d57]">
                {job.sourceCode ||
                  "—"}
              </span>
            </div>

            <div className="mt-5">
              <RawMetadata
                label={t(
                  "rawJobs.details.rawJobId"
                )}
              >
                <CopyableId
                  label={t(
                    "rawJobs.details.rawJobId"
                  )}
                  value={job.id}
                />
              </RawMetadata>
            </div>

            <div className="mt-5 grid gap-x-7 gap-y-5 sm:grid-cols-2">
              <RawMetadata
                label={t(
                  "rawJobs.details.sourceJobId"
                )}
              >
                <span className="font-mono">
                  {job.sourceJobId ||
                    "—"}
                </span>
              </RawMetadata>

              <RawMetadata
                label={t(
                  "rawJobs.details.fingerprint"
                )}
              >
                <span
                  className="block truncate font-mono"
                  title={
                    job.fingerprint ??
                    undefined
                  }
                >
                  {job.fingerprint ||
                    "—"}
                </span>
              </RawMetadata>

              <RawMetadata
                label={t(
                  "rawJobs.details.location"
                )}
              >
                {job.locationText ||
                  "—"}
              </RawMetadata>

              <RawMetadata
                label={t(
                  "rawJobs.details.salary"
                )}
              >
                {job.salaryText ||
                  "—"}
              </RawMetadata>

              <RawMetadata
                label={t(
                  "rawJobs.details.experience"
                )}
              >
                {job.experienceText ||
                  "—"}
              </RawMetadata>

              <RawMetadata
                label={t(
                  "rawJobs.details.applyType"
                )}
              >
                {job.applyType ||
                  "—"}
              </RawMetadata>

              <RawMetadata
                label={t(
                  "rawJobs.details.firstSeenAt"
                )}
              >
                {formatDate(
                  job.firstSeenAt,
                  locale
                )}
              </RawMetadata>

              <RawMetadata
                label={t(
                  "rawJobs.details.lastSeenAt"
                )}
              >
                {formatDate(
                  job.lastSeenAt,
                  locale
                )}
              </RawMetadata>

              <RawMetadata
                label={t(
                  "rawJobs.details.collectedAt"
                )}
              >
                {formatDate(
                  job.collectedAt,
                  locale
                )}
              </RawMetadata>

              <RawMetadata
                label={t(
                  "rawJobs.details.rawPayloadPurgedAt"
                )}
              >
                {job.rawPayloadPurgedAt
                  ? formatDate(
                      job.rawPayloadPurgedAt,
                      locale
                    )
                  : "—"}
              </RawMetadata>
            </div>

            {skills.length >
            0 ? (
              <div className="mt-6">
                <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.075em] text-[#74746e]">
                  {t(
                    "rawJobs.details.skills"
                  )}
                </p>

                <div className="mt-2.5 flex flex-wrap gap-1.5">
                  {skills
                    .slice(
                      0,
                      8
                    )
                    .map(
                      (
                        skill
                      ) => (
                        <span
                          key={
                            skill
                          }
                          className="rounded-full border border-black/[0.07] bg-white px-2.5 py-1 text-[11px] font-medium text-[#4f4f49]"
                        >
                          {skill}
                        </span>
                      )
                    )}

                  {skills.length >
                  8 ? (
                    <span className="rounded-full border border-black/[0.07] bg-white px-2.5 py-1 text-[11px] font-medium text-[#666660]">
                      +
                      {skills.length -
                        8}
                    </span>
                  ) : null}
                </div>
              </div>
            ) : null}

            {job.detailUrl ||
            job.applyUrl ? (
              <div className="mt-6 flex flex-wrap gap-x-4 gap-y-2">
                {job.detailUrl ? (
                  <a
                    href={
                      job.detailUrl
                    }
                    target="_blank"
                    rel="noreferrer"
                    className="text-[12px] font-semibold text-[#3f3f3a] underline decoration-black/20 underline-offset-4 transition hover:text-black"
                  >
                    {t(
                      "rawJobs.details.openJob"
                    )}{" "}
                    ↗
                  </a>
                ) : null}

                {job.applyUrl ? (
                  <a
                    href={
                      job.applyUrl
                    }
                    target="_blank"
                    rel="noreferrer"
                    className="text-[12px] font-semibold text-[#3f3f3a] underline decoration-black/20 underline-offset-4 transition hover:text-black"
                  >
                    {t(
                      "rawJobs.details.openApply"
                    )}{" "}
                    ↗
                  </a>
                ) : null}
              </div>
            ) : null}
          </section>

          <section className="min-w-0 border-t border-black/[0.06] pt-6 xl:border-t-0 xl:px-7 xl:pt-0">
            <div className="flex items-center gap-2">
              <span className="size-1.5 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.06]" />

              <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.11em] text-[#777771]">
                {t(
                  "rawJobs.columns.pipeline"
                )}
              </p>
            </div>

            <div className="mt-4 space-y-3">
              <JobPipelineCell
                stage="normalization"
                status={
                  job.normalizationStatus
                }
                id={
                  job.normalizedJobId
                }
                version={
                  job.normalizationVersion
                }
                timestamp={
                  job.normalizedAt
                }
                locale={
                  locale
                }
              />

              <div className="flex justify-center py-0.5">
                <span
                  aria-hidden="true"
                  className="flex h-6 w-px bg-black/[0.08]"
                />
              </div>

              <JobPipelineCell
                stage="embedding"
                status={
                  job.embeddingStatus
                }
                id={
                  job.embeddingJobId
                }
                version={
                  job.embeddingVersion
                }
                timestamp={
                  job.embeddedAt
                }
                error={
                  job.embeddingLastError
                }
                locale={
                  locale
                }
              />
            </div>
          </section>

          <section className="border-t border-black/[0.06] pt-6 xl:border-t-0 xl:pl-7 xl:pt-0">
            <p className="font-mono text-[11px] font-semibold uppercase tracking-[0.1em] text-[#55554f]">
              {t(
                "actions.title"
              )}
            </p>

            <p className="mt-2 text-[12px] font-medium leading-5 text-[#5f5f59]">
              {t(
                "actions.description"
              )}
            </p>

            <div className="mt-5 space-y-2">
              <button
                type="button"
                onClick={() =>
                  onNormalize(
                    job
                  )
                }
                disabled={
                  pending
                }
                title={t(
                  "actions.normalizeTitle"
                )}
                className="flex h-10 w-full items-center justify-center gap-2 rounded-[10px] border border-black/[0.08] bg-white px-3 text-[12px] font-semibold text-[#3f3f3a] transition hover:border-black/[0.14] hover:bg-[#fafaf8] hover:text-[#171717] disabled:cursor-not-allowed disabled:opacity-35"
              >
                <RefreshIcon />

                <span>
                  {t(
                    getNormalizeActionKey(
                      job.normalizationStatus,
                      isNormalizing
                    )
                  )}
                </span>
              </button>

              <button
                type="button"
                onClick={() =>
                  onEmbed(
                    job
                  )
                }
                disabled={
                  pending ||
                  !canEmbed
                }
                title={
                  canEmbed
                    ? t(
                        "actions.embedTitle"
                      )
                    : t(
                        "actions.embedRequiresReady"
                      )
                }
                className="flex h-10 w-full items-center justify-center gap-2 rounded-[10px] border border-black/[0.07] bg-[#f1f1ed] px-3 text-[12px] font-semibold text-[#3f3f3a] transition hover:border-black/[0.13] hover:bg-white hover:text-[#171717] disabled:cursor-not-allowed disabled:opacity-35"
              >
                <VectorIcon />

                <span>
                  {t(
                    getEmbeddingActionKey(
                      job.embeddingStatus,
                      isEmbedding
                    )
                  )}
                </span>
              </button>
            </div>

            <div className="mt-4 rounded-[10px] border border-black/[0.055] bg-white/70 px-3 py-3">
              <p className="text-[11px] font-medium leading-[18px] text-[#686862]">
                {canEmbed
                  ? t(
                      "actions.embedUsesNormalizedId"
                    )
                  : t(
                      "actions.embedRequiresReady"
                    )}
              </p>
            </div>
          </section>
        </div>
      </div>
    </details>
  );
}