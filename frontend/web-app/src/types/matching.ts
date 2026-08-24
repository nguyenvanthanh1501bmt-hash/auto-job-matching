import type {
  ApplyType,
  NormalizedJobType
} from "@/types/job";

export type MatchTier =
  | "STRONG"
  | "STRETCH"
  | "POSSIBLE"
  | "EXPLORE";

export type MatchingErrorCode =
  | "MATCHING_AUTHENTICATION_REQUIRED"
  | "MATCHING_CANDIDATE_PROFILE_NOT_FOUND"
  | "MATCHING_CANDIDATE_EMBEDDING_NOT_READY"
  | "MATCHING_CANDIDATE_EMBEDDING_STALE"
  | "MATCHING_CANDIDATE_EMBEDDING_INVALID"
  | "MATCHING_RESULT_NOT_FOUND"
  | "MATCHING_VECTOR_STORE_UNAVAILABLE"
  | "MATCHING_INVALID_REQUEST";

export type MatchingJobSnapshot = {
  sourceCode: string | null;
  sourceJobId: string | null;

  title: string | null;
  companyName: string | null;

  locations: string[];
  locationText: string | null;
  salaryText: string | null;

  jobType: NormalizedJobType | null;
  applyType: ApplyType | null;

  detailUrl: string | null;
  applyUrl: string | null;

  postedAt: string | null;
  deadlineAt: string | null;
};

export type MatchingScoreBreakdown = {
  finalScore: number;
  semanticScore: number;
  skillScore: number;
  seniorityScore: number;
  locationScore: number;
  freshnessScore: number;
};

export type MatchingVersionSnapshot = {
  parserVersion: string | null;
  normalizationVersion: string | null;
  embeddingVersion: string | null;
  candidateTextVersion: string | null;
  jobTextVersion: string | null;
  rankingVersion: string | null;
};

export type MatchingResultItem = {
  normalizedJobId: string;
  qdrantPointId: string;

  rank: number;

  job: MatchingJobSnapshot;

  score: MatchingScoreBreakdown;

  matchTier: MatchTier;

  explanations: string[];

  matchedSkills: string[];
  missingSkills: string[];

  versions: MatchingVersionSnapshot;

  generatedAt: string;
};

export type MatchingResponse = {
  candidateProfileId: string;
  candidateEmbeddingId: string;
  rankingVersion: string;

  retrievedCount: number;
  loadedJobCount: number;
  matchedCount: number;

  reusedExisting: boolean;

  results: MatchingResultItem[];
};

export type RunMatchingOptions = {
  force?: boolean;
};