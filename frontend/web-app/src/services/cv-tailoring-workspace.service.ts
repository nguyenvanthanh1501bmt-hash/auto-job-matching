import { apiClient } from "@/lib/api-client";

import type {
    CvTailoringDraftPreviewResponse,
    CvTailoringDraftResponse,
    CvTailoringDraftUpdateRequest
} from "@/types/cv-tailoring-workspace";

const BASE_PATH = "/api/cv-tailoring";
const TIMEOUT_MS = 60_000;

function requireId(value: string, label: string): string {
    const normalized = value.trim();

    if (!normalized) {
        throw new Error(`${label} is required`);
    }

    return normalized;
}

function normalizeLocale(locale: string): "vi" | "en" {
    return locale.toLowerCase().startsWith("vi") ? "vi" : "en";
}

function buildJobDraftPath(
    candidateProfileId: string,
    normalizedJobId: string
): string {
    const candidateId = requireId(
        candidateProfileId,
        "Candidate profile id"
    );

    const jobId = requireId(
        normalizedJobId,
        "Normalized job id"
    );

    return `${BASE_PATH}/candidates/${encodeURIComponent(
        candidateId
    )}/jobs/${encodeURIComponent(
        jobId
    )}/draft`;
}

function buildDraftPath(
    draftId: string
): string {
    return `${BASE_PATH}/drafts/${encodeURIComponent(
        requireId(
            draftId,
            "Draft id"
        )
    )}`;
}

async function startOrRefreshDraft(
    candidateProfileId: string,
    normalizedJobId: string,
    locale: string
): Promise<CvTailoringDraftResponse> {
    const response =
        await apiClient.post<CvTailoringDraftResponse>(
            buildJobDraftPath(
                candidateProfileId,
                normalizedJobId
            ),
            undefined,
            {
                timeout: TIMEOUT_MS,
                headers: {
                    "Accept-Language":
                        normalizeLocale(
                            locale
                        )
                }
            }
        );

    return response.data;
}

async function getCurrentDraft(
    candidateProfileId: string,
    normalizedJobId: string
): Promise<CvTailoringDraftResponse> {
    const response =
        await apiClient.get<CvTailoringDraftResponse>(
            buildJobDraftPath(
                candidateProfileId,
                normalizedJobId
            ),
            {
                timeout: TIMEOUT_MS
            }
        );

    return response.data;
}

async function updateDraft(
    draftId: string,
    request: CvTailoringDraftUpdateRequest
): Promise<CvTailoringDraftResponse> {
    const response =
        await apiClient.put<CvTailoringDraftResponse>(
            buildDraftPath(
                draftId
            ),
            request,
            {
                timeout: TIMEOUT_MS
            }
        );

    return response.data;
}

async function generateCoachingSuggestion(
    draftId: string,
    coachingId: string,
    locale: string
): Promise<CvTailoringDraftResponse> {
    const response =
        await apiClient.post<CvTailoringDraftResponse>(
            `${buildDraftPath(
                draftId
            )}/coaching/${encodeURIComponent(
                requireId(
                    coachingId,
                    "Coaching id"
                )
            )}/generate`,
            undefined,
            {
                timeout: TIMEOUT_MS,
                headers: {
                    "Accept-Language":
                        normalizeLocale(
                            locale
                        )
                }
            }
        );

    return response.data;
}

async function previewDraft(
    draftId: string
): Promise<CvTailoringDraftPreviewResponse> {
    const response =
        await apiClient.post<CvTailoringDraftPreviewResponse>(
            `${buildDraftPath(
                draftId
            )}/preview`,
            undefined,
            {
                timeout: TIMEOUT_MS
            }
        );

    return response.data;
}

export const cvTailoringWorkspaceService = {
    startOrRefreshDraft,
    getCurrentDraft,
    updateDraft,
    generateCoachingSuggestion,
    previewDraft
};