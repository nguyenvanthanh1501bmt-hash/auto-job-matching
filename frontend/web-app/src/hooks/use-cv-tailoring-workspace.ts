"use client";

import {
    useMutation,
    useQuery
} from "@tanstack/react-query";

import {
    useLocale
} from "next-intl";

import {
    cvTailoringWorkspaceService
} from "@/services/cv-tailoring-workspace.service";

import type {
    CvTailoringDraftPreviewResponse,
    CvTailoringDraftResponse,
    CvTailoringDraftUpdateRequest
} from "@/types/cv-tailoring-workspace";

export function useCurrentCvTailoringDraft(
    candidateProfileId: string | null,
    normalizedJobId: string | null
) {
    return useQuery<
        CvTailoringDraftResponse,
        Error
    >({
        queryKey: [
            "cv-tailoring-workspace",
            "draft",
            candidateProfileId,
            normalizedJobId
        ],

        queryFn: () =>
            cvTailoringWorkspaceService
                .getCurrentDraft(
                    candidateProfileId!,
                    normalizedJobId!
                ),

        enabled:
            Boolean(
                candidateProfileId &&
                normalizedJobId
            ),

        retry: false
    });
}

export function useStartOrRefreshCvTailoringDraft() {
    const locale =
        useLocale();

    return useMutation<
        CvTailoringDraftResponse,
        Error,
        {
            candidateProfileId: string;
            normalizedJobId: string;
        }
    >({
        mutationFn: ({
                         candidateProfileId,
                         normalizedJobId
                     }) =>
            cvTailoringWorkspaceService
                .startOrRefreshDraft(
                    candidateProfileId,
                    normalizedJobId,
                    locale
                )
    });
}

export function useUpdateCvTailoringDraft() {
    return useMutation<
        CvTailoringDraftResponse,
        Error,
        {
            draftId: string;
            request: CvTailoringDraftUpdateRequest;
        }
    >({
        mutationFn: ({
                         draftId,
                         request
                     }) =>
            cvTailoringWorkspaceService
                .updateDraft(
                    draftId,
                    request
                )
    });
}

export function usePreviewCvTailoringDraft() {
    return useMutation<
        CvTailoringDraftPreviewResponse,
        Error,
        string
    >({
        mutationFn: (
            draftId
        ) =>
            cvTailoringWorkspaceService
                .previewDraft(
                    draftId
                )
    });
}