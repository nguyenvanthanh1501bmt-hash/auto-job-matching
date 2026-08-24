import {apiClient} from "@/lib/api-client";

import type {PageResponse} from "@/types/pagination";

import type {
  NormalizedJobDetail,
  NormalizedJobListParams,
  NormalizedJobSummary
} from "@/types/job";

const NORMALIZED_JOBS_BASE_PATH =
  "/api/normalized-jobs";

export const DEFAULT_JOB_PAGE = 0;

export const DEFAULT_JOB_PAGE_SIZE = 20;

export const MAX_JOB_PAGE_SIZE = 100;

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

function normalizeListParams(
  params: NormalizedJobListParams
): Required<
  Pick<
    NormalizedJobListParams,
    "page" | "size"
  >
> &
  Pick<
    NormalizedJobListParams,
    "sourceCode" | "normalizationVersion"
  > {
  const page =
    params.page ?? DEFAULT_JOB_PAGE;

  const size =
    params.size ?? DEFAULT_JOB_PAGE_SIZE;

  if (
    !Number.isInteger(page) ||
    page < 0
  ) {
    throw new Error(
      "Job page must be an integer greater than or equal to 0"
    );
  }

  if (
    !Number.isInteger(size) ||
    size < 1 ||
    size > MAX_JOB_PAGE_SIZE
  ) {
    throw new Error(
      `Job page size must be between 1 and ${MAX_JOB_PAGE_SIZE}`
    );
  }

  return {
    page,
    size,

    sourceCode: normalizeOptionalText(
      params.sourceCode
    ),

    normalizationVersion:
      normalizeOptionalText(
        params.normalizationVersion
      )
  };
}

async function list(
  params: NormalizedJobListParams = {}
): Promise<
  PageResponse<NormalizedJobSummary>
> {
  // Chuẩn hóa params trước khi gửi để cache và backend nhận cùng một truy vấn.
  const normalizedParams =
    normalizeListParams(params);

  const response = await apiClient.get<
    PageResponse<NormalizedJobSummary>
  >(
    NORMALIZED_JOBS_BASE_PATH,
    {
      params: normalizedParams
    }
  );

  return response.data;
}

async function getById(
  jobId: string
): Promise<NormalizedJobDetail> {
  // Detail endpoint dùng normalized job id, không dùng raw job id.
  const normalizedJobId = jobId.trim();

  if (!normalizedJobId) {
    throw new Error(
      "Job id is required"
    );
  }

  const response =
    await apiClient.get<NormalizedJobDetail>(
      `${NORMALIZED_JOBS_BASE_PATH}/${encodeURIComponent(
        normalizedJobId
      )}`
    );

  return response.data;
}

export const jobService = {
  list,
  getById
};