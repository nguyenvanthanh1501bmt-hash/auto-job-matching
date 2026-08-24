"use client";

import {
  keepPreviousData,
  useQuery
} from "@tanstack/react-query";

import {
  DEFAULT_JOB_PAGE,
  DEFAULT_JOB_PAGE_SIZE,
  jobService
} from "@/services/job.service";

import type {
  NormalizedJobListParams
} from "@/types/job";

function normalizeOptionalText(
  value: string | undefined
): string | undefined {
  if (!value) {
    return undefined;
  }

  const normalized = value.trim();

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
  // Params are part of the key so each page/filter combination has its own cache entry.
  all: ["normalized-jobs"] as const,

  lists: () =>
    [...jobQueryKeys.all, "list"] as const,

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

  detail: (jobId: string) =>
    [
      ...jobQueryKeys.details(),
      jobId
    ] as const
};

export function useNormalizedJobs(
  params: NormalizedJobListParams = {}
) {
  const normalizedParams =
    normalizeJobListParams(params);

  return useQuery({
    queryKey:
      jobQueryKeys.list(
        normalizedParams
      ),

    queryFn: () =>
      jobService.list(
        normalizedParams
      ),

    /**
     * Khi user chuyển page:
     *
     * page 0 -> page 1
     *
     * React Query vẫn giữ dữ liệu page cũ
     * trong lúc page mới đang tải.
     *
     * UI không bị nháy trắng.
     */
    placeholderData:
      keepPreviousData,

    staleTime: 60 * 1000
  });
}

export function useNormalizedJob(
  jobId: string | null | undefined
) {
  const normalizedJobId =
    jobId?.trim() ?? "";

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
      if (!normalizedJobId) {
        throw new Error(
          "Job id is required"
        );
      }

      return jobService.getById(
        normalizedJobId
      );
    },

    enabled:
      // Không gọi API với job id rỗng.
      normalizedJobId.length > 0,

    staleTime: 5 * 60 * 1000
  });
}