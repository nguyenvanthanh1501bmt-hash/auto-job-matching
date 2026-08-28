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

function CopyIcon({
  copied
}: {
  copied: boolean;
}) {
  if (copied) {
    return (
      <svg
        viewBox="0 0 20 20"
        fill="none"
        className="size-3.5"
        aria-hidden="true"
      >
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
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-3.5"
      aria-hidden="true"
    >
      <rect
        x="6.5"
        y="6.5"
        width="8"
        height="8"
        rx="1.5"
        stroke="currentColor"
        strokeWidth="1.4"
      />

      <path
        d="M5 12.5H4.5A1.5 1.5 0 0 1 3 11V4.5A1.5 1.5 0 0 1 4.5 3H11A1.5 1.5 0 0 1 12.5 4.5V5"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinecap="round"
      />
    </svg>
  );
}

export function PipelineStatusBadge({
  status
}: PipelineStatusBadgeProps) {
  const t = useTranslations("admin.jobs");

  const statusKey =
    PIPELINE_STATUS_KEY[status];

  const styles = {
    NOT_CREATED:
      "border-black/[0.06] bg-white text-[#8b8b85]",
    OUTDATED:
      "border-amber-200/80 bg-amber-50 text-amber-700",
    PROCESSING:
      "border-sky-200/80 bg-sky-50 text-sky-700",
    READY:
      "border-[#d6f680] bg-[#f9ffe9] text-[#59623b]",
    FAILED:
      "border-red-200/80 bg-red-50 text-red-700"
  }[status];

  const dotStyles = {
    NOT_CREATED: "bg-black/20",
    OUTDATED: "bg-amber-400",
    PROCESSING: "bg-sky-400",
    READY: "bg-[#cfff54]",
    FAILED: "bg-red-400"
  }[status];

  return (
    <span
      className={`inline-flex shrink-0 items-center gap-1.5 rounded-full border px-2 py-1 font-mono text-[9px] font-semibold leading-none tracking-[0.025em] ${styles}`}
    >
      <span
        className={`size-1.5 shrink-0 rounded-full ${dotStyles}`}
      />

      {t(statusKey)}
    </span>
  );
}

export function CopyableId({
  label,
  value
}: CopyableJobIdProps) {
  const t = useTranslations("admin.jobs");

  const [copied, setCopied] =
    useState(false);

  async function copyValue() {
    if (
      !value ||
      !navigator.clipboard
    ) {
      return;
    }

    try {
      await navigator.clipboard.writeText(
        value
      );

      setCopied(true);

      window.setTimeout(() => {
        setCopied(false);
      }, 1200);
    } catch {
      setCopied(false);
    }
  }

  if (!value) {
    return (
      <span className="font-mono text-[12px] text-[#8c8c86]">
        —
      </span>
    );
  }

  const actionLabel = copied
    ? t("rawJobs.copied", {
        label
      })
    : t("rawJobs.copy", {
        label
      });

  return (
    <div className="flex min-w-0 items-center gap-2">
      <span
        className="min-w-0 flex-1 truncate font-mono text-[12px] font-medium text-[#4f4f4a]"
        title={value}
      >
        {value}
      </span>

      <button
        type="button"
        onClick={copyValue}
        className="flex size-7 shrink-0 items-center justify-center rounded-[8px] text-[#898983] transition hover:bg-black/[0.045] hover:text-[#222]"
        aria-label={actionLabel}
        title={actionLabel}
      >
        <CopyIcon
          copied={copied}
        />
      </button>
    </div>
  );
}

function DetailRow({
  label,
  children
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="grid min-w-0 grid-cols-[86px_minmax(0,1fr)] items-center gap-3">
      <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.07em] text-[#7b7b75]">
        {label}
      </span>

      <div className="min-w-0">
        {children}
      </div>
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
  const t = useTranslations(
    "admin.jobs"
  );

  const title =
    stage === "normalization"
      ? t(
          "pipeline.normalization"
        )
      : t(
          "pipeline.embedding"
        );

  return (
    <section className="relative min-w-0 rounded-[14px] border border-black/[0.06] bg-white px-4 py-4 shadow-[0_2px_10px_rgba(0,0,0,0.015)]">
      {/*
       * Status là metadata phụ nên đặt nhỏ ở góc trên.
       * Stage name vẫn giữ hierarchy chính của card.
       */}
      <div className="absolute right-4 top-4">
        <PipelineStatusBadge
          status={status}
        />
      </div>

      <div className="pr-24">
        <p className="font-mono text-[11px] font-semibold uppercase tracking-[0.11em] text-[#55554f]">
          {title}
        </p>

        <p className="mt-1 text-[11px] font-medium leading-5 text-[#8a8a84]">
          {stage === "normalization"
            ? t(
                "pipeline.normalizationDescription"
              )
            : t(
                "pipeline.embeddingDescription"
              )}
        </p>
      </div>

      <div className="mt-5 space-y-3.5 border-t border-black/[0.05] pt-4">
        <DetailRow
          label={t(
            "pipeline.id"
          )}
        >
          <CopyableId
            label={`${title} ${t(
              "pipeline.id"
            )}`}
            value={id}
          />
        </DetailRow>

        <DetailRow
          label={t(
            "pipeline.version"
          )}
        >
          <p
            className="truncate font-mono text-[12px] font-medium text-[#4f4f4a]"
            title={
              version ??
              undefined
            }
          >
            {version || "—"}
          </p>
        </DetailRow>

        <DetailRow
          label={t(
            "pipeline.updatedAt"
          )}
        >
          <p className="text-[12px] font-medium leading-5 text-[#5d5d57]">
            {timestamp
              ? formatDate(
                  timestamp,
                  locale
                )
              : "—"}
          </p>
        </DetailRow>
      </div>

      {error ? (
        <div className="mt-4 rounded-[10px] border border-red-100 bg-red-50/55 px-3 py-2.5">
          <p className="text-[11px] font-semibold text-red-700">
            {t(
              "pipeline.latestError"
            )}
          </p>

          <p
            className="mt-1 line-clamp-3 text-[11px] leading-5 text-red-700/80"
            title={error}
          >
            {error}
          </p>
        </div>
      ) : null}
    </section>
  );
}