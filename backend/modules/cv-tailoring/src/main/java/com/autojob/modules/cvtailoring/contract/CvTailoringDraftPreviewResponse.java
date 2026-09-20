package com.autojob.modules.cvtailoring.contract;

public record CvTailoringDraftPreviewResponse(
        CvTailoringDraftResponse draft,
        CvTailoringPreviewResponse preview
) {
}