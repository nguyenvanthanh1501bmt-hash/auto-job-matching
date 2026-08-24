export type EmbeddingStatus =
  | "PROCESSING"
  | "READY"
  | "FAILED";

export type CandidateEmbeddingResponse = {
  // Embedding thuộc profile đã parse; rawCvId chỉ là liên kết ngược tới CV.
  candidateProfileId: string;

  rawCvId: string | null;

  parserVersion: string | null;

  textVersion: string | null;

  modelName: string | null;

  modelRevision: string | null;

  embeddingVersion: string | null;

  textHash: string | null;

  dimension: number | null;

  normalized: boolean | null;

  status: EmbeddingStatus;

  embeddedAt: string | null;

  lastError: string | null;
};

export type JobEmbeddingResponse = {
  normalizedJobId: string;

  normalizationVersion: string | null;

  modelName: string | null;

  modelRevision: string | null;

  embeddingVersion: string | null;

  textHash: string | null;

  dimension: number | null;

  normalized: boolean | null;

  status: EmbeddingStatus;

  qdrantCollection: string | null;

  qdrantPointId: string | null;

  embeddedAt: string | null;

  lastError: string | null;
};

export type RebuildEmbeddingOptions = {
  force?: boolean;
};

export type EmbeddingErrorCode =
  | "CANDIDATE_EMBEDDING_NOT_FOUND"
  | "CANDIDATE_EMBEDDING_FAILED"
  | "JOB_EMBEDDING_NOT_FOUND"
  | "JOB_EMBEDDING_FAILED"
  | "INVALID_REQUEST";