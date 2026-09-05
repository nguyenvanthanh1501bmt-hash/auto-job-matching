import type {ReactNode} from "react";

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
};