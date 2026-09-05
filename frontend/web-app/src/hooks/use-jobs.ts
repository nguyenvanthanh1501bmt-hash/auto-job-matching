"use client";

import {
  useCallback
} from "react";

import {
  keepPreviousData,
  useQuery,
  useQueryClient
} from "@tanstack/react-query";

import {
  DEFAULT_JOB_PAGE,
  DEFAULT_JOB_PAGE_SIZE,
  jobService
} from "@/services/job.service";

import type {
  NormalizedJobListParams
} from "@/types/job";

const JOB_DETAIL_STALE_TIME =
  5 * 60 * 1000;

function normalizeOptionalText(
  value: string | undefined
): string | undefined {
  if (!value) {
    return undefined;
  }

  const normalized =
    value.trim();

  return normalized.length > 0
    ? normalized
    : undefined;
}

function normalizeJobListParams(
  params: NormalizedJobListParams
): NormalizedJobListParams {
  return {
    page:
      params.page ??
      DEFAULT_JOB_PAGE,

    size:
      params.size ??
      DEFAULT_JOB_PAGE_SIZE,

    sourceCode:
      normalizeOptionalText(
        params.sourceCode
      ),

    normalizationVersion:
      normalizeOptionalText(
        params.normalizationVersion
      )
  };
}

export const jobQueryKeys = {
  all:
    [
      "normalized-jobs"
    ] as const,

  lists: () =>
    [
      ...jobQueryKeys.all,
      "list"
    ] as const,

  list: (
    params: NormalizedJobListParams
  ) =>
    [
      ...jobQueryKeys.lists(),
      params
    ] as const,

  details: () =>
    [
      ...jobQueryKeys.all,
      "detail"
    ] as const,

  detail: (
    jobId: string
  ) =>
    [
      ...jobQueryKeys.details(),
      jobId
    ] as const
};

export function useNormalizedJobs(
  params: NormalizedJobListParams = {}
) {
  const normalizedParams =
    normalizeJobListParams(
      params
    );

  return useQuery({
    queryKey:
      jobQueryKeys.list(
        normalizedParams
      ),

    queryFn: () =>
      jobService.list(
        normalizedParams
      ),

    placeholderData:
      keepPreviousData,

    staleTime:
      60 * 1000
  });
}

export function useNormalizedJob(
  jobId:
    | string
    | null
    | undefined
) {
  const normalizedJobId =
    jobId?.trim() ??
    "";

  return useQuery({
    queryKey:
      normalizedJobId
        ? jobQueryKeys.detail(
            normalizedJobId
          )
        : [
            ...jobQueryKeys.details(),
            "no-job"
          ] as const,

    queryFn: () => {
      if (
        !normalizedJobId
      ) {
        throw new Error(
          "Job id is required"
        );
      }

      return jobService.getById(
        normalizedJobId
      );
    },

    enabled:
      normalizedJobId.length >
      0,

    staleTime:
      JOB_DETAIL_STALE_TIME
  });
}

/*
 * Prefetch detail nhưng không subscribe UI vào query.
 *
 * Drawer có thể chạy animation trước trong khi request
 * được xử lý ngầm. Khi animation hoàn tất, useNormalizedJob
 * mới subscribe vào đúng query key.
 *
 * Nếu request đã hoàn tất thì data lấy thẳng từ cache.
 */
export function usePrefetchNormalizedJob() {
  const queryClient =
    useQueryClient();

  return useCallback(
    (
      jobId: string
    ) => {
      const normalizedJobId =
        jobId.trim();

      if (
        !normalizedJobId
      ) {
        return;
      }

      void queryClient.prefetchQuery({
        queryKey:
          jobQueryKeys.detail(
            normalizedJobId
          ),

        queryFn: () =>
          jobService.getById(
            normalizedJobId
          ),

        staleTime:
          JOB_DETAIL_STALE_TIME
      });
    },
    [
      queryClient
    ]
  );
}