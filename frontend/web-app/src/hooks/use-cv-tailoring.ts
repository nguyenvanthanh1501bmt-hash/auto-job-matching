"use client";

import {
  useMutation
} from "@tanstack/react-query";

import {
  cvTailoringService
} from "@/services/cv-tailoring.service";

import type {
  CvTailoringAnalyzeResponse,
  CvTailoringPreviewResponse
} from "@/types/cv-tailoring";

export type AnalyzeCvTailoringVariables = {
  candidateProfileId: string;
  normalizedJobId: string;
};

export type PreviewCvTailoringVariables = {
  candidateProfileId: string;
  normalizedJobId: string;
  analysisId: string;
  acceptedSuggestionIds: string[];
};

export function useAnalyzeCvTailoring() {
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
        normalizedJobId
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