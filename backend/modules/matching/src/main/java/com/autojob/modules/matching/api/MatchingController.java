package com.autojob.modules.matching.api;

import com.autojob.modules.matching.contract.MatchingResponse;
import com.autojob.modules.matching.contract.MatchingRunResult;
import com.autojob.modules.matching.service.HybridMatchingService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matching/candidates")
public class MatchingController {

    private final HybridMatchingService
            hybridMatchingService;

    public MatchingController(
            HybridMatchingService hybridMatchingService
    ) {
        this.hybridMatchingService =
                hybridMatchingService;
    }

    @PostMapping("/{candidateProfileId}")
    public MatchingResponse run(
            @PathVariable("candidateProfileId")
            String candidateProfileId,

            @RequestParam(
                    name = "force",
                    defaultValue = "false"
            )
            boolean force,

            Authentication authentication
    ) {
        MatchingRunResult result =
                hybridMatchingService.run(
                        candidateProfileId,
                        resolveOwnerUserId(
                                authentication
                        ),
                        force
                );

        return MatchingResponse.from(
                result
        );
    }

    @GetMapping("/{candidateProfileId}")
    public MatchingResponse getCurrent(
            @PathVariable("candidateProfileId")
            String candidateProfileId,

            Authentication authentication
    ) {
        MatchingRunResult result =
                hybridMatchingService.getCurrent(
                        candidateProfileId,
                        resolveOwnerUserId(
                                authentication
                        )
                );

        return MatchingResponse.from(
                result
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