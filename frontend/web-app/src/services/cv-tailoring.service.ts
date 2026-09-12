import {apiClient} from "@/lib/api-client";

import type {
  CvTailoringAnalyzeResponse,
  CvTailoringPreviewRequest,
  CvTailoringPreviewResponse
} from "@/types/cv-tailoring";

const CV_TAILORING_BASE_PATH =
  "/api/cv-tailoring/candidates";

/*
 * Analyze có thể đi qua Groq rồi Gemini fallback.
 * Giữ timeout dài hơn ở riêng feature này thay vì
 * tăng timeout 30s cho toàn bộ apiClient.
 */
const CV_TAILORING_TIMEOUT_MS =
  60_000;

function requireId(
  value: string,
  label: string
): string {
  const normalized = value.trim();

  if (!normalized) {
    throw new Error(
      `${label} is required`
    );
  }

  return normalized;
}

function buildJobPath(
  candidateProfileId: string,
  normalizedJobId: string
): string {
  const candidateId = requireId(
    candidateProfileId,
    "Candidate profile id"
  );

  const jobId = requireId(
    normalizedJobId,
    "Normalized job id"
  );

  return `${CV_TAILORING_BASE_PATH}/${encodeURIComponent(
    candidateId
  )}/jobs/${encodeURIComponent(jobId)}`;
}

async function analyze(
  candidateProfileId: string,
  normalizedJobId: string
): Promise<CvTailoringAnalyzeResponse> {
  const path = buildJobPath(
    candidateProfileId,
    normalizedJobId
  );

  const response =
    await apiClient.post<CvTailoringAnalyzeResponse>(
      `${path}/analyze`,
      undefined,
      {
        timeout:
          CV_TAILORING_TIMEOUT_MS
      }
    );

  return response.data;
}

async function preview(
  candidateProfileId: string,
  normalizedJobId: string,
  request: CvTailoringPreviewRequest
): Promise<CvTailoringPreviewResponse> {
  const path = buildJobPath(
    candidateProfileId,
    normalizedJobId
  );

  const analysisId = requireId(
    request.analysisId,
    "Analysis id"
  );

  const acceptedSuggestionIds =
    request.acceptedSuggestionIds.map(
      (suggestionId) =>
        requireId(
          suggestionId,
          "Suggestion id"
        )
    );

  const response =
    await apiClient.post<CvTailoringPreviewResponse>(
      `${path}/preview`,
      {
        analysisId,
        acceptedSuggestionIds
      },
      {
        timeout:
          CV_TAILORING_TIMEOUT_MS
      }
    );

  return response.data;
}

export const cvTailoringService = {
  analyze,
  preview
};