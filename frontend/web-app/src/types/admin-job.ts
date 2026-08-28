import type {
  ApplyType
} from "@/types/job";

export type PipelineStageStatus =
  | "NOT_CREATED"
  | "OUTDATED"
  | "PROCESSING"
  | "READY"
  | "FAILED";

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

  normalizationStatus: PipelineStageStatus;

  normalizedJobId: string | null;

  normalizationVersion: string | null;

  normalizedAt: string | null;

  embeddingStatus: PipelineStageStatus;

  embeddingJobId: string | null;

  embeddingVersion: string | null;

  embeddedAt: string | null;

  embeddingLastError: string | null;
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

export type JobPipelineStage =
  | "normalization"
  | "embedding";

export type PipelineStatusBadgeProps = {
  status: PipelineStageStatus;
};

export type CopyableJobIdProps = {
  label: string;
  value: string | null;
};

export type JobPipelineCellProps = {
  stage: JobPipelineStage;
  status: PipelineStageStatus;
  id: string | null;
  version: string | null;
  timestamp: string | null;
  error?: string | null;
  locale: string;
};

export type AdminJobRowProps = {
  job: RawJobSummary;
  index: number;
  locale: string;

  isNormalizing: boolean;
  isEmbedding: boolean;
  pending: boolean;

  onNormalize: (
    job: RawJobSummary
  ) => void;

  onEmbed: (
    job: RawJobSummary
  ) => void;
};