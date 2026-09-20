package com.autojob.modules.cvtailoring.api;

import com.autojob.modules.cvtailoring.contract.CvTailoringDraftPreviewResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringDraftUpdateRequest;
import com.autojob.modules.cvtailoring.service.CvTailoringWorkspaceService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cv-tailoring")
public class CvTailoringWorkspaceController {

    private final CvTailoringWorkspaceService
            workspaceService;

    public CvTailoringWorkspaceController(
            CvTailoringWorkspaceService workspaceService
    ) {
        this.workspaceService =
                workspaceService;
    }

    @PostMapping(
            "/candidates/{candidateProfileId}/jobs/{normalizedJobId}/draft"
    )
    public CvTailoringDraftResponse startOrRefresh(
            @PathVariable("candidateProfileId")
            String candidateProfileId,

            @PathVariable("normalizedJobId")
            String normalizedJobId,

            Authentication authentication
    ) {
        return workspaceService.startOrRefresh(
                candidateProfileId,
                normalizedJobId,
                resolveOwnerUserId(authentication)
        );
    }

    @GetMapping(
            "/candidates/{candidateProfileId}/jobs/{normalizedJobId}/draft"
    )
    public CvTailoringDraftResponse getCurrent(
            @PathVariable("candidateProfileId")
            String candidateProfileId,

            @PathVariable("normalizedJobId")
            String normalizedJobId,

            Authentication authentication
    ) {
        return workspaceService.getCurrent(
                candidateProfileId,
                normalizedJobId,
                resolveOwnerUserId(authentication)
        );
    }

    @GetMapping("/drafts/{draftId}")
    public CvTailoringDraftResponse getById(
            @PathVariable("draftId")
            String draftId,

            Authentication authentication
    ) {
        return workspaceService.getById(
                draftId,
                resolveOwnerUserId(authentication)
        );
    }

    @PutMapping("/drafts/{draftId}")
    public CvTailoringDraftResponse update(
            @PathVariable("draftId")
            String draftId,

            @RequestBody
            CvTailoringDraftUpdateRequest request,

            Authentication authentication
    ) {
        return workspaceService.update(
                draftId,
                resolveOwnerUserId(authentication),
                request
        );
    }

    @PostMapping(
            "/drafts/{draftId}/coaching/{coachingId}/generate"
    )
    public CvTailoringDraftResponse generateCoachingSuggestion(
            @PathVariable("draftId")
            String draftId,

            @PathVariable("coachingId")
            String coachingId,

            Authentication authentication
    ) {
        return workspaceService.generateCoachingSuggestion(
                draftId,
                coachingId,
                resolveOwnerUserId(authentication)
        );
    }

    @PostMapping("/drafts/{draftId}/preview")
    public CvTailoringDraftPreviewResponse preview(
            @PathVariable("draftId")
            String draftId,

            Authentication authentication
    ) {
        return workspaceService.preview(
                draftId,
                resolveOwnerUserId(authentication)
        );
    }

    private String resolveOwnerUserId(
            Authentication authentication
    ) {
        return authentication != null
                ? authentication.getName()
                : null;
    }
}