import type {
  ApplyType
} from "@/types/job";

export const PARSER_SOURCE_CODES = [
  "MOCK",
  "ITVIEC",
  "JOBOKO",
  "TOPDEV",
  "VIECLAM24H"
] as const;

export type ParserSourceCode =
  (typeof PARSER_SOURCE_CODES)[number];

export type ListFileParseRequest = {
  /**
   * Đây là đường dẫn filesystem mà
   * BACKEND có thể đọc được.
   *
   * Không phải File object của browser.
   */
  filePath: string;

  baseUrl: string;
};

export type ListFileParseResponse = {
  sourceCode: string;

  detailUrlCount: number;

  detailUrls: string[];
};

export type DetailFileParseRequest = {
  detailUrl: string;

  /**
   * Đây là đường dẫn filesystem
   * trên máy/container backend.
   */
  filePath: string;

  listUrl?: string | null;

  /**
   * Legacy parameter.
   *
   * Controller hiện vẫn nhận field này,
   * nhưng RawJobService hiện tại bỏ qua
   * retentionDays và không tạo expiration.
   */
  rawRetentionDays?: number | null;
};

export type DetailFileParseResponse = {
  id: string;

  sourceCode: string | null;

  sourceJobId: string | null;

  fingerprint: string | null;

  title: string | null;

  companyName: string | null;

  salaryText: string | null;

  locationText: string | null;

  experienceText: string | null;

  seniorityText: string | null;

  jobTypeText: string | null;

  deadlineText: string | null;

  postedText: string | null;

  skills: string[] | null;

  detailUrl: string | null;

  applyUrl: string | null;

  applyType: ApplyType | null;

  rawHtmlStored: boolean;

  rawTextStored: boolean;
};