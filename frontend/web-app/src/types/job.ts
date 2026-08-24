export type NormalizedJobType =
  | "FULL_TIME"
  | "PART_TIME"
  | "CONTRACT"
  | "INTERNSHIP"
  | "FREELANCE"
  | "TEMPORARY"
  | "UNKNOWN";

export type JobSeniority =
  | "EXECUTIVE"
  | "DIRECTOR"
  | "HEAD"
  | "MANAGER"
  | "SUPERVISOR"
  | "LEAD"
  | "SENIOR"
  | "MID"
  | "JUNIOR"
  | "ENTRY_LEVEL"
  | "FRESHER"
  | "TRAINEE"
  | "INTERN"
  | "UNKNOWN";

export type ApplyType =
  | "DETAIL_PAGE"
  | "DETAIL_PAGE_APPLY_BUTTON"
  | "EXTERNAL_COMPANY_SITE"
  | "EMAIL"
  | "UNKNOWN";

export type NormalizedJobListParams = {
  page?: number;
  size?: number;
  sourceCode?: string;
  normalizationVersion?: string;
};

export type NormalizedJobSummary = {
  id: string;

  rawJobId: string | null;

  sourceCode: string | null;

  sourceJobId: string | null;

  title: string | null;

  companyName: string | null;

  skills: string[];

  locations: string[];

  salaryText: string | null;

  salaryMin: number | null;

  salaryMax: number | null;

  currency: string | null;

  experienceMin: number | null;

  experienceMax: number | null;

  seniority: JobSeniority;

  jobType: NormalizedJobType;

  normalizationVersion: string | null;

  postedAt: string | null;

  deadlineAt: string | null;

  normalizedAt: string | null;
};

export type NormalizedJobDetail =
  NormalizedJobSummary & {
    sourceFingerprint: string | null;

    rawContentHash: string | null;

    locationText: string | null;

    descriptionText: string | null;

    requirementsText: string | null;

    benefitsText: string | null;

    detailUrl: string | null;

    applyUrl: string | null;

    applyType: ApplyType;
  };