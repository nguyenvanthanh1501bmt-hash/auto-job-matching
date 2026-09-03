"use client";

import {
  useMemo,
  useState
} from "react";
import {
  useTranslations
} from "next-intl";

import {JobCard} from "@/components/jobs/job-card";
import {JobDetailDrawer} from "@/components/jobs/job-detail-drawer";

import {
  WorkspacePageHeader
} from "@/components/user/workspace-page-header";

import {
  useNormalizedJobs
} from "@/hooks/use-jobs";

import {
  getApiErrorMessage
} from "@/lib/api-error";

const PAGE_SIZE = 12;

function SearchIcon() {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      className="size-4"
      aria-hidden="true"
    >
      <circle
        cx="11"
        cy="11"
        r="6"
        stroke="currentColor"
        strokeWidth="1.7"
      />

      <path
        d="m16 16 4 4"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
      />
    </svg>
  );
}

function PageArrow({
  direction
}: {
  direction:
    | "left"
    | "right";
}) {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className={`size-4 ${
        direction ===
        "left"
          ? "rotate-180"
          : ""
      }`}
      aria-hidden="true"
    >
      <path
        d="M4.5 10h10.25M11 6.25 14.75 10 11 13.75"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function JobsSkeleton() {
  return (
    <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      {Array.from({
        length: 6
      }).map(
        (
          _,
          index
        ) => (
          <div
            key={index}
            className="h-[310px] animate-pulse rounded-[20px] border border-black/[0.04] bg-white/70"
          />
        )
      )}
    </div>
  );
}

export function JobsWorkspace() {
  const t =
    useTranslations(
      "user.jobs"
    );

  const [
    page,
    setPage
  ] = useState(0);

  const [
    searchValue,
    setSearchValue
  ] = useState("");

  const [
    selectedJobId,
    setSelectedJobId
  ] =
    useState<
      string | null
    >(null);

  const jobsQuery =
    useNormalizedJobs({
      page,
      size: PAGE_SIZE
    });

  const jobs =
    jobsQuery.data
      ?.content ?? [];

  const visibleJobs =
    useMemo(() => {
      const keyword =
        searchValue
          .trim()
          .toLowerCase();

      if (!keyword) {
        return jobs;
      }

      return jobs.filter(
        (job) => {
          const searchable =
            [
              job.title,
              job.companyName,
              job.sourceCode,
              job.salaryText,
              ...job.locations,
              ...job.skills
            ]
              .filter(Boolean)
              .join(" ")
              .toLowerCase();

          return searchable.includes(
            keyword
          );
        }
      );
    }, [
      jobs,
      searchValue
    ]);

  const totalElements =
    jobsQuery.data
      ?.totalElements ?? 0;

  const totalPages =
    jobsQuery.data
      ?.totalPages ?? 0;

  const currentPage =
    jobsQuery.data
      ?.page ?? page;

  return (
    <>
      <WorkspacePageHeader
        eyebrow={t(
          "eyebrow"
        )}
        title={t(
          "title"
        )}
        description={t(
          "description"
        )}
        statistic={{
          label:
            t(
              "availableJobs"
            ),

          value:
            totalElements.toLocaleString()
        }}
      />

      <div className="mt-7 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className="relative w-full sm:max-w-[430px]">
          <span className="pointer-events-none absolute left-4 top-1/2 -translate-y-1/2 text-black/30">
            <SearchIcon />
          </span>

          <input
            value={
              searchValue
            }
            onChange={(
              event
            ) =>
              setSearchValue(
                event.target
                  .value
              )
            }
            placeholder={t(
              "searchPlaceholder"
            )}
            className="h-11 w-full rounded-[13px] border border-black/[0.065] bg-white pl-11 pr-4 text-[11px] font-medium text-[#333] outline-none transition-[border-color,box-shadow] placeholder:text-black/25 focus:border-black/[0.13] focus:shadow-[0_0_0_3px_rgba(0,0,0,0.025)]"
          />
        </div>

        <div className="flex items-center gap-2 text-[10px] text-black/35">
          <span className="size-1.5 rounded-full bg-[#d9ff75] ring-1 ring-black/[0.05]" />

          {searchValue.trim()
            ? t(
                "searchResultCount",
                {
                  count:
                    visibleJobs.length
                }
              )
            : t(
                "pageStatus",
                {
                  current:
                    currentPage +
                    1,

                  total:
                    Math.max(
                      totalPages,
                      1
                    )
                }
              )}
        </div>
      </div>

      {jobsQuery.isLoading ? (
        <JobsSkeleton />
      ) : null}

      {jobsQuery.isError ? (
        <div className="mt-6 rounded-[20px] border border-black/[0.055] bg-white p-6 shadow-[0_4px_18px_rgba(0,0,0,0.025)]">
          <h2 className="text-[16px] font-bold tracking-[-0.03em]">
            {t(
              "loadError"
            )}
          </h2>

          <p className="mt-2 max-w-[600px] text-[11px] leading-5 text-black/45">
            {getApiErrorMessage(
              jobsQuery.error
            )}
          </p>

          <button
            type="button"
            onClick={() =>
              jobsQuery.refetch()
            }
            className="mt-5 h-10 rounded-full bg-[#171717] px-5 text-[10px] font-semibold text-white"
          >
            {t(
              "tryAgain"
            )}
          </button>
        </div>
      ) : null}

      {!jobsQuery.isLoading &&
      !jobsQuery.isError &&
      visibleJobs.length ===
        0 ? (
        <div className="mt-6 flex min-h-[340px] flex-col items-center justify-center rounded-[20px] border border-dashed border-black/[0.08] bg-white/45 px-6 text-center">
          <div className="flex size-11 items-center justify-center rounded-[14px] bg-white shadow-[0_3px_16px_rgba(0,0,0,0.035)]">
            <SearchIcon />
          </div>

          <h2 className="mt-4 text-[15px] font-bold tracking-[-0.03em] text-[#333]">
            {searchValue.trim()
              ? t(
                  "emptySearchTitle"
                )
              : t(
                  "emptyTitle"
                )}
          </h2>

          <p className="mt-2 max-w-[420px] text-[11px] leading-5 text-black/40">
            {searchValue.trim()
              ? t(
                  "emptySearchDescription"
                )
              : t(
                  "emptyDescription"
                )}
          </p>

          {searchValue.trim() ? (
            <button
              type="button"
              onClick={() =>
                setSearchValue(
                  ""
                )
              }
              className="mt-5 h-9 rounded-full border border-black/[0.07] bg-white px-4 text-[10px] font-semibold text-[#555]"
            >
              {t(
                "clearSearch"
              )}
            </button>
          ) : null}
        </div>
      ) : null}

      {!jobsQuery.isLoading &&
      !jobsQuery.isError &&
      visibleJobs.length >
        0 ? (
        <div className="mt-6 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {visibleJobs.map(
            (job) => (
              <JobCard
                key={job.id}
                job={job}
                onOpen={
                  setSelectedJobId
                }
              />
            )
          )}
        </div>
      ) : null}

      {!jobsQuery.isLoading &&
      !jobsQuery.isError &&
      totalPages > 1 ? (
        <div className="mt-8 flex items-center justify-between border-t border-black/[0.05] pt-6">
          <button
            type="button"
            disabled={
              currentPage <= 0 ||
              jobsQuery.isFetching
            }
            onClick={() =>
              setPage(
                (value) =>
                  Math.max(
                    value - 1,
                    0
                  )
              )
            }
            className="inline-flex h-10 items-center gap-2 rounded-full border border-black/[0.065] bg-white px-4 text-[10px] font-semibold text-[#444] disabled:cursor-not-allowed disabled:opacity-35"
          >
            <PageArrow direction="left" />

            {t(
              "previous"
            )}
          </button>

          <div className="flex items-center gap-2">
            <span className="font-mono text-[9px] text-black/35">
              {String(
                currentPage + 1
              ).padStart(
                2,
                "0"
              )}
            </span>

            <span className="h-px w-6 bg-black/[0.1]" />

            <span className="font-mono text-[9px] text-black/22">
              {String(
                totalPages
              ).padStart(
                2,
                "0"
              )}
            </span>
          </div>

          <button
            type="button"
            disabled={
              currentPage >=
                totalPages - 1 ||
              jobsQuery.isFetching
            }
            onClick={() =>
              setPage(
                (value) =>
                  value + 1
              )
            }
            className="inline-flex h-10 items-center gap-2 rounded-full bg-[#171717] px-4 text-[10px] font-semibold text-white disabled:cursor-not-allowed disabled:opacity-35"
          >
            {t(
              "next"
            )}

            <PageArrow direction="right" />
          </button>
        </div>
      ) : null}

      <JobDetailDrawer
        jobId={
          selectedJobId
        }
        onClose={() =>
          setSelectedJobId(
            null
          )
        }
      />
    </>
  );
}