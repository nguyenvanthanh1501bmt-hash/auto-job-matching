"use client";

import {useState} from "react";
import type {FormEvent} from "react";
import {useLocale, useTranslations} from "next-intl";

import {
  useAdminRawJobs,
  useNormalizeRawJob,
  useRebuildJobEmbedding,
  useRenormalizeBatch
} from "@/hooks/use-admin-tools";

import {
  DEFAULT_RAW_JOB_LIMIT,
  DEFAULT_RENORMALIZATION_SIZE,
  MAX_RAW_JOB_LIMIT,
  MAX_RENORMALIZATION_SIZE
} from "@/services/admin-job.service";

import {LIVE_CRAWLER_SOURCES} from "@/types/admin-crawler";
import type {RawJobSummary} from "@/types/admin-job";

import {AdminJobRow} from "./admin-job-row";
import {
  ErrorMessage,
  Field,
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

  const [rawJobLimit, setRawJobLimit] = useState(
    String(DEFAULT_RAW_JOB_LIMIT)
  );

  const [normalizingId, setNormalizingId] = useState<string | null>(null);
  const [embeddingId, setEmbeddingId] = useState<string | null>(null);
  const [batchSource, setBatchSource] = useState("");
  const [batchPage, setBatchPage] = useState("0");
  const [batchSize, setBatchSize] = useState(
    String(DEFAULT_RENORMALIZATION_SIZE)
  );
  const [batchForce, setBatchForce] = useState(false);

  const normalizedRawJobLimit = Math.min(
    Math.max(
      numberFromInput(rawJobLimit, DEFAULT_RAW_JOB_LIMIT),
      1
    ),
    MAX_RAW_JOB_LIMIT
  );

  const rawJobsQuery = useAdminRawJobs(
    normalizedRawJobLimit,
    true
  );

  const normalizeRawJob = useNormalizeRawJob();
  const rebuildJobEmbedding = useRebuildJobEmbedding();
  const renormalizeBatch = useRenormalizeBatch();

  const rawJobs = rawJobsQuery.data ?? [];

  const pipelinePending =
    normalizeRawJob.isPending ||
    rebuildJobEmbedding.isPending;

  function normalizeJob(job: RawJobSummary) {
    setNormalizingId(job.id);

    /*
     * Nếu normalized document đã tồn tại thì force=true. Action thủ công này
     * có mục đích recompute dữ liệu chứ không chỉ bảo đảm record đã được tạo.
     */
    normalizeRawJob.mutate(
      {
        rawJobId: job.id,
        options: {
          force: job.normalizationStatus !== "NOT_CREATED"
        }
      },
      {
        onSettled: () => {
          setNormalizingId(null);
        }
      }
    );
  }

  function embedJob(job: RawJobSummary) {
    const normalizedJobId = job.normalizedJobId?.trim();

    /*
     * Embedding contract nhận normalizedJobId. Không fallback sang rawJobId
     * vì hai ID thuộc hai stage khác nhau dù hình thức của chúng khá giống nhau.
     */
    if (
      !normalizedJobId ||
      job.normalizationStatus !== "READY" ||
      job.embeddingStatus === "PROCESSING"
    ) {
      return;
    }

    setEmbeddingId(normalizedJobId);

    rebuildJobEmbedding.mutate(
      {
        normalizedJobId,
        force: job.embeddingStatus !== "NOT_CREATED"
      },
      {
        onSettled: () => {
          setEmbeddingId(null);
        }
      }
    );
  }

  function runBatchNormalization(
    event: FormEvent<HTMLFormElement>
  ) {
    event.preventDefault();

    renormalizeBatch.mutate({
      sourceCode: batchSource || undefined,
      page: numberFromInput(batchPage, 0),
      size: numberFromInput(
        batchSize,
        DEFAULT_RENORMALIZATION_SIZE
      ),
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
        <div className="border-b border-black/[0.055] px-5 py-5 sm:px-6">
          <div className="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
            <div className="flex items-start gap-3.5">
              <div className="flex size-10 shrink-0 items-center justify-center rounded-[11px] border border-black/[0.07] bg-[#fafaf8] text-[#444]">
                <JobsIcon />
              </div>

              <div>
                <div className="flex items-center gap-2.5">
                  <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-[#777771]">
                    {t("rawJobs.workspace")}
                  </span>

                  <span className="h-px w-8 bg-black/[0.1]" />
                </div>

                <div className="mt-2.5 flex flex-wrap items-center gap-2.5">
                  <h2 className="text-[20px] font-semibold tracking-[-0.035em] text-[#20201e]">
                    {t("rawJobs.title")}
                  </h2>

                  {!rawJobsQuery.isLoading ? (
                    <span className="rounded-full border border-black/[0.07] bg-[#fafaf8] px-2.5 py-1 font-mono text-[10px] font-semibold text-[#666660]">
                      {rawJobs.length}
                    </span>
                  ) : null}
                </div>

                <p className="mt-1.5 max-w-[680px] text-[13px] font-medium leading-6 text-[#666660]">
                  {t("rawJobs.description")}
                </p>
              </div>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <div className="flex h-10 items-center rounded-[11px] border border-black/[0.08] bg-[#fafaf8]">
                <span className="border-r border-black/[0.06] px-3 font-mono text-[9px] font-semibold uppercase tracking-[0.09em] text-[#777771]">
                  {t("rawJobs.limitLabel")}
                </span>

                <input
                  type="number"
                  min="1"
                  max={MAX_RAW_JOB_LIMIT}
                  value={rawJobLimit}
                  onChange={(event) =>
                    setRawJobLimit(event.target.value)
                  }
                  className="h-full w-[68px] bg-transparent px-2 text-center text-[13px] font-semibold text-[#333330] outline-none"
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

          <div className="mt-5 flex items-start gap-3 border-t border-black/[0.055] pt-4">
            <span className="mt-[8px] size-1.5 shrink-0 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.07]" />

            <p className="max-w-[800px] text-[12px] font-medium leading-5 text-[#666660]">
              {t("rawJobs.pipelineHint")}
            </p>
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

        {rebuildJobEmbedding.isError ? (
          <div className="px-5 pt-5 sm:px-6">
            <ErrorMessage error={rebuildJobEmbedding.error} />
          </div>
        ) : null}

        {rawJobsQuery.isLoading ? (
          <div className="flex min-h-[360px] items-center justify-center">
            <div className="flex items-center gap-3">
              <span className="size-4 animate-spin rounded-full border-2 border-black/10 border-t-black/50" />

              <span className="text-[13px] font-medium text-[#666660]">
                {t("rawJobs.loading")}
              </span>
            </div>
          </div>
        ) : null}

        {!rawJobsQuery.isLoading && rawJobs.length === 0 ? (
          <div className="flex min-h-[360px] items-center justify-center px-6 text-center">
            <div>
              <div className="mx-auto flex size-10 items-center justify-center rounded-full border border-black/[0.06] bg-[#fafaf8]">
                <span className="size-1.5 rounded-full bg-[#d9ff75]" />
              </div>

              <p className="mt-4 text-[13px] font-medium leading-6 text-[#777771]">
                {t("rawJobs.empty")}
              </p>
            </div>
          </div>
        ) : null}

        {!rawJobsQuery.isLoading && rawJobs.length > 0 ? (
          <div>
            {/*
             * Collapsed table chỉ phục vụ scan nhanh. Metadata kỹ thuật nằm
             * trong expandable row để tăng font mà không làm bảng bị tràn ngang.
             */}
            <div className="hidden grid-cols-[30px_minmax(0,1.55fr)_94px_minmax(0,0.9fr)_250px_125px_34px] items-center gap-4 border-b border-black/[0.055] bg-[#fafaf8]/85 px-6 py-3.5 font-mono text-[10px] font-semibold uppercase tracking-[0.09em] text-[#777771] xl:grid">
              <span>#</span>
              <span>{t("rawJobs.columns.job")}</span>
              <span>{t("rawJobs.columns.source")}</span>
              <span>{t("rawJobs.columns.location")}</span>
              <span>{t("rawJobs.columns.pipeline")}</span>
              <span>{t("rawJobs.columns.collected")}</span>
              <span />
            </div>

            <div className="divide-y divide-black/[0.055]">
              {rawJobs.map((job, index) => (
                <AdminJobRow
                  key={job.id}
                  job={job}
                  index={index}
                  locale={locale}
                  isNormalizing={normalizingId === job.id}
                  isEmbedding={
                    embeddingId !== null &&
                    embeddingId === job.normalizedJobId
                  }
                  pending={pipelinePending}
                  onNormalize={normalizeJob}
                  onEmbed={embedJob}
                />
              ))}
            </div>
          </div>
        ) : null}
      </section>

      <details className="group overflow-hidden rounded-[20px] border border-black/[0.055] bg-white shadow-[0_10px_32px_rgba(0,0,0,0.02)]">
        <summary className="flex cursor-pointer list-none items-center gap-4 px-5 py-5 sm:px-6 [&::-webkit-details-marker]:hidden">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-[11px] border border-black/[0.07] bg-[#fafaf8] text-[#444]">
            <MaintenanceIcon />
          </div>

          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2.5">
              <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-[#777771]">
                {t("batch.label")}
              </span>

              <span className="h-px w-8 bg-black/[0.1]" />
            </div>

            <h2 className="mt-2 text-[18px] font-semibold tracking-[-0.03em] text-[#292927]">
              {t("batch.title")}
            </h2>

            <p className="mt-1 max-w-[760px] text-[12px] font-medium leading-5 text-[#666660]">
              {t("batch.description")}
            </p>
          </div>

          <div className="flex size-9 shrink-0 items-center justify-center rounded-full border border-black/[0.07] bg-[#fafaf8] text-[#666]">
            <ChevronIcon />
          </div>
        </summary>

        <div className="border-t border-black/[0.055] px-5 pt-5 sm:px-6">
          <div className="rounded-[16px] border border-black/[0.06] bg-[#fafaf8] p-4 sm:p-5">
            <div className="flex items-start gap-3">
              <div className="flex size-8 shrink-0 items-center justify-center rounded-full border border-black/[0.07] bg-white text-[#555]">
                <InfoIcon />
              </div>

              <div>
                <p className="text-[13px] font-semibold text-[#40403c]">
                  {t("batch.guideTitle")}
                </p>

                <p className="mt-1 max-w-[820px] text-[12px] font-medium leading-5 text-[#666660]">
                  {t("batch.guideDescription")}
                </p>
              </div>
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
                onChange={(event) =>
                  setBatchSource(event.target.value)
                }
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
                onChange={(event) =>
                  setBatchPage(event.target.value)
                }
                className="h-11 w-full rounded-xl border border-black/[0.09] bg-white px-3.5 text-[13px] text-[#222] outline-none"
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
                onChange={(event) =>
                  setBatchSize(event.target.value)
                }
                className="h-11 w-full rounded-xl border border-black/[0.09] bg-white px-3.5 text-[13px] text-[#222] outline-none"
              />
            </Field>
          </div>

          <div className="mt-5 flex flex-col gap-4 border-t border-black/[0.055] pt-5 sm:flex-row sm:items-center sm:justify-between">
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
            <div className="mt-5">
              <ResultBox
                title={t("batch.result")}
                value={renormalizeBatch.data}
              />
            </div>
          ) : null}
        </form>
      </details>
    </div>
  );
}