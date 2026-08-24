"use client";

import {
  useMutation,
  useQuery,
  useQueryClient
} from "@tanstack/react-query";

import {
  cvService,
  type CvUploadProgressHandler
} from "@/services/cv.service";

import type {
  CandidateProfileResponse,
  RawCvResponse
} from "@/types/cv";

export const cvQueryKeys = {
  // rawCvId là identity của CV; profile vẫn được cache dưới cùng resource đó.
  all: ["cvs"] as const,

  detail: (rawCvId: string) =>
    ["cvs", rawCvId] as const,

  profile: (rawCvId: string) =>
    ["cvs", rawCvId, "profile"] as const
};

export type UploadCvVariables = {
  file: File;
  onProgress?: CvUploadProgressHandler;
};

export function useUploadCv() {
  return useMutation<
    RawCvResponse,
    Error,
    UploadCvVariables
  >({
    mutationFn: ({
      file,
      onProgress
    }) => {
      return cvService.upload(
        file,
        onProgress
      );
    }
  });
}

export function useRawCv(
  rawCvId: string | null | undefined
) {
  return useQuery({
    queryKey: rawCvId
      ? cvQueryKeys.detail(rawCvId)
      : ["cvs", "no-raw-cv"] as const,

    queryFn: () => {
      if (!rawCvId) {
        throw new Error(
          "rawCvId is required"
        );
      }

      return cvService.getById(rawCvId);
    },

    // Không gọi API nếu chưa có rawCvId.
    enabled: Boolean(rawCvId)
  });
}

export function useCandidateProfile(
  rawCvId: string | null | undefined
) {
  return useQuery({
    queryKey: rawCvId
      ? cvQueryKeys.profile(rawCvId)
      : [
          "cvs",
          "no-raw-cv",
          "profile"
        ] as const,

    queryFn: () => {
      if (!rawCvId) {
        throw new Error(
          "rawCvId is required"
        );
      }

      return cvService.getProfile(
        rawCvId
      );
    },

    // Chỉ lấy profile sau khi upload đã cung cấp rawCvId.
    enabled: Boolean(rawCvId),

    /**
     * Profile đã parse thường không cần
     * refetch liên tục.
     */
    staleTime: 5 * 60 * 1000
  });
}

export function useParseCv() {
  const queryClient = useQueryClient();

  return useMutation<
    CandidateProfileResponse,
    Error,
    string
  >({
    mutationFn: (rawCvId) => {
      // Upload và parse là hai bước riêng: parse dùng rawCvId đã tạo.
      return cvService.parse(rawCvId);
    },

    onSuccess: (
      profile,
      rawCvId
    ) => {
      /**
       * Backend vừa trả profile đầy đủ,
       * nên đưa thẳng vào React Query cache.
       *
       * Không cần GET profile ngay lập tức
       * thêm một lần nữa.
       */
      queryClient.setQueryData(
        cvQueryKeys.profile(rawCvId),
        profile
      );

      /**
       * Status RawCv có thể đã chuyển:
       *
       * UPLOADED -> PARSING -> PARSED
       *
       * nên invalid lại detail.
       */
      void queryClient.invalidateQueries({
        queryKey:
          cvQueryKeys.detail(rawCvId)
      });
    }
  });
}