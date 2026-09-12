export type CvTailoringSuggestionType =
  | "REWRITE"
  | "EMPHASIZE"
  | "GAP_WARNING";

export type CvTailoringSection =
  | "PROFESSIONAL_SUMMARY"
  | "SKILLS"
  | "WORK_EXPERIENCE"
  | "PROJECT"
  | "EDUCATION"
  | "CERTIFICATION"
  | "LICENSE"
  | "LANGUAGE";

export type CvTailoringEvidenceKind =
  | "TEXT"
  | "SKILL"
  | "TOOL"
  | "EQUIPMENT"
  | "EDUCATION"
  | "CERTIFICATION"
  | "LICENSE"
  | "LANGUAGE";

export type CvTailoringJobSnapshot = {
  normalizedJobId: string;
  rank: number;
  title: string | null;
  companyName: string | null;
  locationText: string | null;
  salaryText: string | null;
  detailUrl: string | null;
  applyUrl: string | null;
};

export type CvTailoringCurrentMatch = {
  rankingVersion: string;
  finalScore: number;
  semanticScore: number;
  skillScore: number;
  seniorityScore: number;
  locationScore: number;
  freshnessScore: number;
  skillKnown: boolean;
  seniorityKnown: boolean;
  locationKnown: boolean;
  freshnessKnown: boolean;
  matchedSkills: string[];
  missingSkills: string[];
};

export type CvTailoringEvidenceItem = {
  id: string;
  section: CvTailoringSection;
  scopeId: string | null;
  kind: CvTailoringEvidenceKind;
  text: string | null;
  canonicalSkillKey: string | null;
};

export type CvTailoringSuggestionItem = {
  id: string;
  type: Exclude<
    CvTailoringSuggestionType,
    "GAP_WARNING"
  >;
  section: CvTailoringSection;
  sourceId: string | null;
  original: string | null;
  suggested: string | null;
  reason: string | null;
  targetSkills: string[];
  evidenceIds: string[];
};

export type CvTailoringGapItem = {
  id: string;
  type: "GAP_WARNING";
  skill: string | null;
  reason: string | null;
};

export type CvTailoringAnalyzeResponse = {
  analysisId: string;
  expiresAt: string;
  candidateProfileId: string;
  normalizedJobId: string;
  job: CvTailoringJobSnapshot;
  currentMatch: CvTailoringCurrentMatch;
  evidence: CvTailoringEvidenceItem[];
  suggestions: CvTailoringSuggestionItem[];
  gaps: CvTailoringGapItem[];
};

export type CvTailoringPreviewStatus =
  | "MATCHED"
  | "NOT_RETRIEVED"
  | "NOT_MATCHED";

export type CvTailoringPreviewScore = {
  status: CvTailoringPreviewStatus;
  rank: number | null;
  finalScore: number | null;
  semanticScore: number | null;
  skillScore: number | null;
  seniorityScore: number | null;
  locationScore: number | null;
  freshnessScore: number | null;
  skillKnown: boolean | null;
  seniorityKnown: boolean | null;
  locationKnown: boolean | null;
  freshnessKnown: boolean | null;
  matchedSkills: string[];
  missingSkills: string[];
  reason: string | null;
};

export type CvTailoringPreviewRequest = {
  analysisId: string;
  acceptedSuggestionIds: string[];
};

export type CvTailoringPreviewResponse = {
  analysisId: string;
  candidateProfileId: string;
  normalizedJobId: string;
  rankingVersion: string;
  temporaryEmbeddingVersion: string;
  appliedSuggestionIds: string[];
  before: CvTailoringPreviewScore;
  after: CvTailoringPreviewScore;
  retrievedCount: number;
  hydratedCount: number;
};