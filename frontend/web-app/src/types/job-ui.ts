import type {
  ReactNode
} from "react";

export type JobDetailDrawerProps = {
  jobId: string | null;

  onClose: () => void;
};

export type JobDetailItemProps = {
  label: string;

  value: ReactNode;
};

export type JobTextSectionProps = {
  title: string;

  content: string | null;
};