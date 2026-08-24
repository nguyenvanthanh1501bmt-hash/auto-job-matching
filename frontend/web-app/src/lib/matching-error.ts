import {toApiError} from "@/lib/api-error";

import type {
  MatchingErrorCode
} from "@/types/matching";

const MATCHING_ERROR_CODES =
  new Set<MatchingErrorCode>([
    "MATCHING_AUTHENTICATION_REQUIRED",
    "MATCHING_CANDIDATE_PROFILE_NOT_FOUND",
    "MATCHING_CANDIDATE_EMBEDDING_NOT_READY",
    "MATCHING_CANDIDATE_EMBEDDING_STALE",
    "MATCHING_CANDIDATE_EMBEDDING_INVALID",
    "MATCHING_RESULT_NOT_FOUND",
    "MATCHING_VECTOR_STORE_UNAVAILABLE",
    "MATCHING_INVALID_REQUEST"
  ]);

export function getMatchingErrorCode(
  error: unknown
): MatchingErrorCode | null {
  const apiError = toApiError(error);

  if (
    MATCHING_ERROR_CODES.has(
      apiError.error as MatchingErrorCode
    )
  ) {
    return apiError.error as MatchingErrorCode;
  }

  return null;
}

export function isMatchingResultNotFound(
  error: unknown
): boolean {
  return (
    getMatchingErrorCode(error) ===
    "MATCHING_RESULT_NOT_FOUND"
  );
}

export function isCandidateEmbeddingNotReady(
  error: unknown
): boolean {
  return (
    getMatchingErrorCode(error) ===
    "MATCHING_CANDIDATE_EMBEDDING_NOT_READY"
  );
}

export function isCandidateEmbeddingStale(
  error: unknown
): boolean {
  return (
    getMatchingErrorCode(error) ===
    "MATCHING_CANDIDATE_EMBEDDING_STALE"
  );
}

export function isCandidateEmbeddingInvalid(
  error: unknown
): boolean {
  return (
    getMatchingErrorCode(error) ===
    "MATCHING_CANDIDATE_EMBEDDING_INVALID"
  );
}

export function isMatchingVectorStoreUnavailable(
  error: unknown
): boolean {
  return (
    getMatchingErrorCode(error) ===
    "MATCHING_VECTOR_STORE_UNAVAILABLE"
  );
}

export function shouldRetryMatchingQuery(
  failureCount: number,
  error: unknown
): boolean {
  const apiError = toApiError(error);

  /**
   * Không retry những lỗi client/auth/precondition.
   */
  if (
    apiError.status === 400 ||
    apiError.status === 401 ||
    apiError.status === 403 ||
    apiError.status === 404 ||
    apiError.status === 409
  ) {
    return false;
  }

  /**
   * Qdrant/service tạm thời unavailable
   * có thể retry một vài lần.
   */
  if (
    apiError.status === 503 ||
    apiError.isNetworkError
  ) {
    return failureCount < 2;
  }

  /**
   * Các lỗi server khác chỉ retry tối đa 1 lần.
   */
  if (
    apiError.status !== null &&
    apiError.status >= 500
  ) {
    return failureCount < 1;
  }

  return false;
}