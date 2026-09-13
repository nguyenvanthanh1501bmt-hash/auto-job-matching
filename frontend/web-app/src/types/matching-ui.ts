import type {ReactNode} from "react";

import type {
  CvTailoringCoachingItem,
  CvTailoringEvidenceItem,
  CvTailoringGapItem,
  CvTailoringPreviewScore,
  CvTailoringSuggestionItem
} from "@/types/cv-tailoring";

import type {
  MatchTier,
  MatchingResponse,
  MatchingResultItem
} from "@/types/matching";

export type MatchingFilter =
    | "ALL"
    | MatchTier;

export type MatchingEmptyStateProps = {
  eyebrow: string;
  title: string;
  description: string;
  action?: ReactNode;
};

export type MatchingSummaryProps = {
  matching: MatchingResponse;
};

export type MatchResultCardProps = {
  item: MatchingResultItem;
  onOpen: (jobId: string) => void;
  onTailor: (jobId: string) => void;
};

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

export type CvTailoringDrawerProps = {
  candidateProfileId: string | null;
  jobId: string | null;
  onClose: () => void;
};

export type CvTailoringPlanProps = {
  suggestions: CvTailoringSuggestionItem[];
  coaching: CvTailoringCoachingItem[];
  gaps: CvTailoringGapItem[];
  evidence: CvTailoringEvidenceItem[];
  selectedIds: Set<string>;
  onToggle: (
      suggestionId: string
  ) => void;
};

export type CvTailoringScoreRow = {
  key:
      | "semantic"
      | "skills"
      | "seniority"
      | "location"
      | "freshness";
  value: number | null;
  known: boolean;
};

export type CvTailoringPreviewScoreCardProps = {
  label: string;
  snapshot: CvTailoringPreviewScore;
};