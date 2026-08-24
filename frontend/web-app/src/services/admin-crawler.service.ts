import {
  apiClient
} from "@/lib/api-client";

import {
  requireAdminAccess
} from "@/lib/access-control";

import {
  LIVE_CRAWLER_SOURCES
} from "@/types/admin-crawler";

import type {
  CrawlRunResponse,
  LiveCrawlerRunOptions,
  LiveCrawlerSourceCode
} from "@/types/admin-crawler";

const CRAWLER_ADMIN_BASE_PATH =
  "/api/admin/crawlers";

export const DEFAULT_LIVE_CRAWLER_LIMIT = 15;

export const MAX_LIVE_CRAWLER_LIMIT = 50;

function isLiveCrawlerSource(
  value: string
): value is LiveCrawlerSourceCode {
  return (
    LIVE_CRAWLER_SOURCES as readonly string[]
  ).includes(value);
}

function normalizeSourceCode(
  sourceCode: string
): LiveCrawlerSourceCode {
  const normalized =
    sourceCode
      .trim()
      .toUpperCase();

  if (
    !isLiveCrawlerSource(
      normalized
    )
  ) {
    throw new Error(
      `Unsupported live crawler source: ${sourceCode}`
    );
  }

  return normalized;
}

function normalizeLimit(
  limit: number | undefined
): number {
  if (limit === undefined) {
    return DEFAULT_LIVE_CRAWLER_LIMIT;
  }

  if (!Number.isFinite(limit)) {
    return DEFAULT_LIVE_CRAWLER_LIMIT;
  }

  const normalized =
    Math.trunc(limit);

  /**
   * Mirror behavior backend:
   *
   * limit < 1 -> default 15
   * limit > 50 -> 50
   */
  if (normalized < 1) {
    return DEFAULT_LIVE_CRAWLER_LIMIT;
  }

  return Math.min(
    normalized,
    MAX_LIVE_CRAWLER_LIMIT
  );
}

async function runMock(): Promise<CrawlRunResponse> {
  // Mock crawler chỉ dành cho vận hành/admin, không thuộc user flow bình thường.
  requireAdminAccess();

  const response =
    await apiClient.post<
      CrawlRunResponse
    >(
      `${CRAWLER_ADMIN_BASE_PATH}/mock/run`
    );

  return response.data;
}

async function runLive(
  sourceCode: LiveCrawlerSourceCode,
  options: LiveCrawlerRunOptions = {}
): Promise<CrawlRunResponse> {
  // Live crawler cũng yêu cầu ADMIN dù route đã có /admin.
  requireAdminAccess();

  const normalizedSourceCode =
    normalizeSourceCode(
      sourceCode
    );

  const limit =
    normalizeLimit(
      options.limit
    );

  const response =
    await apiClient.post<
      CrawlRunResponse
    >(
      `${CRAWLER_ADMIN_BASE_PATH}/live/${encodeURIComponent(
        normalizedSourceCode
      )}/run`,
      undefined,
      {
        params: {
          limit
        }
      }
    );

  return response.data;
}

export const adminCrawlerService = {
  runMock,
  runLive
};