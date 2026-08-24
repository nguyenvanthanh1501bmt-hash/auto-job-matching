import {
  requireAdminAccess
} from "@/lib/access-control";

import {
  apiClient
} from "@/lib/api-client";

import {
  PARSER_SOURCE_CODES
} from "@/types/admin-parser";

import type {
  DetailFileParseRequest,
  DetailFileParseResponse,
  ListFileParseRequest,
  ListFileParseResponse,
  ParserSourceCode
} from "@/types/admin-parser";

const PARSER_BASE_PATH =
  "/api/parsers";

function isParserSourceCode(
  value: string
): value is ParserSourceCode {
  return (
    PARSER_SOURCE_CODES as readonly string[]
  ).includes(value);
}

function normalizeSourceCode(
  sourceCode: string
): ParserSourceCode {
  const normalized =
    sourceCode
      .trim()
      .toUpperCase();

  if (!isParserSourceCode(normalized)) {
    throw new Error(
      `Unsupported parser source: ${sourceCode}`
    );
  }

  return normalized;
}

function requireNonBlank(
  value: string,
  fieldName: string
): string {
  const normalized = value.trim();

  if (!normalized) {
    throw new Error(
      `${fieldName} is required`
    );
  }

  return normalized;
}

async function parseListFile(
  sourceCode: ParserSourceCode,
  request: ListFileParseRequest
): Promise<ListFileParseResponse> {
  // Đây là admin/debug tool, không phải luồng upload CV của user.
  requireAdminAccess();

  const source =
    normalizeSourceCode(sourceCode);

  const filePath =
    // filePath là đường dẫn filesystem phía backend, không phải File từ browser.
    requireNonBlank(
      request.filePath,
      "File path"
    );

  const baseUrl =
    requireNonBlank(
      request.baseUrl,
      "Base URL"
    );

  const response =
    await apiClient.post<ListFileParseResponse>(
      `${PARSER_BASE_PATH}/${encodeURIComponent(
        source
      )}/list-file`,
      {
        filePath,
        baseUrl
      }
    );

  return response.data;
}

async function parseDetailFile(
  sourceCode: ParserSourceCode,
  request: DetailFileParseRequest
): Promise<DetailFileParseResponse> {
  // Parser đọc file đã có trên filesystem backend để mô phỏng/debug pipeline.
  requireAdminAccess();

  const source =
    normalizeSourceCode(sourceCode);

  const filePath =
    // Browser không thể truyền đường dẫn local của file người dùng vào đây.
    requireNonBlank(
      request.filePath,
      "File path"
    );

  const detailUrl =
    requireNonBlank(
      request.detailUrl,
      "Detail URL"
    );

  const listUrl =
    request.listUrl?.trim() || null;

  const response =
    await apiClient.post<DetailFileParseResponse>(
      `${PARSER_BASE_PATH}/${encodeURIComponent(
        source
      )}/detail-file`,
      {
        detailUrl,
        filePath,
        listUrl,

        rawRetentionDays:
          request.rawRetentionDays ??
          null
      }
    );

  return response.data;
}

export const adminParserService = {
  parseListFile,
  parseDetailFile
};