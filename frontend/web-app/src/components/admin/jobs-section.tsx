"use client";

import {useState} from "react";
import type {FormEvent} from "react";
import {useLocale, useTranslations} from "next-intl";

import {useAdminRawJobs, useNormalizeRawJob, useRenormalizeBatch} from "@/hooks/use-admin-tools";
import {
  DEFAULT_RAW_JOB_LIMIT,
  DEFAULT_RENORMALIZATION_SIZE,
  MAX_RAW_JOB_LIMIT,
  MAX_RENORMALIZATION_SIZE
} from "@/services/admin-job.service";
import {LIVE_CRAWLER_SOURCES} from "@/types/admin-crawler";

import {
  ErrorMessage,
  Field,
  formatDate,
  numberFromInput,
  PageHeading,
  PrimaryButton,
  ResultBox,
  SecondaryButton,
  selectClassName,
  Toggle
} from "./admin-ui";

function JobsIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-[18px]"
      aria-hidden="true"
    >
      <rect
        x="4"
        y="7"
        width="16"
        height="12"
        rx="2.5"
        stroke="currentColor"
        strokeWidth="1.7"
      />

      <path
        d="M9 7V5.8C9 4.8 9.8 4 10.8 4h2.4C14.2 4 15 4.8 15 5.8V7M4 11.5h16"
        stroke="currentColor"
        strokeWidth="1.7"
      />
    </svg>
  );
}

function MaintenanceIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-[18px]"
      aria-hidden="true"
    >
      <path
        d="M6 7h12M6 12h8M6 17h5"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />

      <path
        d="m16 15 4 3-4 3"
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

function InfoIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-[15px]"
      aria-hidden="true"
    >
      <circle
        cx="10"
        cy="10"
        r="7"
        stroke="currentColor"
        strokeWidth="1.5"
      />

      <path
        d="M10 9v4M10 6.7v.1"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
      />
    </svg>
  );
}

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

export function JobsSection() {
  const t = useTranslations("admin.jobs");
  const locale = useLocale();

  const [rawJobLimit, setRawJobLimit] = useState(String(DEFAULT_RAW_JOB_LIMIT));
  const [singleForce, setSingleForce] = useState(false);
  const [normalizingId, setNormalizingId] = useState<string | null>(null);

  const [batchSource, setBatchSource] = useState("");
  const [batchPage, setBatchPage] = useState("0");
  const [batchSize, setBatchSize] = useState(String(DEFAULT_RENORMALIZATION_SIZE));
  const [batchForce, setBatchForce] = useState(false);

  const normalizedRawJobLimit = Math.min(
    Math.max(numberFromInput(rawJobLimit, DEFAULT_RAW_JOB_LIMIT), 1),
    MAX_RAW_JOB_LIMIT
  );

  const rawJobsQuery = useAdminRawJobs(normalizedRawJobLimit, true);
  const normalizeRawJob = useNormalizeRawJob();
  const renormalizeBatch = useRenormalizeBatch();

  const rawJobs = rawJobsQuery.data ?? [];

  function normalizeJob(rawJobId: string) {
    setNormalizingId(rawJobId);

    /*
     * Happy path của backend đã tự normalize sau khi crawler lưu RawJob.
     * Action này chỉ dành cho trường hợp admin cần kiểm tra hoặc xử lý lại.
     */
    normalizeRawJob.mutate(
      {
        rawJobId,
        options: {
          force: singleForce
        }
      },
      {
        onSettled: () => {
          setNormalizingId(null);
        }
      }
    );
  }

  function runBatchNormalization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    renormalizeBatch.mutate({
      sourceCode: batchSource || undefined,
      page: numberFromInput(batchPage, 0),
      size: numberFromInput(batchSize, DEFAULT_RENORMALIZATION_SIZE),
      force: batchForce
    });
  }

  return (
    <div className="space-y-6">
      <PageHeading
        eyebrow={t("eyebrow")}
        title={t("title")}
        description={t("description")}
      />

      <section className="overflow-hidden rounded-[20px] border border-black/[0.055] bg-white shadow-[0_12px_40px_rgba(0,0,0,0.025)]">
        <div className="border-b border-black/[0.045] px-5 py-5 sm:px-6">
          <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
            <div className="flex items-start gap-3.5">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-[11px] border border-black/[0.055] bg-[#fafaf8] text-[#555]">
                <JobsIcon />
              </div>

              <div>
                <div className="flex items-center gap-2.5">
                  <span className="font-mono text-[9px] font-medium uppercase tracking-[0.15em] text-[#aaa]">
                    {t("rawJobs.workspace")}
                  </span>

                  <span className="h-px w-8 bg-black/[0.08]" />
                </div>

                <div className="mt-2.5 flex flex-wrap items-center gap-2.5">
                  <h2 className="text-[19px] font-semibold tracking-[-0.035em] text-[#222220]">
                    {t("rawJobs.title")}
                  </h2>

                  {!rawJobsQuery.isLoading ? (
                    <span className="rounded-full border border-black/[0.055] bg-[#fafaf8] px-2.5 py-1 font-mono text-[9px] font-medium text-[#888]">
                      {rawJobs.length}
                    </span>
                  ) : null}
                </div>

                <p className="mt-1.5 max-w-[650px] text-[12px] leading-5 text-[#92928c]">
                  {t("rawJobs.description")}
                </p>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <div className="flex h-10 items-center rounded-[11px] border border-black/[0.065] bg-[#fafaf8]">
                <span className="border-r border-black/[0.05] px-3 font-mono text-[8px] font-medium uppercase tracking-[0.1em] text-[#aaa]">
                  {t("rawJobs.limitLabel")}
                </span>

                <input
                  type="number"
                  min="1"
                  max={MAX_RAW_JOB_LIMIT}
                  value={rawJobLimit}
                  onChange={(event) => setRawJobLimit(event.target.value)}
                  className="h-full w-[68px] bg-transparent px-2 text-center text-[12px] font-semibold text-[#444] outline-none"
                />
              </div>

              <SecondaryButton
                onClick={() => void rawJobsQuery.refetch()}
                disabled={rawJobsQuery.isFetching}
              >
                {rawJobsQuery.isFetching
                  ? t("rawJobs.refreshing")
                  : t("rawJobs.refresh")}
              </SecondaryButton>
            </div>
          </div>

          <div className="mt-5 flex flex-col gap-3 border-t border-black/[0.045] pt-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-start gap-3">
              <span className="mt-[7px] size-1.5 shrink-0 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />

              <p className="max-w-[680px] text-[11px] leading-[19px] text-[#8d8d87]">
                {t("rawJobs.pipelineHint")}
              </p>
            </div>

            <Toggle
              checked={singleForce}
              onChange={setSingleForce}
              label={t("rawJobs.force")}
            />
          </div>
        </div>

        {rawJobsQuery.isError ? (
          <div className="p-5 sm:p-6">
            <ErrorMessage error={rawJobsQuery.error} />
          </div>
        ) : null}

        {normalizeRawJob.isError ? (
          <div className="px-5 pt-5 sm:px-6">
            <ErrorMessage error={normalizeRawJob.error} />
          </div>
        ) : null}

        {normalizeRawJob.data ? (
          <div className="px-5 pt-5 sm:px-6">
            <details className="group overflow-hidden rounded-[14px] border border-emerald-200/70 bg-emerald-50/40">
              <summary className="flex cursor-pointer list-none items-center gap-3 px-4 py-3.5">
                <div className="flex size-8 shrink-0 items-center justify-center rounded-full border border-emerald-200 bg-white text-emerald-700">
                  <CheckIcon />
                </div>

                <div className="min-w-0 flex-1">
                  <p className="text-[12px] font-semibold text-emerald-800">
                    {t("rawJobs.latestNormalized")}
                  </p>

                  <p className="mt-0.5 text-[10px] text-emerald-700/60">
                    {t("rawJobs.reprocessCompleted")}
                  </p>
                </div>

                <span className="text-emerald-700/60">
                  <ChevronIcon />
                </span>
              </summary>

              <div className="border-t border-emerald-200/60 p-3">
                <ResultBox
                  title={t("rawJobs.latestNormalized")}
                  value={normalizeRawJob.data}
                />
              </div>
            </details>
          </div>
        ) : null}

        {rawJobsQuery.isLoading ? (
          <div className="flex min-h-[360px] items-center justify-center">
            <div className="flex items-center gap-3">
              <span className="size-4 animate-spin rounded-full border-2 border-black/10 border-t-black/50" />

              <span className="text-[12px] font-medium text-[#888]">
                {t("rawJobs.loading")}
              </span>
            </div>
          </div>
        ) : null}

        {!rawJobsQuery.isLoading && rawJobs.length === 0 ? (
          <div className="flex min-h-[360px] items-center justify-center px-6 text-center">
            <div>
              <div className="mx-auto flex size-10 items-center justify-center rounded-full border border-black/[0.05] bg-[#fafaf8]">
                <span className="size-1.5 rounded-full bg-[#d9ff75]" />
              </div>

              <p className="mt-4 text-[12px] leading-5 text-[#999]">
                {t("rawJobs.empty")}
              </p>
            </div>
          </div>
        ) : null}

        {!rawJobsQuery.isLoading && rawJobs.length > 0 ? (
          <div className="mt-5">
            <div className="hidden grid-cols-[42px_minmax(300px,1fr)_110px_180px_165px_105px] items-center gap-4 border-y border-black/[0.045] bg-[#fafaf8]/80 px-6 py-3 font-mono text-[8px] font-medium uppercase tracking-[0.12em] text-[#aaa] xl:grid">
              <span>#</span>
              <span>{t("rawJobs.columns.job")}</span>
              <span>{t("rawJobs.columns.source")}</span>
              <span>{t("rawJobs.columns.location")}</span>
              <span>{t("rawJobs.columns.collected")}</span>
              <span className="text-right">{t("rawJobs.columns.action")}</span>
            </div>

            <div>
              {rawJobs.map((job, index) => {
                const isNormalizing = normalizingId === job.id;

                return (
                  <article
                    key={job.id}
                    className={`group px-5 py-5 transition-colors hover:bg-[#fafaf8]/70 sm:px-6 ${
                      index !== rawJobs.length - 1
                        ? "border-b border-black/[0.045]"
                        : ""
                    }`}
                  >
                    <div className="xl:grid xl:grid-cols-[42px_minmax(300px,1fr)_110px_180px_165px_105px] xl:items-center xl:gap-4">
                      <span className="hidden font-mono text-[9px] text-[#b5b5af] xl:block">
                        {String(index + 1).padStart(2, "0")}
                      </span>

                      <div className="min-w-0">
                        <div className="mb-2 flex items-center gap-2 xl:hidden">
                          <span className="font-mono text-[9px] text-[#aaa]">
                            {String(index + 1).padStart(2, "0")}
                          </span>

                          <span className="size-1 rounded-full bg-[#d9ff75]" />

                          <span className="rounded-full border border-black/[0.055] bg-[#fafaf8] px-2 py-0.5 font-mono text-[8px] font-semibold text-[#777]">
                            {job.sourceCode || "—"}
                          </span>
                        </div>

                        <h3 className="truncate text-[14px] font-semibold tracking-[-0.02em] text-[#292927]">
                          {job.title || t("rawJobs.untitled")}
                        </h3>

                        <p className="mt-1 truncate text-[11px] text-[#8e8e88]">
                          {job.companyName || t("rawJobs.unknownCompany")}
                        </p>

                        <p className="mt-1.5 truncate font-mono text-[8px] tracking-[0.02em] text-[#bbb]">
                          {job.id}
                        </p>
                      </div>

                      <div className="hidden xl:block">
                        <span className="inline-flex rounded-full border border-black/[0.055] bg-[#fafaf8] px-2.5 py-1 font-mono text-[9px] font-semibold tracking-[0.03em] text-[#72726c]">
                          {job.sourceCode || "—"}
                        </span>
                      </div>

                      <p className="hidden truncate text-[11px] leading-5 text-[#85857f] xl:block">
                        {job.locationText || "—"}
                      </p>

                      <p className="hidden text-[10px] leading-[17px] text-[#999] xl:block">
                        {formatDate(job.collectedAt, locale)}
                      </p>

                      <div className="mt-4 flex flex-wrap items-center justify-between gap-4 border-t border-black/[0.04] pt-4 xl:mt-0 xl:block xl:border-0 xl:pt-0 xl:text-right">
                        <div className="min-w-0 text-[10px] leading-[18px] text-[#999] xl:hidden">
                          <p className="truncate">
                            {job.locationText || "—"}
                          </p>

                          <p>
                            {formatDate(job.collectedAt, locale)}
                          </p>
                        </div>

                        <button
                          type="button"
                          onClick={() => normalizeJob(job.id)}
                          disabled={normalizeRawJob.isPending}
                          className="inline-flex h-9 shrink-0 items-center justify-center rounded-[10px] border border-black/[0.07] bg-white px-3.5 text-[10px] font-semibold text-[#62625d] transition hover:border-black/[0.13] hover:bg-[#f7f7f4] hover:text-[#222] disabled:cursor-not-allowed disabled:opacity-40"
                        >
                          {isNormalizing
                            ? t("rawJobs.reprocessing")
                            : t("rawJobs.normalize")}
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          </div>
        ) : null}
      </section>

      <details className="group overflow-hidden rounded-[20px] border border-black/[0.055] bg-white shadow-[0_10px_32px_rgba(0,0,0,0.02)]">
        <summary className="flex cursor-pointer list-none items-center gap-4 px-5 py-5 sm:px-6">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-[11px] border border-black/[0.055] bg-[#fafaf8] text-[#555]">
            <MaintenanceIcon />
          </div>

          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2.5">
              <span className="font-mono text-[9px] font-medium uppercase tracking-[0.15em] text-[#aaa]">
                {t("batch.label")}
              </span>

              <span className="h-px w-8 bg-black/[0.08]" />
            </div>

            <h2 className="mt-2 text-[17px] font-semibold tracking-[-0.03em] text-[#292927]">
              {t("batch.title")}
            </h2>

            <p className="mt-1 max-w-[760px] text-[11px] leading-[18px] text-[#92928c]">
              {t("batch.description")}
            </p>
          </div>

          <div className="flex size-9 shrink-0 items-center justify-center rounded-full border border-black/[0.055] bg-[#fafaf8] text-[#888] transition group-hover:border-black/[0.1] group-hover:bg-white group-hover:text-[#333]">
            <ChevronIcon />
          </div>
        </summary>

        <div className="border-t border-black/[0.045] px-5 pt-5 sm:px-6">
          <div className="rounded-[16px] border border-black/[0.05] bg-[#fafaf8] p-4 sm:p-5">
            <div className="flex items-start gap-3">
              <div className="flex size-8 shrink-0 items-center justify-center rounded-full border border-black/[0.055] bg-white text-[#777]">
                <InfoIcon />
              </div>

              <div>
                <p className="text-[12px] font-semibold text-[#4f4f4b]">
                  {t("batch.guideTitle")}
                </p>

                <p className="mt-1 max-w-[820px] text-[11px] leading-[19px] text-[#8f8f89]">
                  {t("batch.guideDescription")}
                </p>
              </div>
            </div>

            <div className="mt-5 grid gap-px overflow-hidden rounded-[12px] border border-black/[0.045] bg-black/[0.045] md:grid-cols-2 xl:grid-cols-4">
              {[
                {
                  index: "01",
                  title: t("batch.guide.sourceTitle"),
                  description: t("batch.guide.sourceDescription")
                },
                {
                  index: "02",
                  title: t("batch.guide.pageTitle"),
                  description: t("batch.guide.pageDescription")
                },
                {
                  index: "03",
                  title: t("batch.guide.sizeTitle"),
                  description: t("batch.guide.sizeDescription")
                },
                {
                  index: "04",
                  title: t("batch.guide.forceTitle"),
                  description: t("batch.guide.forceDescription")
                }
              ].map((item) => (
                <div
                  key={item.index}
                  className="bg-[#fafaf8] px-4 py-4"
                >
                  <div className="flex items-center gap-2">
                    <span className="font-mono text-[8px] font-medium text-[#b4b4ae]">
                      {item.index}
                    </span>

                    <p className="text-[11px] font-semibold text-[#5f5f59]">
                      {item.title}
                    </p>
                  </div>

                  <p className="mt-2 text-[10px] leading-[17px] text-[#999]">
                    {item.description}
                  </p>
                </div>
              ))}
            </div>

            <div className="mt-4 flex flex-col gap-2 rounded-[11px] border border-black/[0.045] bg-white px-4 py-3.5 sm:flex-row sm:items-center">
              <span className="shrink-0 font-mono text-[8px] font-medium uppercase tracking-[0.12em] text-[#aaa]">
                {t("batch.example")}
              </span>

              <span className="hidden text-black/15 sm:block">
                /
              </span>

              <p className="text-[10px] leading-[17px] text-[#85857f]">
                {t("batch.exampleDescription")}
              </p>
            </div>
          </div>
        </div>

        <form
          onSubmit={runBatchNormalization}
          className="px-5 py-5 sm:px-6"
        >
          <div className="grid gap-4 md:grid-cols-[minmax(220px,1fr)_130px_150px]">
            <Field
              label={t("batch.source")}
              hint={t("batch.sourceHint")}
            >
              <select
                value={batchSource}
                onChange={(event) => setBatchSource(event.target.value)}
                className={selectClassName}
              >
                <option value="">
                  {t("batch.allSources")}
                </option>

                <option value="MOCK">
                  MOCK
                </option>

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

            <Field label={t("batch.page")}>
              <input
                type="number"
                min="0"
                value={batchPage}
                onChange={(event) => setBatchPage(event.target.value)}
                className="h-11 w-full rounded-xl border border-black/[0.09] bg-white px-3.5 text-[13px] text-[#222] outline-none transition focus:border-black/25 focus:ring-4 focus:ring-black/[0.035]"
              />
            </Field>

            <Field
              label={t("batch.size")}
              hint={t("batch.sizeHint", {
                max: MAX_RENORMALIZATION_SIZE
              })}
            >
              <input
                type="number"
                min="1"
                max={MAX_RENORMALIZATION_SIZE}
                value={batchSize}
                onChange={(event) => setBatchSize(event.target.value)}
                className="h-11 w-full rounded-xl border border-black/[0.09] bg-white px-3.5 text-[13px] text-[#222] outline-none transition focus:border-black/25 focus:ring-4 focus:ring-black/[0.035]"
              />
            </Field>
          </div>

          <div className="mt-5 flex flex-col gap-4 border-t border-black/[0.045] pt-5 sm:flex-row sm:items-center sm:justify-between">
            <Toggle
              checked={batchForce}
              onChange={setBatchForce}
              label={t("batch.force")}
            />

            <PrimaryButton
              type="submit"
              disabled={renormalizeBatch.isPending}
            >
              {renormalizeBatch.isPending
                ? t("batch.running")
                : t("batch.run")}
            </PrimaryButton>
          </div>

          {renormalizeBatch.isError ? (
            <div className="mt-5">
              <ErrorMessage error={renormalizeBatch.error} />
            </div>
          ) : null}

          {renormalizeBatch.data ? (
            <details className="group/result mt-5 overflow-hidden rounded-[14px] border border-emerald-200/70 bg-emerald-50/40">
              <summary className="flex cursor-pointer list-none items-center gap-3 px-4 py-3.5">
                <div className="flex size-8 shrink-0 items-center justify-center rounded-full border border-emerald-200 bg-white text-emerald-700">
                  <CheckIcon />
                </div>

                <span className="min-w-0 flex-1 text-[12px] font-semibold text-emerald-800">
                  {t("batch.result")}
                </span>

                <span className="text-emerald-700/60 transition-transform group-open/result:rotate-180">
                  <ChevronIcon />
                </span>
              </summary>

              <div className="border-t border-emerald-200/60 p-3">
                <ResultBox
                  title={t("batch.result")}
                  value={renormalizeBatch.data}
                />
              </div>
            </details>
          ) : null}
        </form>
      </details>
    </div>
  );
}