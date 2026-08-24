import {
  apiClient
} from "@/lib/api-client";

import {
  requireAdminAccess
} from "@/lib/access-control";

import type {
  NormalizedJobDetail
} from "@/types/job";

import type {
  NormalizeRawJobOptions,
  RawJobListParams,
  RawJobSummary,
  RenormalizationBatchRequest,
  RenormalizationBatchResponse
} from "@/types/admin-job";

const RAW_JOBS_BASE_PATH =
  "/api/raw-jobs";

const JOB_NORMALIZATION_ADMIN_BASE_PATH =
  "/api/admin/job-normalization";

export const DEFAULT_RAW_JOB_LIMIT = 20;

export const MAX_RAW_JOB_LIMIT = 100;

export const DEFAULT_RENORMALIZATION_PAGE = 0;

export const DEFAULT_RENORMALIZATION_SIZE = 100;

export const MAX_RENORMALIZATION_SIZE = 500;

function normalizeRawJobLimit(
  limit: number | undefined
): number {
  if (limit === undefined) {
    return DEFAULT_RAW_JOB_LIMIT;
  }

  if (!Number.isFinite(limit)) {
    return DEFAULT_RAW_JOB_LIMIT;
  }

  const normalized =
    Math.trunc(limit);

  return Math.min(
    Math.max(normalized, 1),
    MAX_RAW_JOB_LIMIT
  );
}

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

function normalizeBatchRequest(
  request: RenormalizationBatchRequest
): RenormalizationBatchRequest {
  const page =
    request.page ??
    DEFAULT_RENORMALIZATION_PAGE;

  const size =
    request.size ??
    DEFAULT_RENORMALIZATION_SIZE;

  if (
    !Number.isInteger(page) ||
    page < 0
  ) {
    throw new Error(
      "Renormalization page must be an integer greater than or equal to 0"
    );
  }

  if (
    !Number.isInteger(size) ||
    size < 1 ||
    size > MAX_RENORMALIZATION_SIZE
  ) {
    throw new Error(
      `Renormalization size must be between 1 and ${MAX_RENORMALIZATION_SIZE}`
    );
  }

  return {
    sourceCode:
      normalizeOptionalText(
        request.sourceCode
      ),

    page,

    size,

    force:
      request.force ?? false
  };
}

async function listRawJobs(
  params: RawJobListParams = {}
): Promise<RawJobSummary[]> {
  requireAdminAccess();

  const limit =
    normalizeRawJobLimit(
      params.limit
    );

  const response =
    await apiClient.get<
      RawJobSummary[]
    >(
      RAW_JOBS_BASE_PATH,
      {
        params: {
          limit
        }
      }
    );

  return response.data;
}

async function normalizeRawJob(
  rawJobId: string,
  options: NormalizeRawJobOptions = {}
): Promise<NormalizedJobDetail> {
  // Đây là admin operation dù URL raw-jobs không có đoạn /admin.
  requireAdminAccess();

  const normalizedRawJobId =
    rawJobId.trim();

  if (!normalizedRawJobId) {
    throw new Error(
      "Raw job id is required"
    );
  }

  const force =
    options.force ?? false;

  // Single-job normalize nhận force qua query parameter.
  const response =
    await apiClient.post<
      NormalizedJobDetail
    >(
      `${RAW_JOBS_BASE_PATH}/${encodeURIComponent(
        normalizedRawJobId
      )}/normalize`,
      undefined,
      {
        params: {
          force
        }
      }
    );

  return response.data;
}

async function renormalizeBatch(
  request: RenormalizationBatchRequest = {}
): Promise<RenormalizationBatchResponse> {
  // Batch contract khác single-job: force nằm trong JSON body.
  requireAdminAccess();

  const normalizedRequest =
    normalizeBatchRequest(request);

  const response =
    await apiClient.post<
      RenormalizationBatchResponse
    >(
      `${JOB_NORMALIZATION_ADMIN_BASE_PATH}/renormalize`,
      normalizedRequest
    );

  return response.data;
}

export const adminJobService = {
  listRawJobs,
  normalizeRawJob,
  renormalizeBatch
};