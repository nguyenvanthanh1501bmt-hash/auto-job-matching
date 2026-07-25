package com.autojob.modules.cv.api;

import com.autojob.modules.cv.domain.CvProcessingStatus;
import com.autojob.modules.cv.domain.RawCv;
import com.autojob.modules.cv.service.CvUploadService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
@RequiredArgsConstructor
public class CvController {

    private final CvUploadService cvUploadService;

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
            @PathVariable String rawCvId
    ) {
        return RawCvResponse.from(
                cvUploadService.getById(rawCvId)
        );
    }

    private String resolveUserId(
            Authentication authentication
    ) {
        if (authentication != null
                && authentication.isAuthenticated()) {
            return authentication.getName();
        }

        return null;
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