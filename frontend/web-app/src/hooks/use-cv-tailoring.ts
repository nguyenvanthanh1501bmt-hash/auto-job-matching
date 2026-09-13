"use client";

import {
  useMutation
} from "@tanstack/react-query";

import {
  useLocale
} from "next-intl";

import {
  cvTailoringService
} from "@/services/cv-tailoring.service";

import type {
  CvTailoringAnalyzeResponse,
  CvTailoringPreviewResponse
} from "@/types/cv-tailoring";

import type {
  AnalyzeCvTailoringVariables,
  PreviewCvTailoringVariables
} from "@/types/matching-ui";

export function useAnalyzeCvTailoring() {
  const locale =
      useLocale();

  return useMutation<
      CvTailoringAnalyzeResponse,
      Error,
      AnalyzeCvTailoringVariables
  >({
    mutationFn: ({
                   candidateProfileId,
                   normalizedJobId
                 }) =>
        cvTailoringService.analyze(
            candidateProfileId,
            normalizedJobId,
            locale
        )
  });
}

export function usePreviewCvTailoring() {
  return useMutation<
      CvTailoringPreviewResponse,
      Error,
      PreviewCvTailoringVariables
  >({
    mutationFn: ({
                   candidateProfileId,
                   normalizedJobId,
                   analysisId,
                   acceptedSuggestionIds
                 }) =>
        cvTailoringService.preview(
            candidateProfileId,
            normalizedJobId,
            {
              analysisId,
              acceptedSuggestionIds
            }
        )
  });
}