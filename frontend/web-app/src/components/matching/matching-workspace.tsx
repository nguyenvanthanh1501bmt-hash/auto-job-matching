"use client";

import {
  useEffect,
  useMemo,
  useState
} from "react";

import {
  useTranslations
} from "next-intl";

import {
  JobDetailDrawer
} from "@/components/jobs/job-detail-drawer";

import {
  MatchResultCard
} from "@/components/matching/match-result-card";

import {
  MatchingEmptyState
} from "@/components/matching/matching-empty-state";

import {
  MatchingSummary
} from "@/components/matching/matching-summary";

import {
  WorkspacePageHeader
} from "@/components/user/workspace-page-header";

import {
  WorkspaceSearchInput
} from "@/components/user/workspace-search-input";

import {
  useCurrentMatching,
  useRunMatching
} from "@/hooks/use-matching";

import {
  Link
} from "@/i18n/navigation";

import {
  getApiErrorMessage
} from "@/lib/api-error";

import {
  getAuthSession
} from "@/lib/auth-storage";

import {
  getMatchingErrorCode,
  isMatchingResultNotFound
} from "@/lib/matching-error";

import {
  getUserCvContext
} from "@/lib/user-cv-context";

import type {
  MatchTier
} from "@/types/matching";

import type {
  MatchingFilter
} from "@/types/matching-ui";

const TIER_ORDER: MatchTier[] =
  [
    "STRONG",
    "STRETCH",
    "POSSIBLE",
    "EXPLORE"
  ];

function RefreshIcon() {
  return (
    <svg
      viewBox="0 0 20 20"
      fill="none"
      className="size-3.5"
      aria-hidden="true"
    >
      <path
        d="M15.2 7A5.75 5.75 0 1 0 16 11"
        stroke="currentColor"
        strokeWidth="1.55"
        strokeLinecap="round"
      />

      <path
        d="M12.5 6.75h3v-3"
        stroke="currentColor"
        strokeWidth="1.55"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function MatchingSkeleton() {
  return (
    <div className="mt-7 space-y-4">
      <div className="h-[220px] animate-pulse rounded-[22px] border border-black/[0.04] bg-white/70" />

      {Array.from({
        length: 3
      }).map(
        (
          _,
          index
        ) => (
          <div
            key={
              index
            }
            className="h-[390px] animate-pulse rounded-[22px] border border-black/[0.04] bg-white/70"
          />
        )
      )}
    </div>
  );
}

export function MatchingWorkspace() {
  const t =
    useTranslations(
      "user.matches"
    );

  const [
    contextResolved,
    setContextResolved
  ] =
    useState(false);

  const [
    candidateProfileId,
    setCandidateProfileId
  ] =
    useState<
      string | null
    >(null);

  const [
    selectedTier,
    setSelectedTier
  ] =
    useState<MatchingFilter>(
      "ALL"
    );

  const [
    searchValue,
    setSearchValue
  ] =
    useState("");

  const [
    selectedJobId,
    setSelectedJobId
  ] =
    useState<
      string | null
    >(null);

  useEffect(() => {
    const session =
      getAuthSession();

    if (!session) {
      setContextResolved(
        true
      );

      return;
    }

    const context =
      getUserCvContext(
        session.user.id
      );

    setCandidateProfileId(
      context
        ?.candidateProfileId ??
        null
    );

    setContextResolved(
      true
    );
  }, []);

  const matchingQuery =
    useCurrentMatching(
      candidateProfileId
    );

  const runMutation =
    useRunMatching();

  const matching =
    runMutation.data ??
    matchingQuery.data ??
    null;

  const tierCounts =
    useMemo(() => {
      const counts: Record<
        MatchTier,
        number
      > = {
        STRONG: 0,
        STRETCH: 0,
        POSSIBLE: 0,
        EXPLORE: 0
      };

      matching
        ?.results
        .forEach(
          (item) => {
            counts[
              item.matchTier
            ] += 1;
          }
        );

      return counts;
    }, [
      matching
    ]);

  const visibleResults =
    useMemo(() => {
      if (!matching) {
        return [];
      }

      const keyword =
        searchValue
          .trim()
          .toLowerCase();

      return matching.results.filter(
        (item) => {
          if (
            selectedTier !==
              "ALL" &&
            item.matchTier !==
              selectedTier
          ) {
            return false;
          }

          if (!keyword) {
            return true;
          }

          const searchable =
            [
              item.job.title,
              item.job.companyName,
              item.job.locationText,
              item.job.salaryText,
              ...item.job.locations,
              ...item.matchedSkills,
              ...item.missingSkills
            ]
              .filter(
                Boolean
              )
              .join(
                " "
              )
              .toLowerCase();

          return searchable.includes(
            keyword
          );
        }
      );
    }, [
      matching,
      searchValue,
      selectedTier
    ]);

  const currentError =
    runMutation.isError
      ? runMutation.error
      : matchingQuery.isError
        ? matchingQuery.error
        : null;

  const currentErrorCode =
    currentError
      ? getMatchingErrorCode(
          currentError
        )
      : null;

  const noExistingResult =
    matchingQuery.isError &&
    isMatchingResultNotFound(
      matchingQuery.error
    ) &&
    !runMutation.isError;

  const matchedCount =
    matching?.matchedCount ??
    0;

  const isRunning =
    runMutation.isPending;

  function runMatching(
    force: boolean
  ) {
    if (
      !candidateProfileId ||
      isRunning
    ) {
      return;
    }

    runMutation.mutate({
      candidateProfileId,
      force
    });
  }

  function getFriendlyError():
    string {
    switch (
      currentErrorCode
    ) {
      case "MATCHING_CANDIDATE_PROFILE_NOT_FOUND":
        return t(
          "errors.profileMissing"
        );

      case "MATCHING_CANDIDATE_EMBEDDING_NOT_READY":
        return t(
          "errors.embeddingNotReady"
        );

      case "MATCHING_CANDIDATE_EMBEDDING_STALE":
        return t(
          "errors.embeddingStale"
        );

      case "MATCHING_CANDIDATE_EMBEDDING_INVALID":
        return t(
          "errors.embeddingInvalid"
        );

      case "MATCHING_VECTOR_STORE_UNAVAILABLE":
        return t(
          "errors.vectorStore"
        );

      case "MATCHING_AUTHENTICATION_REQUIRED":
        return t(
          "errors.authentication"
        );

      case "MATCHING_INVALID_REQUEST":
        return t(
          "errors.invalidRequest"
        );

      default:
        return currentError
          ? getApiErrorMessage(
              currentError
            ) ||
              t(
                "errors.generic"
              )
          : t(
              "errors.generic"
            );
    }
  }

  const shouldGoToCv =
    currentErrorCode ===
      "MATCHING_CANDIDATE_PROFILE_NOT_FOUND" ||
    currentErrorCode ===
      "MATCHING_CANDIDATE_EMBEDDING_STALE" ||
    currentErrorCode ===
      "MATCHING_CANDIDATE_EMBEDDING_INVALID";

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
              "matchedJobs"
            ),

          value:
            matchedCount.toLocaleString()
        }}
      />

      {!contextResolved ? (
        <MatchingSkeleton />
      ) : null}

      {contextResolved &&
      !candidateProfileId ? (
        <MatchingEmptyState
          eyebrow={t(
            "states.noCv.eyebrow"
          )}
          title={t(
            "states.noCv.title"
          )}
          description={t(
            "states.noCv.description"
          )}
          action={
            <Link
              href="/cv"
              className="inline-flex h-10 items-center rounded-full bg-[#171717] px-5 text-[10px] font-semibold text-white shadow-[0_4px_14px_rgba(0,0,0,0.08)]"
            >
              {t(
                "actions.goToCv"
              )}
            </Link>
          }
        />
      ) : null}

      {contextResolved &&
      candidateProfileId &&
      matchingQuery.isLoading &&
      !matching ? (
        <MatchingSkeleton />
      ) : null}

      {contextResolved &&
      candidateProfileId &&
      noExistingResult &&
      !matching ? (
        <MatchingEmptyState
          eyebrow={t(
            "states.firstRun.eyebrow"
          )}
          title={t(
            "states.firstRun.title"
          )}
          description={t(
            "states.firstRun.description"
          )}
          action={
            <button
              type="button"
              onClick={() =>
                runMatching(
                  false
                )
              }
              disabled={
                isRunning
              }
              className="inline-flex h-10 items-center gap-2 rounded-full bg-[#171717] px-5 text-[10px] font-semibold text-white shadow-[0_4px_14px_rgba(0,0,0,0.08)] disabled:cursor-not-allowed disabled:opacity-50"
            >
              {isRunning ? (
                <span className="size-3.5 animate-spin rounded-full border-2 border-white/25 border-t-white" />
              ) : null}

              {isRunning
                ? t(
                    "actions.running"
                  )
                : t(
                    "actions.run"
                  )}
            </button>
          }
        />
      ) : null}

      {contextResolved &&
      candidateProfileId &&
      currentError &&
      !noExistingResult &&
      !matching ? (
        <MatchingEmptyState
          eyebrow={t(
            "states.error.eyebrow"
          )}
          title={t(
            "states.error.title"
          )}
          description={
            getFriendlyError()
          }
          action={
            shouldGoToCv ? (
              <Link
                href="/cv"
                className="inline-flex h-10 items-center rounded-full bg-[#171717] px-5 text-[10px] font-semibold text-white"
              >
                {t(
                  "actions.goToCv"
                )}
              </Link>
            ) : (
              <button
                type="button"
                onClick={() => {
                  if (
                    runMutation.isError
                  ) {
                    runMatching(
                      false
                    );

                    return;
                  }

                  matchingQuery.refetch();
                }}
                disabled={
                  isRunning ||
                  matchingQuery.isFetching
                }
                className="inline-flex h-10 items-center rounded-full bg-[#171717] px-5 text-[10px] font-semibold text-white disabled:cursor-not-allowed disabled:opacity-50"
              >
                {t(
                  "actions.tryAgain"
                )}
              </button>
            )
          }
        />
      ) : null}

      {matching ? (
        <>
          <MatchingSummary
            matching={
              matching
            }
          />

          <div className="mt-7 flex flex-col gap-4 border-b border-black/[0.05] pb-6">
            <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
              <WorkspaceSearchInput
                value={
                  searchValue
                }
                placeholder={t(
                  "searchPlaceholder"
                )}
                onValueChange={
                  setSearchValue
                }
                className="w-full xl:max-w-[430px]"
              />

              <div className="flex items-center justify-between gap-3 xl:justify-end">
                <p className="text-[10px] text-black/35">
                  {t(
                    "resultsCount",
                    {
                      count:
                        visibleResults.length
                    }
                  )}
                </p>

                <button
                  type="button"
                  onClick={() =>
                    runMatching(
                      true
                    )
                  }
                  disabled={
                    isRunning
                  }
                  className="inline-flex h-10 shrink-0 items-center gap-2 rounded-full border border-black/[0.065] bg-white px-4 text-[10px] font-semibold text-[#444] shadow-[0_2px_10px_rgba(0,0,0,0.02)] transition-colors hover:border-black/[0.12] hover:text-black disabled:cursor-not-allowed disabled:opacity-45"
                >
                  <span
                    className={
                      isRunning
                        ? "animate-spin"
                        : ""
                    }
                  >
                    <RefreshIcon />
                  </span>

                  {isRunning
                    ? t(
                        "actions.running"
                      )
                    : t(
                        "actions.refresh"
                      )}
                </button>
              </div>
            </div>

            <div className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1">
              <button
                type="button"
                onClick={() =>
                  setSelectedTier(
                    "ALL"
                  )
                }
                className={`h-9 shrink-0 rounded-full border px-3.5 text-[9px] font-semibold transition-colors ${
                  selectedTier ===
                  "ALL"
                    ? "border-[#171717] bg-[#171717] text-white"
                    : "border-black/[0.065] bg-white text-black/45 hover:text-black"
                }`}
              >
                {t(
                  "filters.all"
                )}{" "}
                ·{" "}
                {
                  matching
                    .results
                    .length
                }
              </button>

              {TIER_ORDER.map(
                (tier) => (
                  <button
                    key={
                      tier
                    }
                    type="button"
                    onClick={() =>
                      setSelectedTier(
                        tier
                      )
                    }
                    className={`h-9 shrink-0 rounded-full border px-3.5 text-[9px] font-semibold transition-colors ${
                      selectedTier ===
                      tier
                        ? "border-[#171717] bg-[#171717] text-white"
                        : "border-black/[0.065] bg-white text-black/45 hover:text-black"
                    }`}
                  >
                    {t(
                      `tiers.${tier}`
                    )}{" "}
                    ·{" "}
                    {
                      tierCounts[
                        tier
                      ]
                    }
                  </button>
                )
              )}
            </div>
          </div>

          {runMutation.isError ? (
            <div className="mt-5 flex flex-col gap-3 rounded-[15px] border border-black/[0.06] bg-white px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
              <p className="text-[10px] leading-5 text-black/45">
                {getFriendlyError()}
              </p>

              {shouldGoToCv ? (
                <Link
                  href="/cv"
                  className="shrink-0 text-[10px] font-semibold text-black underline decoration-black/20 underline-offset-4"
                >
                  {t(
                    "actions.goToCv"
                  )}
                </Link>
              ) : (
                <button
                  type="button"
                  onClick={() =>
                    runMatching(
                      true
                    )
                  }
                  className="shrink-0 text-[10px] font-semibold text-black underline decoration-black/20 underline-offset-4"
                >
                  {t(
                    "actions.tryAgain"
                  )}
                </button>
              )}
            </div>
          ) : null}

          {matching.results.length ===
          0 ? (
            <MatchingEmptyState
              eyebrow={t(
                "states.empty.eyebrow"
              )}
              title={t(
                "states.empty.title"
              )}
              description={t(
                "states.empty.description"
              )}
              action={
                <button
                  type="button"
                  onClick={() =>
                    runMatching(
                      true
                    )
                  }
                  disabled={
                    isRunning
                  }
                  className="inline-flex h-10 items-center rounded-full bg-[#171717] px-5 text-[10px] font-semibold text-white disabled:opacity-50"
                >
                  {isRunning
                    ? t(
                        "actions.running"
                      )
                    : t(
                        "actions.refresh"
                      )}
                </button>
              }
            />
          ) : visibleResults.length ===
            0 ? (
            <MatchingEmptyState
              eyebrow={t(
                "eyebrow"
              )}
              title={t(
                "states.noFiltered.title"
              )}
              description={t(
                "states.noFiltered.description"
              )}
            />
          ) : (
            <div className="mt-6 space-y-4">
              {visibleResults.map(
                (item) => (
                  <MatchResultCard
                    key={
                      item.normalizedJobId
                    }
                    item={
                      item
                    }
                    onOpen={
                      setSelectedJobId
                    }
                  />
                )
              )}
            </div>
          )}
        </>
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