package com.autojob.modules.jobnormalizer.api;

import com.autojob.common.dtos.ApplyType;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import com.autojob.modules.jobnormalizer.exception.NormalizedJobNotFoundException;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/normalized-jobs")
@RequiredArgsConstructor
public class NormalizedJobController {

    private static final int MAX_PAGE_SIZE = 100;

    private final NormalizedJobRepository normalizedJobRepository;

    @GetMapping
    public PageResponse<NormalizedJobSummaryResponse> list(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sourceCode", required = false)
            String sourceCode,
            @RequestParam(name = "normalizationVersion", required = false)
            String normalizationVersion
    ) {
        validatePagination(page, size);

        String normalizedSourceCode = normalizeSourceCode(sourceCode);
        String normalizedVersion = normalizeOptionalText(
                normalizationVersion
        );

        PageRequest pageRequest = PageRequest.of(
                page,
                size,
                Sort.by(
                        Sort.Order.desc("normalizedAt"),
                        Sort.Order.desc("id")
                )
        );

        Page<NormalizedJob> result = findPage(
                normalizedSourceCode,
                normalizedVersion,
                pageRequest
        );

        List<NormalizedJobSummaryResponse> content = result
                .getContent()
                .stream()
                .map(NormalizedJobSummaryResponse::from)
                .toList();

        return new PageResponse<>(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.isFirst(),
                result.isLast()
        );
    }

    @GetMapping("/{id}")
    public NormalizedJobDetailResponse getById(
            @PathVariable("id") String id
    ) {
        String normalizedId = requireText(
                id,
                "Normalized job id must not be blank"
        );

        NormalizedJob normalizedJob = normalizedJobRepository
                .findById(normalizedId)
                .orElseThrow(
                        () -> new NormalizedJobNotFoundException(
                                normalizedId
                        )
                );

        return NormalizedJobDetailResponse.from(normalizedJob);
    }

    private Page<NormalizedJob> findPage(
            String sourceCode,
            String normalizationVersion,
            PageRequest pageRequest
    ) {
        if (sourceCode != null && normalizationVersion != null) {
            return normalizedJobRepository
                    .findBySourceCodeAndNormalizationVersion(
                            sourceCode,
                            normalizationVersion,
                            pageRequest
                    );
        }

        if (sourceCode != null) {
            return normalizedJobRepository.findBySourceCode(
                    sourceCode,
                    pageRequest
            );
        }

        if (normalizationVersion != null) {
            return normalizedJobRepository
                    .findByNormalizationVersion(
                            normalizationVersion,
                            pageRequest
                    );
        }

        return normalizedJobRepository.findAll(pageRequest);
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must be greater than or equal to 0"
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and " + MAX_PAGE_SIZE
            );
        }
    }

    private String normalizeSourceCode(String sourceCode) {
        String normalized = normalizeOptionalText(sourceCode);

        if (normalized == null) {
            return null;
        }

        return normalized.toUpperCase(Locale.ROOT);
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String requireText(
            String value,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }

        return value.trim();
    }

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last
    ) {
    }

    public record NormalizedJobSummaryResponse(
            String id,
            String rawJobId,
            String sourceCode,
            String sourceJobId,
            String title,
            String companyName,
            List<String> skills,
            List<String> locations,
            String salaryText,
            Long salaryMin,
            Long salaryMax,
            String currency,
            Double experienceMin,
            Double experienceMax,
            SeniorityLevel seniority,
            NormalizedJobType jobType,
            String normalizationVersion,
            Instant postedAt,
            Instant deadlineAt,
            Instant normalizedAt
    ) {
        static NormalizedJobSummaryResponse from(
                NormalizedJob job
        ) {
            return new NormalizedJobSummaryResponse(
                    job.getId(),
                    job.getRawJobId(),
                    job.getSourceCode(),
                    job.getSourceJobId(),
                    job.getTitle(),
                    job.getCompanyName(),
                    job.getSkills(),
                    job.getLocations(),
                    job.getSalaryText(),
                    job.getSalaryMin(),
                    job.getSalaryMax(),
                    job.getCurrency(),
                    job.getExperienceMin(),
                    job.getExperienceMax(),
                    job.getSeniority(),
                    job.getJobType(),
                    job.getNormalizationVersion(),
                    job.getPostedAt(),
                    job.getDeadlineAt(),
                    job.getNormalizedAt()
            );
        }
    }

    public record NormalizedJobDetailResponse(
            String id,
            String rawJobId,
            String sourceCode,
            String sourceJobId,
            String sourceFingerprint,
            String rawContentHash,
            String title,
            String companyName,
            List<String> skills,
            List<String> locations,
            String locationText,
            String salaryText,
            Long salaryMin,
            Long salaryMax,
            String currency,
            Double experienceMin,
            Double experienceMax,
            SeniorityLevel seniority,
            NormalizedJobType jobType,
            String descriptionText,
            String requirementsText,
            String benefitsText,
            String detailUrl,
            String applyUrl,
            ApplyType applyType,
            String normalizationVersion,
            Instant postedAt,
            Instant deadlineAt,
            Instant normalizedAt
    ) {
        public static NormalizedJobDetailResponse from(
                NormalizedJob job
        ) {
            return new NormalizedJobDetailResponse(
                    job.getId(),
                    job.getRawJobId(),
                    job.getSourceCode(),
                    job.getSourceJobId(),
                    job.getSourceFingerprint(),
                    job.getRawContentHash(),
                    job.getTitle(),
                    job.getCompanyName(),
                    job.getSkills(),
                    job.getLocations(),
                    job.getLocationText(),
                    job.getSalaryText(),
                    job.getSalaryMin(),
                    job.getSalaryMax(),
                    job.getCurrency(),
                    job.getExperienceMin(),
                    job.getExperienceMax(),
                    job.getSeniority(),
                    job.getJobType(),
                    job.getDescriptionText(),
                    job.getRequirementsText(),
                    job.getBenefitsText(),
                    job.getDetailUrl(),
                    job.getApplyUrl(),
                    job.getApplyType(),
                    job.getNormalizationVersion(),
                    job.getPostedAt(),
                    job.getDeadlineAt(),
                    job.getNormalizedAt()
            );
        }
    }
}