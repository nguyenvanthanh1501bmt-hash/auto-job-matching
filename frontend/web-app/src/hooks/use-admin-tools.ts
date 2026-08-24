"use client";

import {
  useMutation,
  useQuery,
  useQueryClient
} from "@tanstack/react-query";

import {
  adminEmbeddingService
} from "@/services/admin-embedding.service";

import {
  adminParserService
} from "@/services/admin-parser.service";

import type {
  CandidateEmbeddingResponse,
  JobEmbeddingResponse
} from "@/types/admin-embedding";

import type {
  DetailFileParseRequest,
  DetailFileParseResponse,
  ListFileParseRequest,
  ListFileParseResponse,
  ParserSourceCode
} from "@/types/admin-parser";

export const adminEmbeddingQueryKeys = {
  // Key tách riêng embedding admin theo loại resource và id để cache không lẫn dữ liệu.
  all: [
    "admin",
    "embeddings"
  ] as const,

  candidate: (
    candidateProfileId: string
  ) =>
    [
      ...adminEmbeddingQueryKeys.all,
      "candidate",
      candidateProfileId
    ] as const,

  job: (
    normalizedJobId: string
  ) =>
    [
      ...adminEmbeddingQueryKeys.all,
      "job",
      normalizedJobId
    ] as const
};

export function useAdminCandidateEmbedding(
  candidateProfileId:
    | string
    | null
    | undefined
) {
  const id =
    candidateProfileId?.trim() ?? "";

  return useQuery({
    queryKey:
      id
        ? adminEmbeddingQueryKeys.candidate(
            id
          )
        : [
            ...adminEmbeddingQueryKeys.all,
            "candidate",
            "no-id"
          ] as const,

    queryFn: () => {
      if (!id) {
        throw new Error(
          "Candidate profile id is required"
        );
      }

      return adminEmbeddingService
        .getCandidateEmbedding(id);
    },

    // Không gọi API khi chưa có candidateProfileId.
    enabled: id.length > 0,

    // Đây là công cụ debug/admin; lỗi cần hiện ngay thay vì tự retry.
    retry: false
  });
}

export type RebuildCandidateEmbeddingVariables = {
  candidateProfileId: string;
  force?: boolean;
};

export function useRebuildCandidateEmbedding() {
  const queryClient =
    useQueryClient();

  return useMutation<
    CandidateEmbeddingResponse,
    Error,
    RebuildCandidateEmbeddingVariables
  >({
    mutationFn: ({
      candidateProfileId,
      force = false
    }) =>
      adminEmbeddingService
        .rebuildCandidateEmbedding(
          candidateProfileId,
          {
            force
          }
        ),

    onSuccess: (
      embedding,
      variables
    ) => {
      const id =
        variables.candidateProfileId.trim();

      queryClient.setQueryData(
        // POST đã trả embedding mới nhất nên cập nhật cache trực tiếp.
        adminEmbeddingQueryKeys.candidate(
          id
        ),
        embedding
      );
    }
  });
}

export function useAdminJobEmbedding(
  normalizedJobId:
    | string
    | null
    | undefined
) {
  const id =
    normalizedJobId?.trim() ?? "";

  return useQuery({
    queryKey:
      id
        ? adminEmbeddingQueryKeys.job(id)
        : [
            ...adminEmbeddingQueryKeys.all,
            "job",
            "no-id"
          ] as const,

    queryFn: () => {
      if (!id) {
        throw new Error(
          "Normalized job id is required"
        );
      }

      return adminEmbeddingService
        .getJobEmbedding(id);
    },

    // Không gọi API khi chưa có normalizedJobId.
    enabled: id.length > 0,

    // Tránh retry tự động cho endpoint vận hành admin.
    retry: false
  });
}

export type RebuildJobEmbeddingVariables = {
  normalizedJobId: string;
  force?: boolean;
};

export function useRebuildJobEmbedding() {
  const queryClient =
    useQueryClient();

  return useMutation<
    JobEmbeddingResponse,
    Error,
    RebuildJobEmbeddingVariables
  >({
    mutationFn: ({
      normalizedJobId,
      force = false
    }) =>
      adminEmbeddingService
        .rebuildJobEmbedding(
          normalizedJobId,
          {
            force
          }
        ),

    onSuccess: (
      embedding,
      variables
    ) => {
      const id =
        variables.normalizedJobId.trim();

      queryClient.setQueryData(
        // Dữ liệu rebuild đã đầy đủ, không cần GET embedding thêm lần nữa.
        adminEmbeddingQueryKeys.job(id),
        embedding
      );
    }
  });
}

export type ParseListFileVariables = {
  sourceCode: ParserSourceCode;
  request: ListFileParseRequest;
};

export function useParseListFile() {
  return useMutation<
    ListFileParseResponse,
    Error,
    ParseListFileVariables
  >({
    mutationFn: ({
      sourceCode,
      request
    }) =>
      adminParserService.parseListFile(
        sourceCode,
        request
      )
  });
}

export type ParseDetailFileVariables = {
  sourceCode: ParserSourceCode;
  request: DetailFileParseRequest;
};

export function useParseDetailFile() {
  return useMutation<
    DetailFileParseResponse,
    Error,
    ParseDetailFileVariables
  >({
    mutationFn: ({
      sourceCode,
      request
    }) =>
      adminParserService.parseDetailFile(
        sourceCode,
        request
      )
  });
}