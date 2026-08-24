import {
  requireAdminAccess
} from "@/lib/access-control";

import {
  apiClient
} from "@/lib/api-client";

import type {
  CandidateEmbeddingResponse,
  JobEmbeddingResponse,
  RebuildEmbeddingOptions
} from "@/types/admin-embedding";

const CANDIDATE_EMBEDDING_BASE_PATH =
  "/api/admin/candidate-embeddings";

const JOB_EMBEDDING_READ_BASE_PATH =
  "/api/job-embeddings";

const JOB_EMBEDDING_ADMIN_BASE_PATH =
  "/api/admin/job-embeddings";

function normalizeId(
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

async function getCandidateEmbedding(
  candidateProfileId: string
): Promise<CandidateEmbeddingResponse> {
  // Candidate embedding gắn với candidateProfileId, không gắn với rawCvId.
  requireAdminAccess();

  const id = normalizeId(
    candidateProfileId,
    "Candidate profile id"
  );

  const response =
    await apiClient.get<CandidateEmbeddingResponse>(
      `${CANDIDATE_EMBEDDING_BASE_PATH}/${encodeURIComponent(
        id
      )}`
    );

  return response.data;
}

async function rebuildCandidateEmbedding(
  candidateProfileId: string,
  options: RebuildEmbeddingOptions = {}
): Promise<CandidateEmbeddingResponse> {
  // force là query parameter của endpoint rebuild và endpoint này yêu cầu ADMIN.
  requireAdminAccess();

  const id = normalizeId(
    candidateProfileId,
    "Candidate profile id"
  );

  const response =
    await apiClient.post<CandidateEmbeddingResponse>(
      `${CANDIDATE_EMBEDDING_BASE_PATH}/${encodeURIComponent(
        id
      )}/rebuild`,
      undefined,
      {
        params: {
          force: options.force ?? false
        }
      }
    );

  return response.data;
}

async function getJobEmbedding(
  normalizedJobId: string
): Promise<JobEmbeddingResponse> {
  // Dù URL không có /admin, endpoint embedding này vẫn yêu cầu ADMIN.
  // Job embedding dùng normalizedJobId, khác với identity của raw job.
  requireAdminAccess();

  const id = normalizeId(
    normalizedJobId,
    "Normalized job id"
  );

  const response =
    await apiClient.get<JobEmbeddingResponse>(
      `${JOB_EMBEDDING_READ_BASE_PATH}/${encodeURIComponent(
        id
      )}`
    );

  return response.data;
}

async function rebuildJobEmbedding(
  normalizedJobId: string,
  options: RebuildEmbeddingOptions = {}
): Promise<JobEmbeddingResponse> {
  // force chỉ điều khiển rebuild qua query parameter, không nằm trong body JSON.
  requireAdminAccess();

  const id = normalizeId(
    normalizedJobId,
    "Normalized job id"
  );

  const response =
    await apiClient.post<JobEmbeddingResponse>(
      `${JOB_EMBEDDING_ADMIN_BASE_PATH}/${encodeURIComponent(
        id
      )}/rebuild`,
      undefined,
      {
        params: {
          force: options.force ?? false
        }
      }
    );

  return response.data;
}

export const adminEmbeddingService = {
  getCandidateEmbedding,
  rebuildCandidateEmbedding,
  getJobEmbedding,
  rebuildJobEmbedding
};