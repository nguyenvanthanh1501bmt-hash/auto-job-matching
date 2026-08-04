package com.autojob.modules.cv.api;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.domain.CvProcessingStatus;
import com.autojob.modules.cv.domain.RawCv;
import com.autojob.modules.cv.service.CvParsingService;
import com.autojob.modules.cv.service.CvUploadService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

@RestController
@RequestMapping("/api/cvs")
public class CvController {

    static final String DEFAULT_PUBLIC_OWNER_USER_ID =
            "public-local-user";

    private final CvUploadService cvUploadService;
    private final CvParsingService cvParsingService;
    private final boolean publicApiMode;
    private final String publicOwnerUserId;

    public CvController(
            CvUploadService cvUploadService,
            CvParsingService cvParsingService,
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
        this.cvUploadService = cvUploadService;
        this.cvParsingService = cvParsingService;
        this.publicApiMode = publicApiMode;
        this.publicOwnerUserId =
                normalizePublicOwnerUserId(
                        publicOwnerUserId
                );
    }

    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @ResponseStatus(HttpStatus.CREATED)
    public RawCvResponse upload(
            @RequestPart("file") MultipartFile file,
            Authentication authentication,
            HttpServletRequest request
    ) {
        RawCv rawCv = cvUploadService.upload(
                file,
                resolveUserId(authentication),
                request.getRemoteAddr()
        );

        return RawCvResponse.from(rawCv);
    }

    @GetMapping("/{rawCvId}")
    public RawCvResponse getById(
            @PathVariable("rawCvId") String rawCvId,
            Authentication authentication
    ) {
        RawCv rawCv = cvUploadService.getById(
                rawCvId,
                resolveUserId(authentication)
        );

        return RawCvResponse.from(rawCv);
    }

    @PostMapping("/{rawCvId}/parse")
    public CandidateProfileResponse parse(
            @PathVariable("rawCvId") String rawCvId,
            Authentication authentication
    ) {
        CandidateProfile profile =
                cvParsingService.parse(
                        rawCvId,
                        resolveUserId(authentication)
                );

        return CandidateProfileResponse.from(profile);
    }

    @GetMapping("/{rawCvId}/profile")
    public CandidateProfileResponse getProfile(
            @PathVariable("rawCvId") String rawCvId,
            Authentication authentication
    ) {
        CandidateProfile profile =
                cvParsingService.getProfile(
                        rawCvId,
                        resolveUserId(authentication)
                );

        return CandidateProfileResponse.from(profile);
    }

    private String resolveUserId(
            Authentication authentication
    ) {
        /*
         * Public mode phải nhất quán:
         * request có JWT hay không vẫn dùng cùng public owner.
         *
         * Khi public mode tắt, principal name trở thành ownerUserId.
         */
        if (publicApiMode) {
            return publicOwnerUserId;
        }

        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication
                instanceof AnonymousAuthenticationToken)) {
            return authentication.getName();
        }

        return null;
    }

    private String normalizePublicOwnerUserId(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PUBLIC_OWNER_USER_ID;
        }

        return value.trim();
    }

    public record RawCvResponse(
            String id,
            String ownerUserId,
            String originalFilename,
            String extension,
            String contentType,
            long sizeBytes,
            String sha256,
            CvProcessingStatus status,
            Instant uploadedAt
    ) {

        static RawCvResponse from(RawCv rawCv) {
            return new RawCvResponse(
                    rawCv.getId(),
                    rawCv.getOwnerUserId(),
                    rawCv.getOriginalFilename(),
                    rawCv.getExtension(),
                    rawCv.getContentType(),
                    rawCv.getSizeBytes(),
                    rawCv.getSha256(),
                    rawCv.getStatus(),
                    rawCv.getUploadedAt()
            );
        }
    }
}