"use client";

import {useState} from "react";
import type {FormEvent} from "react";
import {useTranslations} from "next-intl";

import {useRunLiveCrawler, useRunMockCrawler} from "@/hooks/use-admin-tools";
import {DEFAULT_LIVE_CRAWLER_LIMIT, MAX_LIVE_CRAWLER_LIMIT} from "@/services/admin-crawler.service";
import {LIVE_CRAWLER_SOURCES} from "@/types/admin-crawler";
import type {LiveCrawlerSourceCode} from "@/types/admin-crawler";
import type {
  AdminSectionLabelProps,
  CrawlerExecutionEmptyProps,
  CrawlerExecutionMetricProps,
  CrawlerExecutionResultProps,
  CrawlerRunStatus,
  CrawlerRunType,
  CrawlerStatusBadgeProps
} from "@/types/admin-ui";

import {
  ErrorMessage,
  Field,
  inputClassName,
  numberFromInput,
  PageHeading,
  PrimaryButton,
  selectClassName
} from "./admin-ui";

function CrawlerIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-[19px]"
      aria-hidden="true"
    >
      <path
        d="M6 7h12M6 12h8M6 17h5"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />

      <path
        d="M18 14v6M15 17h6"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
    </svg>
  );
}

function MockIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-[18px]"
      aria-hidden="true"
    >
      <path
        d="m8 7-4 5 4 5M16 7l4 5-4 5M14 5l-4 14"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function CheckIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-4"
      aria-hidden="true"
    >
      <path
        d="m5 10 3.2 3.2L15.5 6"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function StatusBadge({status}: CrawlerStatusBadgeProps) {
  const t = useTranslations("admin.crawler.status");

  const config = {
    idle: {
      label: t("idle"),
      dot: "bg-[#d9ff75]"
    },
    running: {
      label: t("running"),
      dot: "bg-amber-400"
    },
    success: {
      label: t("success"),
      dot: "bg-emerald-500"
    },
    error: {
      label: t("error"),
      dot: "bg-red-500"
    }
  }[status];

  return (
    <div className="inline-flex items-center gap-2 rounded-full border border-black/[0.055] bg-white px-3 py-1.5">
      <span className="relative flex size-1.5">
        {status === "running" ? (
          <span
            className={`absolute inset-0 animate-ping rounded-full ${config.dot} opacity-30`}
          />
        ) : null}

        <span className={`relative size-1.5 rounded-full ${config.dot}`} />
      </span>

      <span className="font-mono text-[9px] font-medium uppercase tracking-[0.12em] text-[#85857f]">
        {config.label}
      </span>
    </div>
  );
}

function getMutationStatus(
  pending: boolean,
  success: boolean,
  error: boolean
): CrawlerRunStatus {
  if (pending) {
    return "running";
  }

  if (error) {
    return "error";
  }

  if (success) {
    return "success";
  }

  return "idle";
}

function SectionLabel({children}: AdminSectionLabelProps) {
  return (
    <div className="flex items-center gap-2.5">
      <span className="font-mono text-[9px] font-medium uppercase tracking-[0.15em] text-[#aaa]">
        {children}
      </span>

      <span className="h-px w-9 bg-black/[0.08]" />
    </div>
  );
}

function ExecutionMetric({
  label,
  value
}: CrawlerExecutionMetricProps) {
  return (
    <div className="px-5 py-5 sm:px-6">
      <p className="font-mono text-[9px] font-medium uppercase tracking-[0.12em] text-[#aaa]">
        {label}
      </p>

      <p className="mt-3 text-[30px] font-semibold leading-none tracking-[-0.05em] text-[#242422]">
        {value}
      </p>
    </div>
  );
}

function CrawlerExecutionResult({
  result
}: CrawlerExecutionResultProps) {
  const t = useTranslations("admin.crawler.output");

  return (
    <div className="overflow-hidden rounded-[16px] border border-black/[0.055] bg-[#fafaf8]">
      <div className="flex flex-col gap-5 px-5 py-5 sm:flex-row sm:items-start sm:justify-between sm:px-6">
        <div className="flex items-start gap-3.5">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-full border border-emerald-200 bg-emerald-50 text-emerald-700">
            <CheckIcon />
          </div>

          <div>
            <div className="flex flex-wrap items-center gap-2.5">
              <h3 className="text-[17px] font-semibold tracking-[-0.025em] text-[#292927]">
                {t("successTitle")}
              </h3>

              <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-200 bg-emerald-50 px-2.5 py-1">
                <span className="size-1.5 rounded-full bg-emerald-500" />

                <span className="font-mono text-[8px] font-medium uppercase tracking-[0.1em] text-emerald-700">
                  {t("completed")}
                </span>
              </span>
            </div>

            <p className="mt-2 max-w-[620px] text-[12px] leading-5 text-[#85857f]">
              {t("successDescription", {
                source: result.sourceCode,
                inserted: result.insertedCount
              })}
            </p>
          </div>
        </div>

        <span className="w-fit shrink-0 rounded-full border border-black/[0.055] bg-white px-3.5 py-2 font-mono text-[10px] font-medium tracking-[0.08em] text-[#777]">
          {result.sourceCode}
        </span>
      </div>

      <div className="grid border-t border-black/[0.05] sm:grid-cols-3">
        <div className="border-b border-black/[0.05] sm:border-r sm:border-b-0">
          <ExecutionMetric
            label={t("requested")}
            value={result.requestedLimit ?? "—"}
          />
        </div>

        <div className="border-b border-black/[0.05] sm:border-r sm:border-b-0">
          <ExecutionMetric
            label={t("inserted")}
            value={result.insertedCount}
          />
        </div>

        <ExecutionMetric
          label={t("total")}
          value={result.totalRawJobs}
        />
      </div>
    </div>
  );
}

function CrawlerExecutionEmpty({
  loading
}: CrawlerExecutionEmptyProps) {
  const t = useTranslations("admin.crawler.output");

  if (loading) {
    return (
      <div className="flex min-h-[170px] items-center justify-center rounded-[16px] border border-black/[0.05] bg-[#fafaf8]">
        <div className="flex items-center gap-3">
          <span className="size-4 animate-spin rounded-full border-2 border-black/10 border-t-black/50" />

          <span className="text-[12px] font-medium text-[#777]">
            {t("running")}
          </span>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-[170px] items-center justify-center rounded-[16px] border border-dashed border-black/[0.08] bg-[#fafaf8]/60 px-6 text-center">
      <div>
        <div className="mx-auto flex size-10 items-center justify-center rounded-full border border-black/[0.05] bg-white">
          <span className="size-1.5 rounded-full bg-[#d9ff75]" />
        </div>

        <p className="mt-4 text-[12px] leading-5 text-[#999]">
          {t("empty")}
        </p>
      </div>
    </div>
  );
}

export function CrawlerSection() {
  const t = useTranslations("admin.crawler");

  const mockCrawler = useRunMockCrawler();
  const liveCrawler = useRunLiveCrawler();

  const [lastRun, setLastRun] =
    useState<CrawlerRunType | null>(null);

  const [liveSource, setLiveSource] =
    useState<LiveCrawlerSourceCode>(LIVE_CRAWLER_SOURCES[0]);

  const [liveLimit, setLiveLimit] =
    useState(String(DEFAULT_LIVE_CRAWLER_LIMIT));

  const liveStatus = getMutationStatus(
    liveCrawler.isPending,
    liveCrawler.isSuccess,
    liveCrawler.isError
  );

  const mockStatus = getMutationStatus(
    mockCrawler.isPending,
    mockCrawler.isSuccess,
    mockCrawler.isError
  );

  function runLiveCrawler(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    setLastRun("live");

    // Form chỉ chuẩn hóa input; validation nghiệp vụ cuối cùng vẫn thuộc service/backend.
    liveCrawler.mutate({
      sourceCode: liveSource,
      limit: numberFromInput(
        liveLimit,
        DEFAULT_LIVE_CRAWLER_LIMIT
      )
    });
  }

  function runMockCrawler() {
    setLastRun("mock");
    mockCrawler.mutate();
  }

  const latestMutation =
    lastRun === "live"
      ? liveCrawler
      : lastRun === "mock"
        ? mockCrawler
        : null;

  return (
    <div className="space-y-6">
      <PageHeading
        eyebrow={t("eyebrow")}
        title={t("title")}
        description={t("description")}
      />

      <section className="overflow-hidden rounded-[18px] border border-black/[0.055] bg-white shadow-[0_8px_30px_rgba(0,0,0,0.022)]">
        <div className="grid sm:grid-cols-3">
          <div className="border-b border-black/[0.05] px-5 py-4 sm:border-r sm:border-b-0">
            <p className="font-mono text-[9px] uppercase tracking-[0.13em] text-[#aaa]">
              {t("summary.sources")}
            </p>

            <p className="mt-2 text-[17px] font-semibold tracking-[-0.025em] text-[#292927]">
              {LIVE_CRAWLER_SOURCES.length}
            </p>
          </div>

          <div className="border-b border-black/[0.05] px-5 py-4 sm:border-r sm:border-b-0">
            <p className="font-mono text-[9px] uppercase tracking-[0.13em] text-[#aaa]">
              {t("summary.maxPerRun")}
            </p>

            <p className="mt-2 text-[17px] font-semibold tracking-[-0.025em] text-[#292927]">
              {MAX_LIVE_CRAWLER_LIMIT}
            </p>
          </div>

          <div className="px-5 py-4">
            <p className="font-mono text-[9px] uppercase tracking-[0.13em] text-[#aaa]">
              {t("summary.currentSource")}
            </p>

            <div className="mt-2 flex items-center gap-2">
              <span className="size-1.5 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />

              <p className="text-[17px] font-semibold tracking-[-0.025em] text-[#292927]">
                {liveSource}
              </p>
            </div>
          </div>
        </div>
      </section>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1.45fr)_minmax(320px,0.55fr)]">
        <section className="overflow-hidden rounded-[20px] border border-black/[0.055] bg-white shadow-[0_12px_40px_rgba(0,0,0,0.025)]">
          <div className="flex items-start justify-between gap-5 border-b border-black/[0.045] px-6 py-5">
            <div className="flex items-start gap-3.5">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-[11px] border border-black/[0.055] bg-[#fafaf8] text-[#555]">
                <CrawlerIcon />
              </div>

              <div>
                <SectionLabel>
                  {t("live.label")}
                </SectionLabel>

                <h2 className="mt-2.5 text-[19px] font-semibold tracking-[-0.035em] text-[#222220]">
                  {t("live.title")}
                </h2>

                <p className="mt-1.5 max-w-[560px] text-[12px] leading-5 text-[#92928c]">
                  {t("live.description")}
                </p>
              </div>
            </div>

            <StatusBadge status={liveStatus} />
          </div>

          <form
            onSubmit={runLiveCrawler}
            className="p-6"
          >
            <div className="grid gap-5 sm:grid-cols-[minmax(0,1fr)_180px]">
              <Field label={t("live.source")}>
                <select
                  value={liveSource}
                  onChange={(event) =>
                    setLiveSource(
                      event.target.value as LiveCrawlerSourceCode
                    )
                  }
                  disabled={liveCrawler.isPending}
                  className={selectClassName}
                >
                  {LIVE_CRAWLER_SOURCES.map((source) => (
                    <option
                      key={source}
                      value={source}
                    >
                      {source}
                    </option>
                  ))}
                </select>
              </Field>

              <Field
                label={t("live.limit")}
                hint={t("live.limitHint", {
                  max: MAX_LIVE_CRAWLER_LIMIT
                })}
              >
                <input
                  type="number"
                  min="1"
                  max={MAX_LIVE_CRAWLER_LIMIT}
                  value={liveLimit}
                  disabled={liveCrawler.isPending}
                  onChange={(event) =>
                    setLiveLimit(event.target.value)
                  }
                  className={inputClassName}
                />
              </Field>
            </div>

            <div className="mt-6 rounded-[14px] border border-black/[0.045] bg-[#fafaf8] px-4 py-3.5">
              <div className="flex items-start gap-3">
                <span className="mt-1.5 size-1.5 shrink-0 rounded-full bg-[#d9ff75]" />

                <p className="text-[11px] leading-[19px] text-[#888]">
                  {t("live.note", {
                    source: liveSource,
                    limit:
                      liveLimit ||
                      DEFAULT_LIVE_CRAWLER_LIMIT
                  })}
                </p>
              </div>
            </div>

            <div className="mt-6 flex items-center justify-between gap-4 border-t border-black/[0.045] pt-5">
              <p className="hidden text-[10px] leading-4 text-[#aaa] sm:block">
                {t("live.actionHint")}
              </p>

              <PrimaryButton
                type="submit"
                disabled={liveCrawler.isPending}
              >
                {liveCrawler.isPending
                  ? t("live.running")
                  : t("live.run")}
              </PrimaryButton>
            </div>
          </form>
        </section>

        <section className="flex flex-col rounded-[20px] border border-black/[0.055] bg-white p-6 shadow-[0_12px_40px_rgba(0,0,0,0.025)]">
          <div className="flex items-start justify-between gap-4">
            <div className="flex size-10 items-center justify-center rounded-[11px] border border-black/[0.055] bg-[#fafaf8] text-[#555]">
              <MockIcon />
            </div>

            <StatusBadge status={mockStatus} />
          </div>

          <div className="mt-6">
            <SectionLabel>
              {t("mock.label")}
            </SectionLabel>

            <h2 className="mt-2.5 text-[19px] font-semibold tracking-[-0.035em] text-[#222220]">
              {t("mock.title")}
            </h2>

            <p className="mt-2 text-[12px] leading-5 text-[#92928c]">
              {t("mock.description")}
            </p>
          </div>

          <div className="mt-6 rounded-[14px] border border-black/[0.045] bg-[#fafaf8] p-4">
            <p className="text-[11px] leading-[19px] text-[#85857f]">
              {t("mock.note")}
            </p>
          </div>

          <div className="mt-auto pt-7">
            <PrimaryButton
              onClick={runMockCrawler}
              disabled={mockCrawler.isPending}
            >
              {mockCrawler.isPending
                ? t("mock.running")
                : t("mock.run")}
            </PrimaryButton>
          </div>
        </section>
      </div>

      <section className="overflow-hidden rounded-[20px] border border-black/[0.055] bg-white shadow-[0_12px_40px_rgba(0,0,0,0.025)]">
        <div className="flex items-start justify-between gap-4 border-b border-black/[0.045] px-6 py-5">
          <div>
            <SectionLabel>
              {t("output.label")}
            </SectionLabel>

            <h2 className="mt-2.5 text-[19px] font-semibold tracking-[-0.035em] text-[#222220]">
              {t("output.title")}
            </h2>

            <p className="mt-1.5 text-[12px] leading-5 text-[#92928c]">
              {t("output.description")}
            </p>
          </div>

          {latestMutation?.data ? (
            <span className="rounded-full border border-black/[0.055] bg-[#fafaf8] px-3.5 py-2 font-mono text-[9px] font-medium uppercase tracking-[0.11em] text-[#888]">
              {latestMutation.data.sourceCode}
            </span>
          ) : null}
        </div>

        <div className="p-6">
          {!latestMutation || latestMutation.isPending ? (
            <CrawlerExecutionEmpty
              loading={latestMutation?.isPending ?? false}
            />
          ) : null}

          {latestMutation?.isError ? (
            <ErrorMessage error={latestMutation.error} />
          ) : null}

          {latestMutation?.data ? (
            <CrawlerExecutionResult
              result={latestMutation.data}
            />
          ) : null}
        </div>
      </section>
    </div>
  );
}