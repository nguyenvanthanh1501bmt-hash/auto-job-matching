"use client";

import {
  useMutation,
  useQuery,
  useQueryClient
} from "@tanstack/react-query";

import {
  adminCrawlerService
} from "@/services/admin-crawler.service";

import {
  adminEmbeddingService
} from "@/services/admin-embedding.service";

import {
  adminJobService
} from "@/services/admin-job.service";

import {
  adminParserService
} from "@/services/admin-parser.service";

import type {
  CrawlRunResponse,
  LiveCrawlerSourceCode
} from "@/types/admin-crawler";

import type {
  CandidateEmbeddingResponse,
  JobEmbeddingResponse
} from "@/types/admin-embedding";

import type {
  NormalizeRawJobOptions,
  RawJobSummary,
  RenormalizationBatchRequest,
  RenormalizationBatchResponse
} from "@/types/admin-job";

import type {
  NormalizedJobDetail
} from "@/types/job";

import type {
  DetailFileParseRequest,
  DetailFileParseResponse,
  ListFileParseRequest,
  ListFileParseResponse,
  ParserSourceCode
} from "@/types/admin-parser";

export const adminRawJobQueryKeys = {
  all: ["admin", "raw-jobs"] as const,

  list: (limit: number) =>
    [
      ...adminRawJobQueryKeys.all,
      "list",
      limit
    ] as const
};

export const adminEmbeddingQueryKeys = {
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

export function useRunMockCrawler() {
  const queryClient =
    useQueryClient();

  return useMutation<
    CrawlRunResponse,
    Error,
    void
  >({
    mutationFn: () =>
      adminCrawlerService.runMock(),

    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey:
          adminRawJobQueryKeys.all
      });
    }
  });
}

export type RunLiveCrawlerVariables = {
  sourceCode: LiveCrawlerSourceCode;
  limit?: number;
};

export function useRunLiveCrawler() {
  const queryClient =
    useQueryClient();

  return useMutation<
    CrawlRunResponse,
    Error,
    RunLiveCrawlerVariables
  >({
    mutationFn: ({
      sourceCode,
      limit
    }) =>
      adminCrawlerService.runLive(
        sourceCode,
        {limit}
      ),

    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey:
          adminRawJobQueryKeys.all
      });
    }
  });
}

export function useAdminRawJobs(
  limit = 20,
  enabled = true
) {
  return useQuery<
    RawJobSummary[]
  >({
    queryKey:
      adminRawJobQueryKeys.list(
        limit
      ),

    queryFn: () =>
      adminJobService.listRawJobs({
        limit
      }),

    enabled,
    retry: false
  });
}

export type NormalizeRawJobVariables = {
  rawJobId: string;
  options?: NormalizeRawJobOptions;
};

export function useNormalizeRawJob() {
  const queryClient =
    useQueryClient();

  return useMutation<
    NormalizedJobDetail,
    Error,
    NormalizeRawJobVariables
  >({
    mutationFn: ({
      rawJobId,
      options
    }) =>
      adminJobService.normalizeRawJob(
        rawJobId,
        options
      ),

    onSuccess: () => {
      /*
       * normalizedJobId/version/status đều nằm trong Raw Jobs API.
       * Invalidate toàn bộ list để row lấy trạng thái pipeline mới nhất.
       */
      void queryClient.invalidateQueries({
        queryKey:
          adminRawJobQueryKeys.all
      });
    }
  });
}

export function useRenormalizeBatch() {
  const queryClient =
    useQueryClient();

  return useMutation<
    RenormalizationBatchResponse,
    Error,
    RenormalizationBatchRequest
  >({
    mutationFn: (request) =>
      adminJobService.renormalizeBatch(
        request
      ),

    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey:
          adminRawJobQueryKeys.all
      });
    }
  });
}

export function useAdminCandidateEmbedding(
  candidateProfileId:
    | string
    | null
    | undefined
) {
  const id =
    candidateProfileId?.trim() ??
    "";

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
        .getCandidateEmbedding(
          id
        );
    },

    enabled:
      id.length > 0,

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
        variables
          .candidateProfileId
          .trim();

      queryClient.setQueryData(
        adminEmbeddingQueryKeys
          .candidate(id),
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
    normalizedJobId?.trim() ??
    "";

  return useQuery({
    queryKey:
      id
        ? adminEmbeddingQueryKeys.job(
            id
          )
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
        .getJobEmbedding(
          id
        );
    },

    enabled:
      id.length > 0,

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
        variables
          .normalizedJobId
          .trim();

      queryClient.setQueryData(
        adminEmbeddingQueryKeys.job(
          id
        ),
        embedding
      );

      /*
       * Raw Jobs đang hiển thị embedding status/id được join từ backend.
       * Sau rebuild phải invalidate list để row phản ánh trạng thái mới.
       */
      void queryClient.invalidateQueries({
        queryKey:
          adminRawJobQueryKeys.all
      });
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
      adminParserService
        .parseListFile(
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
      adminParserService
        .parseDetailFile(
          sourceCode,
          request
        )
  });
}