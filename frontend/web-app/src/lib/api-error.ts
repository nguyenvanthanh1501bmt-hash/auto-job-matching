import axios from "axios";

import type {
  ApiError,
  ApiErrorResponse
} from "@/types/api";

const DEFAULT_ERROR_MESSAGE =
  "An unexpected error occurred";

const NETWORK_ERROR_MESSAGE =
  "Unable to connect to the server";

function isRecord(
  value: unknown
): value is Record<string, unknown> {
  return (
    typeof value === "object" &&
    value !== null
  );
}

function isApiErrorResponse(
  value: unknown
): value is ApiErrorResponse {
  if (!isRecord(value)) {
    return false;
  }

  return (
    typeof value.error === "string" ||
    typeof value.message === "string" ||
    typeof value.status === "number"
  );
}

function parseRetryAfterHeader(
  value: unknown
): number | null {
  if (typeof value === "number") {
    return Number.isFinite(value)
      ? value
      : null;
  }

  if (typeof value !== "string") {
    return null;
  }

  const seconds = Number(value);

  if (
    Number.isFinite(seconds) &&
    seconds >= 0
  ) {
    return seconds;
  }

  return null;
}

export function toApiError(
  error: unknown
): ApiError {
  if (!axios.isAxiosError(error)) {
    if (error instanceof Error) {
      return {
        status: null,
        error: "UNKNOWN_ERROR",
        message:
          error.message ||
          DEFAULT_ERROR_MESSAGE,
        path: null,
        details: [],
        retryAfterSeconds: null,
        isNetworkError: false
      };
    }

    return {
      status: null,
      error: "UNKNOWN_ERROR",
      message: DEFAULT_ERROR_MESSAGE,
      path: null,
      details: [],
      retryAfterSeconds: null,
      isNetworkError: false
    };
  }

  /**
   * Axios không có response thường có nghĩa là:
   *
   * - backend chưa chạy
   * - sai base URL
   * - CORS
   * - mất mạng
   * - connection refused
   */
  if (!error.response) {
    return {
      status: null,
      error: "NETWORK_ERROR",
      message: NETWORK_ERROR_MESSAGE,
      path: null,
      details: [],
      retryAfterSeconds: null,
      isNetworkError: true
    };
  }

  const responseData = error.response.data;

  const retryAfterFromHeader =
    parseRetryAfterHeader(
      error.response.headers?.["retry-after"]
    );

  if (isApiErrorResponse(responseData)) {
    return {
      status:
        responseData.status ??
        error.response.status ??
        null,

      error:
        responseData.error ??
        "REQUEST_FAILED",

      message:
        responseData.message ??
        error.message ??
        DEFAULT_ERROR_MESSAGE,

      path:
        responseData.path ?? null,

      details: Array.isArray(
        responseData.details
      )
        ? responseData.details.filter(
            (
              detail
            ): detail is string =>
              typeof detail === "string"
          )
        : [],

      retryAfterSeconds:
        typeof responseData.retryAfterSeconds ===
        "number"
          ? responseData.retryAfterSeconds
          : retryAfterFromHeader,

      isNetworkError: false
    };
  }

  if (typeof responseData === "string") {
    return {
      status: error.response.status,
      error: "REQUEST_FAILED",
      message:
        responseData ||
        error.message ||
        DEFAULT_ERROR_MESSAGE,
      path: null,
      details: [],
      retryAfterSeconds:
        retryAfterFromHeader,
      isNetworkError: false
    };
  }

  return {
    status: error.response.status,
    error: "REQUEST_FAILED",
    message:
      error.message ||
      DEFAULT_ERROR_MESSAGE,
    path: null,
    details: [],
    retryAfterSeconds:
      retryAfterFromHeader,
    isNetworkError: false
  };
}

export function getApiErrorMessage(
  error: unknown
): string {
  return toApiError(error).message;
}