import type {ReactNode} from "react";

import type {CrawlRunResponse} from "@/types/admin-crawler";
import type {EmbeddingStatus} from "@/types/admin-embedding";
import type {RawJobSummary} from "@/types/admin-job";

export type AdminSection =
  | "overview"
  | "crawler"
  | "jobs"
  | "embeddings"
  | "parser";

export type AdminOperationSection = Exclude<AdminSection, "overview">;

export type AdminAccessState =
  | "checking"
  | "allowed"
  | "redirecting";

export type AdminDashboardProps = {
  adminName: string;
  locale: string;
};

export type AdminHeaderProps = {
  adminName: string;
  locale: string;
};

export type AdminSidebarProps = {
  activeSection: AdminSection;
  onSectionChange: (section: AdminSection) => void;
};

export type AdminRawJobPreview = Pick<
  RawJobSummary,
  "id" | "title" | "companyName" | "sourceCode" | "collectedAt"
>;

export type AdminOperationIconProps = {
  type: AdminOperationSection;
};

export type AdminMetricsPanelProps = {
  rawJobCount: number;
  loading: boolean;
};

export type AdminOperationCardProps = {
  type: AdminOperationSection;
  index: string;
  title: string;
  description: string;
  onClick: () => void;
};

export type AdminOperationsPanelProps = {
  onNavigate: (section: AdminOperationSection) => void;
};

export type AdminRecentJobsProps = {
  locale: string;
  jobs: AdminRawJobPreview[];
  loading: boolean;
  onOpenJobs: () => void;
};

export type AdminOverviewSectionProps = {
  locale: string;
  onNavigate: (section: AdminOperationSection) => void;
};

export type CrawlerRunType = "mock" | "live";

export type CrawlerRunStatus =
  | "idle"
  | "running"
  | "success"
  | "error";

export type CrawlerStatusBadgeProps = {
  status: CrawlerRunStatus;
};

export type CrawlerExecutionResultProps = {
  result: CrawlRunResponse;
};

export type CrawlerExecutionEmptyProps = {
  loading: boolean;
};

export type CrawlerExecutionMetricProps = {
  label: string;
  value: number | string;
};

export type AdminSectionLabelProps = {
  children: ReactNode;
};

export type AdminErrorMessageProps = {
  error: unknown;
};

export type AdminFieldProps = {
  label: string;
  hint?: string;
  children: ReactNode;
};

export type AdminPanelProps = {
  title: string;
  description?: string;
  action?: ReactNode;
  children: ReactNode;
  className?: string;
};

export type AdminButtonProps = {
  children: ReactNode;
  disabled?: boolean;
  type?: "button" | "submit";
  onClick?: () => void;
};

export type AdminToggleProps = {
  checked: boolean;
  onChange: (checked: boolean) => void;
  label: string;
};

export type AdminResultBoxProps = {
  title: string;
  value: unknown;
};

export type AdminEmbeddingStatusBadgeProps = {
  status: EmbeddingStatus;
};

export type AdminPageHeadingProps = {
  eyebrow: string;
  title: string;
  description: string;
};