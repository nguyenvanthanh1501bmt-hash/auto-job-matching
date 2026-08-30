"use client";

import type {ReactNode} from "react";
import {useTranslations} from "next-intl";

import type {
  AdminJobRowProps,
  PipelineStageStatus,
  RawJobSummary
} from "@/types/admin-job";

import {formatDate} from "./admin-ui";
import {
  CopyableId,
  JobPipelineCell,
  PipelineStatusBadge
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
    <svg viewBox="0 0 20 20" fill="none" className="size-4 transition-transform duration-200 group-open:rotate-180" aria-hidden="true">
      <path d="m6 8 4 4 4-4" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function RefreshIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="none" className="size-4 shrink-0" aria-hidden="true">
      <path d="M15.2 7.1A5.8 5.8 0 1 0 15.5 12" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <path d="M15.2 3.8v3.6h-3.6" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function VectorIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="none" className="size-4 shrink-0" aria-hidden="true">
      <circle cx="5" cy="10" r="1.5" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="15" cy="6" r="1.5" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="15" cy="14" r="1.5" stroke="currentColor" strokeWidth="1.4" />
      <path d="m6.4 9.4 7.2-2.8M6.4 10.6l7.2 2.8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

function ExternalIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="none" className="size-3.5" aria-hidden="true">
      <path d="M8 5h7v7M15 5l-8 8" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M13 11v3a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h3" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
    </svg>
  );
}

function BriefcaseIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" className="size-[17px]" aria-hidden="true">
      <rect x="4" y="7" width="16" height="12" rx="2.5" stroke="currentColor" strokeWidth="1.6" />
      <path d="M9 7V5.8C9 4.8 9.8 4 10.8 4h2.4C14.2 4 15 4.8 15 5.8V7M4 11.5h16" stroke="currentColor" strokeWidth="1.6" />
    </svg>
  );
}

function CompactPipelineStatus({status}: {status: PipelineStageStatus}) {
  const t = useTranslations("admin.jobs");

  const styles = {
    NOT_CREATED: "border-black/[0.06] bg-white text-[#888881]",
    OUTDATED: "border-amber-200/80 bg-amber-50 text-amber-700",
    PROCESSING: "border-sky-200/80 bg-sky-50 text-sky-700",
    READY: "border-[#d8ed9f] bg-[#f9ffe9] text-[#59623b]",
    FAILED: "border-red-200/80 bg-red-50 text-red-700"
  }[status];

  const dotStyles = {
    NOT_CREATED: "bg-black/20",
    OUTDATED: "bg-amber-400",
    PROCESSING: "bg-sky-400",
    READY: "bg-[#c7f34f]",
    FAILED: "bg-red-400"
  }[status];

  return (
    <span className={`inline-flex items-center gap-1 rounded-full border px-1.5 py-[3px] font-mono text-[8px] font-semibold leading-none tracking-[0.02em] ${styles}`}>
      <span className={`size-1 shrink-0 rounded-full ${dotStyles}`} />
      {t(PIPELINE_STATUS_KEY[status])}
    </span>
  );
}

function PipelineSummary({job}: {job: RawJobSummary}) {
  const t = useTranslations("admin.jobs");

  return (
    <div className="w-full min-w-0 max-w-[250px] space-y-2">
      <div className="grid grid-cols-[48px_minmax(24px,1fr)_auto] items-center gap-2">
        <span className="font-mono text-[9px] font-semibold uppercase tracking-[0.07em] text-[#666660]">
          {t("pipeline.normalizationShort")}
        </span>
        <span className={`h-px ${job.normalizationStatus === "READY" ? "bg-gradient-to-r from-[#c7f34f] to-black/[0.08]" : "bg-black/[0.09]"}`} />
        <CompactPipelineStatus status={job.normalizationStatus} />
      </div>

      <div className="grid grid-cols-[48px_minmax(24px,1fr)_auto] items-center gap-2">
        <span className="font-mono text-[9px] font-semibold uppercase tracking-[0.07em] text-[#666660]">
          {t("pipeline.embeddingShort")}
        </span>
        <span className={`h-px ${job.embeddingStatus === "READY" ? "bg-gradient-to-r from-[#c7f34f] to-black/[0.08]" : "bg-black/[0.09]"}`} />
        <CompactPipelineStatus status={job.embeddingStatus} />
      </div>
    </div>
  );
}

function RawMetadata({label, children}: {label: string; children: ReactNode}) {
  return (
    <div className="min-w-0 border-b border-black/[0.045] py-3 last:border-b-0">
      <p className="font-mono text-[9px] font-semibold uppercase tracking-[0.08em] text-[#8c8c86]">
        {label}
      </p>
      <div className="mt-1.5 min-w-0 text-[12px] font-medium leading-5 text-[#464641]">
        {children}
      </div>
    </div>
  );
}

function getNormalizeActionKey(status: PipelineStageStatus, pending: boolean) {
  if (pending) return "actions.normalizing" as const;
  if (status === "NOT_CREATED") return "actions.normalize" as const;
  if (status === "FAILED") return "actions.retryNormalize" as const;
  return "actions.renormalize" as const;
}

function getEmbeddingActionKey(status: PipelineStageStatus, pending: boolean) {
  if (pending) return "actions.embedding" as const;
  if (status === "READY") return "actions.reembed" as const;
  if (status === "FAILED") return "actions.retryEmbed" as const;
  if (status === "PROCESSING") return "actions.processing" as const;
  return "actions.embed" as const;
}

function getOverallStatus(job: RawJobSummary): PipelineStageStatus {
  if (job.normalizationStatus === "FAILED" || job.embeddingStatus === "FAILED") {
    return "FAILED";
  }

  if (job.normalizationStatus === "PROCESSING" || job.embeddingStatus === "PROCESSING") {
    return "PROCESSING";
  }

  if (job.normalizationStatus === "OUTDATED" || job.embeddingStatus === "OUTDATED") {
    return "OUTDATED";
  }

  if (job.normalizationStatus === "READY" && job.embeddingStatus === "READY") {
    return "READY";
  }

  return "NOT_CREATED";
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
  const t = useTranslations("admin.jobs");

  const canEmbed =
    job.normalizationStatus === "READY" &&
    Boolean(job.normalizedJobId) &&
    job.embeddingStatus !== "PROCESSING";

  const skills = job.skills ?? [];
  const overallStatus = getOverallStatus(job);

  return (
    <details className="group relative">
      <summary className="relative cursor-pointer list-none px-5 py-5 transition-colors duration-200 hover:bg-[#fafaf8]/80 group-open:bg-[#fbfbf8] sm:px-6 [&::-webkit-details-marker]:hidden xl:grid xl:grid-cols-[30px_minmax(0,1.55fr)_94px_minmax(0,0.9fr)_250px_125px_34px] xl:items-center xl:gap-4">
        <span className="pointer-events-none absolute inset-y-3 left-0 w-[2px] origin-center scale-y-0 rounded-r-full bg-[#c7f34f] transition-transform duration-200 group-open:scale-y-100" />

        <div className="xl:hidden">
          <div className="flex items-start gap-3.5">
            <span className="flex size-8 shrink-0 items-center justify-center rounded-[9px] border border-black/[0.06] bg-[#fafaf8] font-mono text-[9px] font-semibold text-[#777771]">
              {String(index + 1).padStart(2, "0")}
            </span>

            <div className="min-w-0 flex-1">
              <h3 className="truncate text-[15px] font-semibold tracking-[-0.025em] text-[#20201e]">
                {job.title || t("rawJobs.untitled")}
              </h3>
              <p className="mt-1 truncate text-[12px] font-medium text-[#676761]">
                {job.companyName || t("rawJobs.unknownCompany")}
              </p>

              <div className="mt-3 flex flex-wrap items-center gap-2">
                <span className="inline-flex items-center gap-1.5 rounded-full border border-black/[0.065] bg-white px-2.5 py-1 font-mono text-[9px] font-semibold text-[#565650]">
                  <span className="size-1.5 rounded-full bg-[#c7f34f]" />
                  {job.sourceCode || "—"}
                </span>
                <PipelineStatusBadge status={overallStatus} />
              </div>
            </div>

            <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full border border-black/[0.07] bg-white text-[#555] shadow-[0_2px_8px_rgba(0,0,0,0.025)]">
              <ChevronIcon />
            </span>
          </div>
        </div>

        <span className="hidden font-mono text-[10px] font-medium text-[#74746e] xl:block">
          {String(index + 1).padStart(2, "0")}
        </span>

        <div className="hidden min-w-0 xl:block">
          <h3 className="truncate text-[14px] font-semibold tracking-[-0.02em] text-[#20201e]" title={job.title ?? undefined}>
            {job.title || t("rawJobs.untitled")}
          </h3>
          <p className="mt-1 truncate text-[11px] font-medium text-[#686862]" title={job.companyName ?? undefined}>
            {job.companyName || t("rawJobs.unknownCompany")}
          </p>
        </div>

        <div className="hidden min-w-0 xl:block">
          <span className="inline-flex max-w-full items-center gap-1.5 rounded-full border border-black/[0.065] bg-white px-2.5 py-1.5 font-mono text-[9px] font-semibold text-[#4f4f49]">
            <span className="size-1.5 shrink-0 rounded-full bg-[#c7f34f]" />
            <span className="truncate">{job.sourceCode || "—"}</span>
          </span>
        </div>

        <p className="hidden min-w-0 truncate text-[11px] font-medium leading-5 text-[#565650] xl:block" title={job.locationText ?? undefined}>
          {job.locationText || "—"}
        </p>

        <div className="hidden min-w-0 xl:block">
          <PipelineSummary job={job} />
        </div>

        <p className="hidden text-[10px] font-medium leading-[17px] text-[#686862] xl:block">
          {formatDate(job.collectedAt, locale)}
        </p>

        <span className="hidden size-8 items-center justify-center rounded-full border border-black/[0.07] bg-white text-[#555] shadow-[0_2px_8px_rgba(0,0,0,0.02)] transition group-hover:border-black/[0.12] group-hover:text-[#222] xl:flex">
          <ChevronIcon />
        </span>
      </summary>

      <div className="relative overflow-hidden border-t border-black/[0.055] bg-[#fafaf8]/80 px-5 py-6 sm:px-6">
        <div aria-hidden="true" className="pointer-events-none absolute -right-24 -top-24 size-64 rounded-full bg-[#d9ff75]/[0.075] blur-3xl" />

        <div className="relative mb-5 flex flex-col gap-4 rounded-[16px] border border-black/[0.055] bg-white px-4 py-4 shadow-[0_7px_24px_rgba(0,0,0,0.02)] sm:flex-row sm:items-center sm:justify-between sm:px-5">
          <div className="flex min-w-0 items-center gap-3.5">
            <div className="flex size-10 shrink-0 items-center justify-center rounded-[11px] bg-[#20201e] text-[#d9ff75] shadow-[0_4px_12px_rgba(0,0,0,0.1)]">
              <BriefcaseIcon />
            </div>

            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-mono text-[8px] font-semibold uppercase tracking-[0.13em] text-[#989892]">
                  {t("rawJobs.details.rawJob")}
                </span>
                <span className="size-1 rounded-full bg-black/15" />
                <span className="font-mono text-[8px] font-semibold uppercase tracking-[0.1em] text-[#6f6f69]">
                  {job.sourceCode || "—"}
                </span>
              </div>
              <h4 className="mt-1.5 truncate text-[17px] font-semibold tracking-[-0.03em] text-[#242422]">
                {job.title || t("rawJobs.untitled")}
              </h4>
              <p className="mt-0.5 truncate text-[11px] font-medium text-[#777771]">
                {job.companyName || t("rawJobs.unknownCompany")}
              </p>
            </div>
          </div>

          <div className="flex shrink-0 flex-wrap items-center gap-2 sm:justify-end">
            <PipelineStatusBadge status={overallStatus} />
            <span className="rounded-full border border-black/[0.055] bg-[#fafaf8] px-2.5 py-1.5 font-mono text-[8px] font-medium text-[#777771]">
              {formatDate(job.collectedAt, locale)}
            </span>
          </div>
        </div>

        <div className="relative grid gap-5 xl:grid-cols-[minmax(0,1.35fr)_minmax(330px,0.95fr)_230px] xl:gap-5">
          <section className="min-w-0 overflow-hidden rounded-[16px] border border-black/[0.055] bg-white shadow-[0_7px_24px_rgba(0,0,0,0.018)]">
            <div className="flex flex-col gap-3 border-b border-black/[0.05] px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-5">
              <div>
                <div className="flex items-center gap-2.5">
                  <span className="font-mono text-[9px] font-semibold uppercase tracking-[0.13em] text-[#85857f]">
                    {t("rawJobs.workspace")}
                  </span>
                  <span className="h-px w-8 bg-black/[0.08]" />
                </div>
                <h5 className="mt-1.5 text-[14px] font-semibold tracking-[-0.02em] text-[#292927]">
                  {t("rawJobs.details.rawJob")}
                </h5>
              </div>

              <div className="min-w-0 rounded-[10px] border border-black/[0.055] bg-[#fafaf8] px-2.5 py-1.5 sm:max-w-[300px]">
                <CopyableId label={t("rawJobs.details.rawJobId")} value={job.id} />
              </div>
            </div>

            <div className="grid gap-x-6 px-4 sm:grid-cols-2 sm:px-5">
              <div>
                <RawMetadata label={t("rawJobs.details.sourceJobId")}>
                  <span className="font-mono">{job.sourceJobId || "—"}</span>
                </RawMetadata>
                <RawMetadata label={t("rawJobs.details.location")}>
                  {job.locationText || "—"}
                </RawMetadata>
                <RawMetadata label={t("rawJobs.details.experience")}>
                  {job.experienceText || "—"}
                </RawMetadata>
                <RawMetadata label={t("rawJobs.details.firstSeenAt")}>
                  {formatDate(job.firstSeenAt, locale)}
                </RawMetadata>
                <RawMetadata label={t("rawJobs.details.collectedAt")}>
                  {formatDate(job.collectedAt, locale)}
                </RawMetadata>
              </div>

              <div>
                <RawMetadata label={t("rawJobs.details.fingerprint")}>
                  <span className="block truncate font-mono" title={job.fingerprint ?? undefined}>
                    {job.fingerprint || "—"}
                  </span>
                </RawMetadata>
                <RawMetadata label={t("rawJobs.details.salary")}>
                  {job.salaryText || "—"}
                </RawMetadata>
                <RawMetadata label={t("rawJobs.details.applyType")}>
                  {job.applyType || "—"}
                </RawMetadata>
                <RawMetadata label={t("rawJobs.details.lastSeenAt")}>
                  {formatDate(job.lastSeenAt, locale)}
                </RawMetadata>
                <RawMetadata label={t("rawJobs.details.rawPayloadPurgedAt")}>
                  {job.rawPayloadPurgedAt ? formatDate(job.rawPayloadPurgedAt, locale) : "—"}
                </RawMetadata>
              </div>
            </div>

            {skills.length > 0 || job.detailUrl || job.applyUrl ? (
              <div className="border-t border-black/[0.05] px-4 py-4 sm:px-5">
                {skills.length > 0 ? (
                  <div>
                    <p className="font-mono text-[9px] font-semibold uppercase tracking-[0.08em] text-[#85857f]">
                      {t("rawJobs.details.skills")}
                    </p>
                    <div className="mt-2.5 flex flex-wrap gap-1.5">
                      {skills.slice(0, 8).map((skill) => (
                        <span key={skill} className="rounded-full border border-black/[0.06] bg-[#fafaf8] px-2.5 py-1 text-[10px] font-medium text-[#4f4f49] transition hover:border-black/[0.11] hover:bg-white">
                          {skill}
                        </span>
                      ))}
                      {skills.length > 8 ? (
                        <span className="rounded-full border border-black/[0.06] bg-[#f1f1ed] px-2.5 py-1 font-mono text-[9px] font-semibold text-[#666660]">
                          +{skills.length - 8}
                        </span>
                      ) : null}
                    </div>
                  </div>
                ) : null}

                {job.detailUrl || job.applyUrl ? (
                  <div className={`${skills.length > 0 ? "mt-4" : ""} flex flex-wrap gap-2`}>
                    {job.detailUrl ? (
                      <a href={job.detailUrl} target="_blank" rel="noreferrer" className="inline-flex h-9 items-center gap-2 rounded-[10px] border border-black/[0.07] bg-white px-3 text-[10px] font-semibold text-[#40403b] transition hover:border-black/[0.13] hover:bg-[#fafaf8]">
                        {t("rawJobs.details.openJob")}
                        <ExternalIcon />
                      </a>
                    ) : null}

                    {job.applyUrl ? (
                      <a href={job.applyUrl} target="_blank" rel="noreferrer" className="inline-flex h-9 items-center gap-2 rounded-[10px] border border-black/[0.07] bg-[#f3f3ef] px-3 text-[10px] font-semibold text-[#353531] transition hover:border-black/[0.13] hover:bg-white">
                        {t("rawJobs.details.openApply")}
                        <ExternalIcon />
                      </a>
                    ) : null}
                  </div>
                ) : null}
              </div>
            ) : null}
          </section>

          <section className="min-w-0">
            <div className="mb-3 flex items-start justify-between gap-3 px-0.5">
              <div>
                <div className="flex items-center gap-2.5">
                  <span className="size-1.5 rounded-full bg-[#c7f34f] ring-[3px] ring-[#d9ff75]/15" />
                  <p className="font-mono text-[9px] font-semibold uppercase tracking-[0.13em] text-[#85857f]">
                    {t("rawJobs.columns.pipeline")}
                  </p>
                </div>
                <p className="mt-1.5 max-w-[390px] text-[10px] font-medium leading-[17px] text-[#8a8a84]">
                  {t("rawJobs.pipelineHint")}
                </p>
              </div>
            </div>

            <div className="space-y-0">
              <JobPipelineCell
                stage="normalization"
                status={job.normalizationStatus}
                id={job.normalizedJobId}
                version={job.normalizationVersion}
                timestamp={job.normalizedAt}
                locale={locale}
              />

              <div className="relative flex h-8 items-center justify-center">
                <span className="absolute h-full w-px bg-black/[0.08]" />
                <span className="relative flex size-[18px] items-center justify-center rounded-full border border-black/[0.06] bg-[#fafaf8] shadow-[0_2px_6px_rgba(0,0,0,0.025)]">
                  <span className="size-1.5 rounded-full bg-[#c7f34f]" />
                </span>
              </div>

              <JobPipelineCell
                stage="embedding"
                status={job.embeddingStatus}
                id={job.embeddingJobId}
                version={job.embeddingVersion}
                timestamp={job.embeddedAt}
                error={job.embeddingLastError}
                locale={locale}
              />
            </div>
          </section>

          <aside className="min-w-0">
            <div className="sticky top-5 overflow-hidden rounded-[16px] border border-black/[0.06] bg-white shadow-[0_8px_26px_rgba(0,0,0,0.025)]">
              <div className="border-b border-black/[0.05] bg-[#fafaf8] px-4 py-4">
                <div className="flex items-center gap-2.5">
                  <span className="font-mono text-[9px] font-semibold uppercase tracking-[0.13em] text-[#777771]">
                    {t("actions.title")}
                  </span>
                  <span className="h-px flex-1 bg-black/[0.07]" />
                </div>
                <p className="mt-2 text-[10px] font-medium leading-[17px] text-[#777771]">
                  {t("actions.description")}
                </p>
              </div>

              <div className="p-3">
                <button
                  type="button"
                  onClick={() => onNormalize(job)}
                  disabled={pending}
                  title={t("actions.normalizeTitle")}
                  className="flex min-h-10 w-full items-center gap-2.5 rounded-[10px] border border-black/[0.075] bg-white px-3 text-left text-[10px] font-semibold text-[#3f3f3a] transition hover:border-black/[0.14] hover:bg-[#fafaf8] disabled:cursor-not-allowed disabled:opacity-35"
                >
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-[8px] bg-[#f3f3ef] text-[#55554f]">
                    <RefreshIcon />
                  </span>
                  <span>{t(getNormalizeActionKey(job.normalizationStatus, isNormalizing))}</span>
                </button>

                <button
                  type="button"
                  onClick={() => onEmbed(job)}
                  disabled={pending || !canEmbed}
                  title={canEmbed ? t("actions.embedTitle") : t("actions.embedRequiresReady")}
                  className="mt-2 flex min-h-10 w-full items-center gap-2.5 rounded-[10px] bg-[#20201e] px-3 text-left text-[10px] font-semibold text-white shadow-[0_4px_12px_rgba(0,0,0,0.09)] transition hover:bg-black disabled:cursor-not-allowed disabled:opacity-30"
                >
                  <span className="flex size-7 shrink-0 items-center justify-center rounded-[8px] bg-white/[0.08] text-[#d9ff75]">
                    <VectorIcon />
                  </span>
                  <span>{t(getEmbeddingActionKey(job.embeddingStatus, isEmbedding))}</span>
                </button>

                <div className="mt-3 rounded-[10px] border border-black/[0.05] bg-[#fafaf8] px-3 py-3">
                  <div className="flex items-start gap-2">
                    <span className={`mt-[6px] size-1.5 shrink-0 rounded-full ${canEmbed ? "bg-[#c7f34f]" : "bg-black/20"}`} />
                    <p className="text-[9px] font-medium leading-[16px] text-[#71716b]">
                      {canEmbed ? t("actions.embedUsesNormalizedId") : t("actions.embedRequiresReady")}
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </aside>
        </div>
      </div>
    </details>
  );
}
