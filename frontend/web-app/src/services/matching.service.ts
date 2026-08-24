import {apiClient} from "@/lib/api-client";

import type {
  MatchingResponse,
  RunMatchingOptions
} from "@/types/matching";

const MATCHING_BASE_PATH =
  "/api/matching/candidates";

function normalizeCandidateProfileId(
  candidateProfileId: string
): string {
  const normalized =
    candidateProfileId.trim();

  if (!normalized) {
    throw new Error(
      "Candidate profile id is required"
    );
  }

  return normalized;
}

function buildCandidatePath(
  candidateProfileId: string
): string {
  const normalizedCandidateProfileId =
    normalizeCandidateProfileId(
      candidateProfileId
    );

  return `${MATCHING_BASE_PATH}/${encodeURIComponent(
    normalizedCandidateProfileId
  )}`;
}

async function run(
  candidateProfileId: string,
  options: RunMatchingOptions = {}
): Promise<MatchingResponse> {
  // Matching nhận candidateProfileId, vì profile đã parse mới là dữ liệu đầu vào.
  const path = buildCandidatePath(
    candidateProfileId
  );

  const force = options.force ?? false;

  /**
   * Backend controller:
   *
   * POST /api/matching/candidates/{candidateProfileId}
   *
   * @RequestParam(
   *   name = "force",
   *   defaultValue = "false"
   * )
   *
   * Vì vậy force là QUERY PARAMETER,
   * không phải JSON body.
   */
  const response =
    await apiClient.post<MatchingResponse>(
      path,
      undefined,
      {
        params: {
          force
        }
      }
    );

  return response.data;
}

async function getCurrent(
  candidateProfileId: string
): Promise<MatchingResponse> {
  // rawCvId chỉ thuộc CV; endpoint matching luôn dùng candidateProfileId.
  const path = buildCandidatePath(
    candidateProfileId
  );

  const response =
    await apiClient.get<MatchingResponse>(
      path
    );

  return response.data;
}

export const matchingService = {
  run,
  getCurrent
};