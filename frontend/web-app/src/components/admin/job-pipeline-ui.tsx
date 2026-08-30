"use client";

import {useState} from "react";
import type {ReactNode} from "react";
import {useTranslations} from "next-intl";

import type {
  CopyableJobIdProps,
  JobPipelineCellProps,
  PipelineStatusBadgeProps,
  PipelineStageStatus
} from "@/types/admin-job";

import {formatDate} from "./admin-ui";

const PIPELINE_STATUS_KEY = {
  NOT_CREATED: "pipeline.status.NOT_CREATED",
  OUTDATED: "pipeline.status.OUTDATED",
  PROCESSING: "pipeline.status.PROCESSING",
  READY: "pipeline.status.READY",
  FAILED: "pipeline.status.FAILED"
} as const satisfies Record<PipelineStageStatus, string>;

function CopyIcon({copied}: {copied: boolean}) {
  if (copied) {
    return (
      <svg viewBox="0 0 20 20" fill="none" className="size-3.5" aria-hidden="true">
        <path
          d="m5 10 3 3 7-7"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 20 20" fill="none" className="size-3.5" aria-hidden="true">
      <rect x="6.5" y="6.5" width="8" height="8" rx="1.5" stroke="currentColor" strokeWidth="1.4" />
      <path
        d="M5 12.5H4.5A1.5 1.5 0 0 1 3 11V4.5A1.5 1.5 0 0 1 4.5 3H11A1.5 1.5 0 0 1 12.5 4.5V5"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
      />
    </svg>
  );
}

function NormalizeIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="none" className="size-4" aria-hidden="true">
      <path d="M5 5.5h8M5 9.5h6M5 13.5h4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      <path d="m12 13.2 1.6 1.6L17 11.4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function EmbeddingIcon() {
  return (
    <svg viewBox="0 0 20 20" fill="none" className="size-4" aria-hidden="true">
      <circle cx="5" cy="10" r="1.5" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="15" cy="6" r="1.5" stroke="currentColor" strokeWidth="1.4" />
      <circle cx="15" cy="14" r="1.5" stroke="currentColor" strokeWidth="1.4" />
      <path d="m6.4 9.4 7.2-2.8M6.4 10.6l7.2 2.8" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

export function PipelineStatusBadge({status}: PipelineStatusBadgeProps) {
  const t = useTranslations("admin.jobs");

  const styles = {
    NOT_CREATED: "border-black/[0.065] bg-[#fafaf8] text-[#85857f]",
    OUTDATED: "border-amber-200/80 bg-amber-50 text-amber-700",
    PROCESSING: "border-sky-200/80 bg-sky-50 text-sky-700",
    READY: "border-[#d6ef94] bg-[#f9ffe8] text-[#59623b]",
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
    <span className={`inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2.5 py-1 font-mono text-[8px] font-semibold leading-none tracking-[0.055em] ${styles}`}>
      <span className={`size-1.5 shrink-0 rounded-full ${dotStyles}`} />
      {t(PIPELINE_STATUS_KEY[status])}
    </span>
  );
}

export function CopyableId({label, value}: CopyableJobIdProps) {
  const t = useTranslations("admin.jobs");
  const [copied, setCopied] = useState(false);

  async function copyValue() {
    if (!value || !navigator.clipboard) {
      return;
    }

    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1200);
    } catch {
      setCopied(false);
    }
  }

  if (!value) {
    return <span className="font-mono text-[11px] text-[#94948e]">—</span>;
  }

  const actionLabel = copied
    ? t("rawJobs.copied", {label})
    : t("rawJobs.copy", {label});

  return (
    <div className="flex min-w-0 items-center gap-2">
      <span className="min-w-0 flex-1 truncate font-mono text-[11px] font-medium text-[#4d4d48]" title={value}>
        {value}
      </span>
      <button
        type="button"
        onClick={copyValue}
        className="flex size-7 shrink-0 items-center justify-center rounded-[8px] border border-transparent text-[#93938d] transition hover:border-black/[0.055] hover:bg-white hover:text-[#252522]"
        aria-label={actionLabel}
        title={actionLabel}
      >
        <CopyIcon copied={copied} />
      </button>
    </div>
  );
}

function DetailRow({label, children}: {label: string; children: ReactNode}) {
  return (
    <div className="grid min-w-0 grid-cols-[82px_minmax(0,1fr)] items-center gap-3 border-b border-black/[0.045] py-2.5 last:border-b-0">
      <span className="font-mono text-[9px] font-semibold uppercase tracking-[0.08em] text-[#92928c]">
        {label}
      </span>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

export function JobPipelineCell({
  stage,
  status,
  id,
  version,
  timestamp,
  error,
  locale
}: JobPipelineCellProps) {
  const t = useTranslations("admin.jobs");

  const title =
    stage === "normalization"
      ? t("pipeline.normalization")
      : t("pipeline.embedding");

  const description =
    stage === "normalization"
      ? t("pipeline.normalizationDescription")
      : t("pipeline.embeddingDescription");

  const stageIndex = stage === "normalization" ? "01" : "02";
  const accent =
    status === "FAILED"
      ? "bg-red-400"
      : status === "PROCESSING"
        ? "bg-sky-400"
        : status === "OUTDATED"
          ? "bg-amber-400"
          : status === "READY"
            ? "bg-[#c7f34f]"
            : "bg-black/15";

  return (
    <section className="group/stage relative min-w-0 overflow-hidden rounded-[16px] border border-black/[0.06] bg-white shadow-[0_5px_18px_rgba(0,0,0,0.018)] transition-[border-color,box-shadow,transform] duration-200 hover:-translate-y-px hover:border-black/[0.09] hover:shadow-[0_10px_26px_rgba(0,0,0,0.03)]">
      <span aria-hidden="true" className={`absolute inset-y-0 left-0 w-[2px] ${accent}`} />

      <div className="p-4 pl-[18px]">
        <div className="flex items-start gap-3">
          <div className="relative flex size-9 shrink-0 items-center justify-center rounded-[10px] border border-black/[0.06] bg-[#fafaf8] text-[#55554f]">
            {stage === "normalization" ? <NormalizeIcon /> : <EmbeddingIcon />}
            <span className="absolute -left-1.5 -top-1.5 flex size-[17px] items-center justify-center rounded-full bg-[#20201e] font-mono text-[7px] font-semibold text-white shadow-[0_2px_6px_rgba(0,0,0,0.14)]">
              {stageIndex}
            </span>
          </div>

          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div className="min-w-0">
                <h5 className="text-[13px] font-semibold tracking-[-0.02em] text-[#2a2a27]">
                  {title}
                </h5>
                <p className="mt-1 max-w-[310px] text-[10px] font-medium leading-[17px] text-[#8a8a84]">
                  {description}
                </p>
              </div>
              <PipelineStatusBadge status={status} />
            </div>
          </div>
        </div>

        <div className="mt-4 rounded-[12px] border border-black/[0.05] bg-[#fafaf8]/80 px-3.5 py-1.5">
          <DetailRow label={t("pipeline.id")}>
            <CopyableId label={`${title} ${t("pipeline.id")}`} value={id} />
          </DetailRow>

          <DetailRow label={t("pipeline.version")}>
            <p className="truncate font-mono text-[11px] font-medium text-[#52524d]" title={version ?? undefined}>
              {version || "—"}
            </p>
          </DetailRow>

          <DetailRow label={t("pipeline.updatedAt")}>
            <p className="text-[11px] font-medium leading-[18px] text-[#5f5f59]">
              {timestamp ? formatDate(timestamp, locale) : "—"}
            </p>
          </DetailRow>
        </div>

        {error ? (
          <div className="mt-3 rounded-[11px] border border-red-100 bg-red-50/60 px-3 py-2.5">
            <p className="font-mono text-[9px] font-semibold uppercase tracking-[0.07em] text-red-700">
              {t("pipeline.latestError")}
            </p>
            <p className="mt-1 line-clamp-3 text-[10px] font-medium leading-[17px] text-red-700/80" title={error}>
              {error}
            </p>
          </div>
        ) : null}
      </div>
    </section>
  );
}
