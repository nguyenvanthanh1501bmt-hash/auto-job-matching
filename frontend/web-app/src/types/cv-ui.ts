import type {ReactNode} from "react";

import type {
  CandidateProfileResponse,
  CvProcessingStatus,
  RawCvResponse,
  Skill
} from "@/types/cv";

export type CvUploadCardProps = {
  hasCurrentCv: boolean;

  isBusy: boolean;
  isUploading: boolean;
  isParsing: boolean;

  uploadProgress: number;

  error: string | null;

  onSelectFile: (
    file: File
  ) => void;
};

export type CurrentCvCardProps = {
  cv:
    | RawCvResponse
    | undefined;

  isLoading: boolean;
  isError: boolean;

  error: unknown;

  status:
    | CvProcessingStatus
    | null;

  isBusy: boolean;

  canRetryParse: boolean;

  onRetryParse: () => void;
  onClear: () => void;
};

export type CandidateProfileViewProps = {
  profile:
    CandidateProfileResponse;
};

export type ProfileSectionTitleProps = {
  children: ReactNode;

  index?: number;
};

export type ProfileTagsProps = {
  values: string[];
};

export type StructuredProfileValueProps = {
  fieldKey: string;

  value: unknown;
};

export type ProfileSkillsGridProps = {
  skills: Skill[];
};

export type ProfileSkillCardProps = {
  skill: Skill;

  index: number;
};

export type ProfileSectionNavItem = {
  id: string;
  label: string;
};

export type ProfileSectionNavProps = {
  items: ProfileSectionNavItem[];
};