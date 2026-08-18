package com.autojob.modules.matching.api;

import com.autojob.modules.matching.contract.MatchingResponse;
import com.autojob.modules.matching.contract.MatchingRunResult;
import com.autojob.modules.matching.service.HybridMatchingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
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

    private static final String
            DEFAULT_PUBLIC_OWNER_USER_ID =
            "public-local-user";

    private final HybridMatchingService
            hybridMatchingService;

    private final boolean publicApiMode;

    private final String publicOwnerUserId;

    public MatchingController(
            HybridMatchingService hybridMatchingService,

            @Value(
                    "${autojob.auth.public-api-mode:true}"
            )
            boolean publicApiMode,

            @Value(
                    "${autojob.cv.public-owner-user-id:"
                            + DEFAULT_PUBLIC_OWNER_USER_ID
                            + "}"
            )
            String publicOwnerUserId
    ) {
        this.hybridMatchingService =
                hybridMatchingService;

        this.publicApiMode =
                publicApiMode;

        this.publicOwnerUserId =
                normalizePublicOwnerUserId(
                        publicOwnerUserId
                );
    }

    /**
     * Chạy hybrid matching.
     *
     * POST
     * /api/matching/candidates/{candidateProfileId}
     *
     * Mặc định:
     * force=false
     *
     * Nếu cùng candidate embedding + ranking version
     * đã có result thì reuse.
     */
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

    /**
     * Đọc result của:
     *
     * current READY candidate embedding
     * +
     * current ranking version.
     *
     * GET
     * /api/matching/candidates/{candidateProfileId}
     */
    @GetMapping("/{candidateProfileId}")
    public MatchingResponse getCurrent(
            @PathVariable("candidateProfileId")
            String candidateProfileId,

            Authentication authentication
    ) {
        MatchingRunResult result =
                hybridMatchingService
                        .getCurrent(
                                candidateProfileId,
                                resolveOwnerUserId(
                                        authentication
                                )
                        );

        return MatchingResponse.from(
                result
        );
    }

    /**
     * Phải dùng cùng ownership convention
     * với CvController.
     *
     * publicApiMode=true:
     * luôn dùng public owner cố định.
     *
     * publicApiMode=false:
     * dùng authentication.getName().
     */
    private String resolveOwnerUserId(
            Authentication authentication
    ) {
        if (publicApiMode) {
            return publicOwnerUserId;
        }

        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication
                instanceof AnonymousAuthenticationToken)) {

            return authentication.getName();
        }

        /*
         * Service sẽ convert null này
         * thành AUTHENTICATION_REQUIRED.
         */
        return null;
    }

    private String normalizePublicOwnerUserId(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return DEFAULT_PUBLIC_OWNER_USER_ID;
        }

        return value.trim();
    }
}