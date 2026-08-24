"use client";

import {
  useMutation,
  useQuery,
  useQueryClient
} from "@tanstack/react-query";

import {
  shouldRetryMatchingQuery
} from "@/lib/matching-error";

import {
  matchingService
} from "@/services/matching.service";

import type {
  MatchingResponse
} from "@/types/matching";

export const matchingQueryKeys = {
  // Cache matching phải gắn với candidateProfileId, không phải rawCvId.
  all: ["matching"] as const,

  candidates: () =>
    [
      ...matchingQueryKeys.all,
      "candidates"
    ] as const,

  current: (
    candidateProfileId: string
  ) =>
    [
      ...matchingQueryKeys.candidates(),
      candidateProfileId
    ] as const
};

export type RunMatchingVariables = {
  candidateProfileId: string;
  force?: boolean;
};

export function useCurrentMatching(
  candidateProfileId:
    | string
    | null
    | undefined
) {
  const normalizedCandidateProfileId =
    candidateProfileId?.trim() ?? "";

  return useQuery({
    queryKey:
      normalizedCandidateProfileId
        ? matchingQueryKeys.current(
            normalizedCandidateProfileId
          )
        : [
            ...matchingQueryKeys.candidates(),
            "no-candidate-profile"
          ] as const,

    queryFn: () => {
      if (
        !normalizedCandidateProfileId
      ) {
        throw new Error(
          "Candidate profile id is required"
        );
      }

      return matchingService.getCurrent(
        normalizedCandidateProfileId
      );
    },

    // Không gọi API nếu chưa có candidateProfileId.
    enabled:
      normalizedCandidateProfileId.length >
      0,

    staleTime: 60 * 1000,

    // Chỉ retry lỗi tạm thời; lỗi auth hoặc dữ liệu đầu vào cần hiện ngay.
    retry: (
      failureCount,
      error
    ) =>
      shouldRetryMatchingQuery(
        failureCount,
        error
      )
  });
}

export function useRunMatching() {
  const queryClient = useQueryClient();

  return useMutation<
    MatchingResponse,
    Error,
    RunMatchingVariables
  >({
    mutationFn: ({
      candidateProfileId,
      force = false
    }) => {
      // force được truyền xuống service để gửi dưới dạng query parameter.
      return matchingService.run(
        candidateProfileId,
        {
          force
        }
      );
    },

    onSuccess: (
      matching,
      variables
    ) => {
      const candidateProfileId =
        variables.candidateProfileId.trim();

      /**
       * POST matching vừa trả toàn bộ
       * MatchingResponse rồi.
       *
       * Đưa thẳng vào cache để UI không cần
       * GET lại ngay lập tức.
       */
      queryClient.setQueryData(
        matchingQueryKeys.current(
          candidateProfileId
        ),
        matching
      );
    }
  });
}