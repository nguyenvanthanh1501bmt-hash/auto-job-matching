import type {
  ApplyType
} from "@/types/job";

export type RawJobSummary = {
  id: string;

  sourceCode: string | null;

  sourceJobId: string | null;

  fingerprint: string | null;

  title: string | null;

  companyName: string | null;

  salaryText: string | null;

  locationText: string | null;

  experienceText: string | null;

  skills: string[] | null;

  detailUrl: string | null;

  applyUrl: string | null;

  applyType: ApplyType | null;

  firstSeenAt: string | null;

  lastSeenAt: string | null;

  collectedAt: string | null;

  rawPayloadPurgedAt: string | null;
};

export type RawJobListParams = {
  limit?: number;
};

export type NormalizeRawJobOptions = {
  force?: boolean;
};

export type RenormalizationBatchRequest = {
  sourceCode?: string;
  page?: number;
  size?: number;
  force?: boolean;
};

export type RenormalizationFailure = {
  rawJobId: string;
  errorType: string;
  message: string | null;
};

export type RenormalizationBatchResponse = {
  sourceCode: string | null;

  normalizationVersion: string;

  force: boolean;

  page: number;

  size: number;

  processed: number;

  totalRawJobs: number;

  totalPages: number;

  hasNext: boolean;

  nextPage: number | null;

  created: number;

  updated: number;

  unchanged: number;

  failed: number;

  rawPayloadPurged: number;

  purgeFailed: number;

  failures: RenormalizationFailure[];
};