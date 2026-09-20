import type {
    CvTailoringCoachingItem,
    CvTailoringCurrentMatch,
    CvTailoringEvidenceItem,
    CvTailoringGapItem,
    CvTailoringJobSnapshot,
    CvTailoringPreviewResponse,
    CvTailoringSuggestionItem
} from "@/types/cv-tailoring";

export type CvTailoringDraftStatus =
    | "DRAFT"
    | "READY"
    | "ARCHIVED";

export type CvTailoringCoachingAnswer = {
    coachingId: string;
    answer: string;
    updatedAt: string;
};

export type CvTailoringDraftResponse = {
    draftId: string;
    candidateProfileId: string;
    normalizedJobId: string;
    status: CvTailoringDraftStatus;
    analysisId: string;
    analysisExpiresAt: string;
    rankingVersion: string;
    job: CvTailoringJobSnapshot;
    baselineMatch: CvTailoringCurrentMatch;
    evidence: CvTailoringEvidenceItem[];
    suggestions: CvTailoringSuggestionItem[];
    coaching: CvTailoringCoachingItem[];
    gaps: CvTailoringGapItem[];
    acceptedSuggestionIds: string[];
    rejectedSuggestionIds: string[];
    coachingAnswers: CvTailoringCoachingAnswer[];
    createdAt: string;
    updatedAt: string;
};

export type CvTailoringDraftUpdateRequest = {
    acceptedSuggestionIds: string[];
    rejectedSuggestionIds: string[];
    coachingAnswers: Array<{
        coachingId: string;
        answer: string;
    }>;
};

export type CvTailoringDraftPreviewResponse = {
    draft: CvTailoringDraftResponse;
    preview: CvTailoringPreviewResponse;
};