package com.autojob.modules.cvtailoring.api;

import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewRequest;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewResponse;
import com.autojob.modules.cvtailoring.service.CvTailoringAnalysisService;
import com.autojob.modules.cvtailoring.service.CvTailoringPreviewService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cv-tailoring/candidates")
public class CvTailoringController {

    private final CvTailoringAnalysisService
            cvTailoringAnalysisService;

    private final CvTailoringPreviewService
            cvTailoringPreviewService;

    public CvTailoringController(
            CvTailoringAnalysisService cvTailoringAnalysisService,
            CvTailoringPreviewService cvTailoringPreviewService
    ) {
        this.cvTailoringAnalysisService =
                cvTailoringAnalysisService;

        this.cvTailoringPreviewService =
                cvTailoringPreviewService;
    }

    @PostMapping(
            "/{candidateProfileId}/jobs/{normalizedJobId}/analyze"
    )
    public CvTailoringAnalyzeResponse analyze(
            @PathVariable("candidateProfileId")
            String candidateProfileId,

            @PathVariable("normalizedJobId")
            String normalizedJobId,

            Authentication authentication
    ) {
        return cvTailoringAnalysisService
                .analyze(
                        candidateProfileId,
                        normalizedJobId,
                        resolveOwnerUserId(
                                authentication
                        )
                );
    }

    @PostMapping(
            "/{candidateProfileId}/jobs/{normalizedJobId}/preview"
    )
    public CvTailoringPreviewResponse preview(
            @PathVariable("candidateProfileId")
            String candidateProfileId,

            @PathVariable("normalizedJobId")
            String normalizedJobId,

            @RequestBody
            CvTailoringPreviewRequest request,

            Authentication authentication
    ) {
        return cvTailoringPreviewService
                .preview(
                        candidateProfileId,
                        normalizedJobId,
                        resolveOwnerUserId(
                                authentication
                        ),
                        request
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