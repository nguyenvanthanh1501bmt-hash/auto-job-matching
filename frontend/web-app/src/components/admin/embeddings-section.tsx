"use client";

import {useState} from "react";
import type {FormEvent} from "react";
import {useTranslations} from "next-intl";

import {useAdminCandidateEmbedding, useAdminJobEmbedding, useRebuildCandidateEmbedding, useRebuildJobEmbedding} from "@/hooks/use-admin-tools";

import {
  ErrorMessage,
  Field,
  inputClassName,
  PageHeading,
  PrimaryButton,
  ResultBox,
  SecondaryButton,
  StatusBadge,
  Toggle
} from "./admin-ui";

function CandidateIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-[19px]"
      aria-hidden="true"
    >
      <circle
        cx="12"
        cy="8"
        r="3.5"
        stroke="currentColor"
        strokeWidth="1.7"
      />

      <path
        d="M5.5 19c.8-3.2 3-5 6.5-5s5.7 1.8 6.5 5"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
    </svg>
  );
}

function JobIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-[19px]"
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

function VectorIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-[17px]"
      aria-hidden="true"
    >
      <circle
        cx="6"
        cy="12"
        r="1.8"
        stroke="currentColor"
        strokeWidth="1.6"
      />

      <circle
        cx="18"
        cy="7"
        r="1.8"
        stroke="currentColor"
        strokeWidth="1.6"
      />

      <circle
        cx="18"
        cy="17"
        r="1.8"
        stroke="currentColor"
        strokeWidth="1.6"
      />

      <path
        d="m7.8 11.2 8.4-3.4M7.8 12.8l8.4 3.4"
        stroke="currentColor"
        strokeWidth="1.6"
      />
    </svg>
  );
}

function MaintenanceIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-[17px]"
      aria-hidden="true"
    >
      <path
        d="M12 6v12M6 12h12"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />

      <circle
        cx="12"
        cy="12"
        r="8"
        stroke="currentColor"
        strokeWidth="1.5"
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

function AutomaticPipeline() {
  const t = useTranslations("admin.embeddings.pipeline");

  return (
    <section className="overflow-hidden rounded-[18px] border border-black/[0.055] bg-white shadow-[0_8px_30px_rgba(0,0,0,0.022)]">
      <div className="flex flex-col gap-5 px-5 py-5 sm:px-6 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex items-start gap-3.5">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-[11px] border border-black/[0.055] bg-[#fafaf8] text-[#555]">
            <VectorIcon />
          </div>

          <div>
            <div className="flex items-center gap-2.5">
              <span className="font-mono text-[9px] font-medium uppercase tracking-[0.15em] text-[#aaa]">
                {t("label")}
              </span>

              <span className="h-px w-8 bg-black/[0.08]" />
            </div>

            <h2 className="mt-2.5 text-[17px] font-semibold tracking-[-0.03em] text-[#292927]">
              {t("title")}
            </h2>

            <p className="mt-1.5 max-w-[650px] text-[11px] leading-[19px] text-[#8f8f89]">
              {t("description")}
            </p>
          </div>
        </div>

        <div className="grid min-w-0 shrink-0 grid-cols-[1fr_auto_1fr_auto_1fr] items-center gap-2 sm:min-w-[430px]">
          <div className="rounded-[12px] border border-black/[0.05] bg-[#fafaf8] px-3 py-3 text-center">
            <p className="font-mono text-[8px] uppercase tracking-[0.1em] text-[#aaa]">
              01
            </p>

            <p className="mt-1.5 text-[10px] font-semibold text-[#666]">
              {t("steps.source")}
            </p>
          </div>

          <span className="text-[12px] text-black/20">
            →
          </span>

          <div className="rounded-[12px] border border-black/[0.05] bg-[#fafaf8] px-3 py-3 text-center">
            <p className="font-mono text-[8px] uppercase tracking-[0.1em] text-[#aaa]">
              02
            </p>

            <p className="mt-1.5 text-[10px] font-semibold text-[#666]">
              {t("steps.ready")}
            </p>
          </div>

          <span className="text-[12px] text-black/20">
            →
          </span>

          <div className="rounded-[12px] border border-black/[0.055] bg-[#f8ffe7] px-3 py-3 text-center">
            <p className="font-mono text-[8px] uppercase tracking-[0.1em] text-[#9da96e]">
              03
            </p>

            <div className="mt-1.5 flex items-center justify-center gap-1.5">
              <span className="size-1.5 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />

              <p className="text-[10px] font-semibold text-[#5f6546]">
                {t("steps.embedding")}
              </p>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}

export function EmbeddingsSection() {
  const t = useTranslations("admin.embeddings");

  /*
   * Input state là giá trị admin đang gõ.
   * Lookup state chỉ thay đổi khi submit để query không chạy theo từng ký tự.
   */
  const [candidateProfileId, setCandidateProfileId] = useState("");
  const [candidateLookupId, setCandidateLookupId] = useState("");
  const [candidateForce, setCandidateForce] = useState(false);

  const [normalizedJobId, setNormalizedJobId] = useState("");
  const [jobLookupId, setJobLookupId] = useState("");
  const [jobForce, setJobForce] = useState(false);

  /*
   * Hai lookup hook là useQuery: ID được truyền trực tiếp vào hook.
   * Hai rebuild hook là useMutation: chỉ rebuild mới dùng mutate().
   */
  const candidateLookup = useAdminCandidateEmbedding(candidateLookupId);
  const candidateRebuild = useRebuildCandidateEmbedding();

  const jobLookup = useAdminJobEmbedding(jobLookupId);
  const jobRebuild = useRebuildJobEmbedding();

  function lookupCandidate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const id = candidateProfileId.trim();

    if (!id) {
      return;
    }

    setCandidateLookupId(id);
  }

  function rebuildCandidate() {
    const id = candidateLookupId || candidateProfileId.trim();

    if (!id) {
      return;
    }

    candidateRebuild.mutate({
      candidateProfileId: id,
      force: candidateForce
    });
  }

  function lookupJob(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    const id = normalizedJobId.trim();

    if (!id) {
      return;
    }

    setJobLookupId(id);
  }

  function rebuildJob() {
    const id = jobLookupId || normalizedJobId.trim();

    if (!id) {
      return;
    }

    jobRebuild.mutate({
      normalizedJobId: id,
      force: jobForce
    });
  }

  return (
    <div className="space-y-6">
      <PageHeading
        eyebrow={t("eyebrow")}
        title={t("title")}
        description={t("description")}
      />

      <AutomaticPipeline />

      <section className="overflow-hidden rounded-[20px] border border-black/[0.055] bg-white shadow-[0_12px_40px_rgba(0,0,0,0.025)]">
        <div className="flex items-start gap-3.5 border-b border-black/[0.045] px-5 py-5 sm:px-6">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-[11px] border border-black/[0.055] bg-[#fafaf8] text-[#555]">
            <CandidateIcon />
          </div>

          <div>
            <div className="flex items-center gap-2.5">
              <span className="font-mono text-[9px] font-medium uppercase tracking-[0.15em] text-[#aaa]">
                {t("common.inspector")}
              </span>

              <span className="h-px w-8 bg-black/[0.08]" />
            </div>

            <h2 className="mt-2.5 text-[19px] font-semibold tracking-[-0.035em] text-[#222220]">
              {t("candidate.title")}
            </h2>

            <p className="mt-1.5 max-w-[680px] text-[12px] leading-5 text-[#92928c]">
              {t("candidate.description")}
            </p>
          </div>
        </div>

        <div className="grid xl:grid-cols-[minmax(0,1fr)_320px]">
          <div className="min-w-0 px-5 py-5 sm:px-6">
            <form onSubmit={lookupCandidate}>
              <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
                <div className="min-w-0 flex-1">
                  <Field
                    label={t("candidate.field")}
                    hint={t("candidate.hint")}
                  >
                    <input
                      value={candidateProfileId}
                      onChange={(event) =>
                        setCandidateProfileId(event.target.value)
                      }
                      placeholder={t("candidate.placeholder")}
                      className={inputClassName}
                    />
                  </Field>
                </div>

                <PrimaryButton
                  type="submit"
                  disabled={
                    !candidateProfileId.trim() ||
                    candidateLookup.isFetching
                  }
                >
                  {candidateLookup.isFetching
                    ? t("common.lookingUp")
                    : t("common.lookup")}
                </PrimaryButton>
              </div>
            </form>

            {candidateLookup.isError ? (
              <div className="mt-5">
                <ErrorMessage error={candidateLookup.error} />
              </div>
            ) : null}

            {!candidateLookupId &&
            !candidateLookup.isFetching &&
            !candidateLookup.isError ? (
              <div className="mt-5 rounded-[15px] border border-black/[0.05] bg-[#fafaf8] px-4 py-4">
                <div className="flex items-start gap-3">
                  <span className="mt-[6px] size-1.5 shrink-0 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />

                  <p className="text-[11px] leading-[19px] text-[#8f8f89]">
                    {t("candidate.empty")}
                  </p>
                </div>
              </div>
            ) : null}

            {candidateLookup.isFetching ? (
              <div className="mt-5 flex min-h-[150px] items-center justify-center rounded-[15px] border border-black/[0.05] bg-[#fafaf8]">
                <div className="flex items-center gap-3">
                  <span className="size-4 animate-spin rounded-full border-2 border-black/10 border-t-black/50" />

                  <span className="text-[12px] font-medium text-[#777]">
                    {t("common.lookingUp")}
                  </span>
                </div>
              </div>
            ) : null}

            {candidateLookup.data &&
            !candidateLookup.isFetching ? (
              <div className="mt-5 overflow-hidden rounded-[15px] border border-black/[0.055] bg-[#fafaf8]">
                <div className="flex flex-col gap-4 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="font-mono text-[8px] font-medium uppercase tracking-[0.13em] text-[#aaa]">
                      {t("common.status")}
                    </p>

                    <p className="mt-1.5 truncate font-mono text-[10px] font-medium text-[#666]">
                      {candidateLookupId}
                    </p>
                  </div>

                  <StatusBadge
                    status={candidateLookup.data.status}
                  />
                </div>

                <details className="group border-t border-black/[0.05]">
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-4 px-4 py-3.5 text-[10px] font-semibold text-[#85857f] transition hover:bg-white/70 hover:text-[#444]">
                    {t("common.technicalDetails")}

                    <ChevronIcon />
                  </summary>

                  <div className="border-t border-black/[0.045] p-3">
                    <ResultBox
                      title={t("candidate.result")}
                      value={candidateLookup.data}
                    />
                  </div>
                </details>
              </div>
            ) : null}
          </div>

          <aside className="border-t border-black/[0.045] bg-[#fafaf8]/60 p-5 xl:border-t-0 xl:border-l">
            <div className="flex items-start gap-3">
              <div className="flex size-8 shrink-0 items-center justify-center rounded-full border border-black/[0.055] bg-white text-[#777]">
                <MaintenanceIcon />
              </div>

              <div>
                <p className="font-mono text-[9px] font-medium uppercase tracking-[0.13em] text-[#aaa]">
                  {t("common.maintenance")}
                </p>

                <h3 className="mt-2 text-[14px] font-semibold tracking-[-0.02em] text-[#4d4d48]">
                  {t("common.rebuildTitle")}
                </h3>

                <p className="mt-1.5 text-[10px] leading-[17px] text-[#999]">
                  {t("candidate.rebuildDescription")}
                </p>
              </div>
            </div>

            <div className="mt-5 rounded-[13px] border border-black/[0.045] bg-white px-4 py-3.5">
              <Toggle
                checked={candidateForce}
                onChange={setCandidateForce}
                label={t("common.force")}
              />
            </div>

            <div className="mt-4">
              <SecondaryButton
                onClick={rebuildCandidate}
                disabled={
                  !candidateLookupId ||
                  candidateRebuild.isPending
                }
              >
                {candidateRebuild.isPending
                  ? t("common.rebuilding")
                  : t("common.rebuild")}
              </SecondaryButton>
            </div>

            {candidateRebuild.isError ? (
              <div className="mt-4">
                <ErrorMessage
                  error={candidateRebuild.error}
                />
              </div>
            ) : null}

            {candidateRebuild.data ? (
              <details className="group mt-4 overflow-hidden rounded-[12px] border border-emerald-200/70 bg-emerald-50/50">
                <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-3.5 py-3 text-[10px] font-semibold text-emerald-800">
                  {t("common.rebuildCompleted")}

                  <ChevronIcon />
                </summary>

                <div className="border-t border-emerald-200/60 p-3">
                  <ResultBox
                    title={t("candidate.result")}
                    value={candidateRebuild.data}
                  />
                </div>
              </details>
            ) : null}
          </aside>
        </div>
      </section>

      <section className="overflow-hidden rounded-[20px] border border-black/[0.055] bg-white shadow-[0_12px_40px_rgba(0,0,0,0.025)]">
        <div className="flex items-start gap-3.5 border-b border-black/[0.045] px-5 py-5 sm:px-6">
          <div className="flex size-10 shrink-0 items-center justify-center rounded-[11px] border border-black/[0.055] bg-[#fafaf8] text-[#555]">
            <JobIcon />
          </div>

          <div>
            <div className="flex items-center gap-2.5">
              <span className="font-mono text-[9px] font-medium uppercase tracking-[0.15em] text-[#aaa]">
                {t("common.inspector")}
              </span>

              <span className="h-px w-8 bg-black/[0.08]" />
            </div>

            <h2 className="mt-2.5 text-[19px] font-semibold tracking-[-0.035em] text-[#222220]">
              {t("job.title")}
            </h2>

            <p className="mt-1.5 max-w-[680px] text-[12px] leading-5 text-[#92928c]">
              {t("job.description")}
            </p>
          </div>
        </div>

        <div className="grid xl:grid-cols-[minmax(0,1fr)_320px]">
          <div className="min-w-0 px-5 py-5 sm:px-6">
            <form onSubmit={lookupJob}>
              <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
                <div className="min-w-0 flex-1">
                  <Field
                    label={t("job.field")}
                    hint={t("job.hint")}
                  >
                    <input
                      value={normalizedJobId}
                      onChange={(event) =>
                        setNormalizedJobId(event.target.value)
                      }
                      placeholder={t("job.placeholder")}
                      className={inputClassName}
                    />
                  </Field>
                </div>

                <PrimaryButton
                  type="submit"
                  disabled={
                    !normalizedJobId.trim() ||
                    jobLookup.isFetching
                  }
                >
                  {jobLookup.isFetching
                    ? t("common.lookingUp")
                    : t("common.lookup")}
                </PrimaryButton>
              </div>
            </form>

            {jobLookup.isError ? (
              <div className="mt-5">
                <ErrorMessage error={jobLookup.error} />
              </div>
            ) : null}

            {!jobLookupId &&
            !jobLookup.isFetching &&
            !jobLookup.isError ? (
              <div className="mt-5 rounded-[15px] border border-black/[0.05] bg-[#fafaf8] px-4 py-4">
                <div className="flex items-start gap-3">
                  <span className="mt-[6px] size-1.5 shrink-0 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />

                  <p className="text-[11px] leading-[19px] text-[#8f8f89]">
                    {t("job.empty")}
                  </p>
                </div>
              </div>
            ) : null}

            {jobLookup.isFetching ? (
              <div className="mt-5 flex min-h-[150px] items-center justify-center rounded-[15px] border border-black/[0.05] bg-[#fafaf8]">
                <div className="flex items-center gap-3">
                  <span className="size-4 animate-spin rounded-full border-2 border-black/10 border-t-black/50" />

                  <span className="text-[12px] font-medium text-[#777]">
                    {t("common.lookingUp")}
                  </span>
                </div>
              </div>
            ) : null}

            {jobLookup.data &&
            !jobLookup.isFetching ? (
              <div className="mt-5 overflow-hidden rounded-[15px] border border-black/[0.055] bg-[#fafaf8]">
                <div className="flex flex-col gap-4 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
                  <div className="min-w-0">
                    <p className="font-mono text-[8px] font-medium uppercase tracking-[0.13em] text-[#aaa]">
                      {t("common.status")}
                    </p>

                    <p className="mt-1.5 truncate font-mono text-[10px] font-medium text-[#666]">
                      {jobLookupId}
                    </p>
                  </div>

                  <StatusBadge
                    status={jobLookup.data.status}
                  />
                </div>

                <details className="group border-t border-black/[0.05]">
                  <summary className="flex cursor-pointer list-none items-center justify-between gap-4 px-4 py-3.5 text-[10px] font-semibold text-[#85857f] transition hover:bg-white/70 hover:text-[#444]">
                    {t("common.technicalDetails")}

                    <ChevronIcon />
                  </summary>

                  <div className="border-t border-black/[0.045] p-3">
                    <ResultBox
                      title={t("job.result")}
                      value={jobLookup.data}
                    />
                  </div>
                </details>
              </div>
            ) : null}
          </div>

          <aside className="border-t border-black/[0.045] bg-[#fafaf8]/60 p-5 xl:border-t-0 xl:border-l">
            <div className="flex items-start gap-3">
              <div className="flex size-8 shrink-0 items-center justify-center rounded-full border border-black/[0.055] bg-white text-[#777]">
                <MaintenanceIcon />
              </div>

              <div>
                <p className="font-mono text-[9px] font-medium uppercase tracking-[0.13em] text-[#aaa]">
                  {t("common.maintenance")}
                </p>

                <h3 className="mt-2 text-[14px] font-semibold tracking-[-0.02em] text-[#4d4d48]">
                  {t("common.rebuildTitle")}
                </h3>

                <p className="mt-1.5 text-[10px] leading-[17px] text-[#999]">
                  {t("job.rebuildDescription")}
                </p>
              </div>
            </div>

            <div className="mt-5 rounded-[13px] border border-black/[0.045] bg-white px-4 py-3.5">
              <Toggle
                checked={jobForce}
                onChange={setJobForce}
                label={t("common.force")}
              />
            </div>

            <div className="mt-4">
              <SecondaryButton
                onClick={rebuildJob}
                disabled={
                  !jobLookupId ||
                  jobRebuild.isPending
                }
              >
                {jobRebuild.isPending
                  ? t("common.rebuilding")
                  : t("common.rebuild")}
              </SecondaryButton>
            </div>

            {jobRebuild.isError ? (
              <div className="mt-4">
                <ErrorMessage
                  error={jobRebuild.error}
                />
              </div>
            ) : null}

            {jobRebuild.data ? (
              <details className="group mt-4 overflow-hidden rounded-[12px] border border-emerald-200/70 bg-emerald-50/50">
                <summary className="flex cursor-pointer list-none items-center justify-between gap-3 px-3.5 py-3 text-[10px] font-semibold text-emerald-800">
                  {t("common.rebuildCompleted")}

                  <ChevronIcon />
                </summary>

                <div className="border-t border-emerald-200/60 p-3">
                  <ResultBox
                    title={t("job.result")}
                    value={jobRebuild.data}
                  />
                </div>
              </details>
            ) : null}
          </aside>
        </div>
      </section>
    </div>
  );
}