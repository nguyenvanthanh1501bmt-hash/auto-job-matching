package com.autojob.modules.jobnormalizer.service;

import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.repository.RawJobRepository;
import com.autojob.modules.jobnormalizer.domain.NormalizationAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobRenormalizationBatchService {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 100;
    public static final int MAX_SIZE = 500;

    private final RawJobRepository rawJobRepository;
    private final JobNormalizationService jobNormalizationService;

    public RenormalizationBatchResponse renormalize(
            RenormalizationBatchRequest request
    ) {
        RenormalizationBatchRequest normalizedRequest = normalizeRequest(
                request
        );

        String normalizationVersion =
                jobNormalizationService.getNormalizationVersion();

        PageRequest pageRequest = PageRequest.of(
                normalizedRequest.page(),
                normalizedRequest.size(),
                Sort.by(Sort.Direction.ASC, "id")
        );

        Page<RawJob> rawJobs = normalizedRequest.sourceCode() == null
                ? rawJobRepository.findAll(pageRequest)
                : rawJobRepository.findBySourceCode(
                normalizedRequest.sourceCode(),
                pageRequest
        );

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int failed = 0;
        int rawPayloadPurged = 0;
        int purgeFailed = 0;
        List<RenormalizationFailure> failures = new ArrayList<>();

        for (RawJob rawJob : rawJobs.getContent()) {
            try {
                NormalizationRunResult result =
                        jobNormalizationService.normalizeByRawJobId(
                                rawJob.getId(),
                                normalizedRequest.force()
                        );

                NormalizationAction action = result.execution().action();

                switch (action) {
                    case CREATED -> created++;
                    case UPDATED -> updated++;
                    case UNCHANGED -> unchanged++;
                }

                if (result.rawPayloadPurged()) {
                    rawPayloadPurged++;
                }

                if (result.purgeFailed()) {
                    purgeFailed++;
                    log.error(
                            "Batch normalization purge failed rawJobId={}, error={}",
                            rawJob.getId(),
                            result.purgeError()
                    );
                }
            } catch (RuntimeException exception) {
                failed++;
                failures.add(
                        new RenormalizationFailure(
                                rawJob.getId(),
                                exception.getClass().getSimpleName(),
                                exception.getMessage()
                        )
                );

                log.error(
                        "Batch normalization item failed rawJobId={}, error={}",
                        rawJob.getId(),
                        exception.getMessage(),
                        exception
                );
            }
        }

        boolean hasNext = rawJobs.hasNext();
        Integer nextPage = hasNext
                ? rawJobs.getNumber() + 1
                : null;

        return new RenormalizationBatchResponse(
                normalizedRequest.sourceCode(),
                normalizationVersion,
                normalizedRequest.force(),
                rawJobs.getNumber(),
                rawJobs.getSize(),
                rawJobs.getNumberOfElements(),
                rawJobs.getTotalElements(),
                rawJobs.getTotalPages(),
                hasNext,
                nextPage,
                created,
                updated,
                unchanged,
                failed,
                rawPayloadPurged,
                purgeFailed,
                List.copyOf(failures)
        );
    }

    private RenormalizationBatchRequest normalizeRequest(
            RenormalizationBatchRequest request
    ) {
        RenormalizationBatchRequest safeRequest = request == null
                ? new RenormalizationBatchRequest(
                null,
                null,
                null,
                null
        )
                : request;

        String sourceCode = normalizeSourceCode(
                safeRequest.sourceCode()
        );

        int page = safeRequest.page() == null
                ? DEFAULT_PAGE
                : safeRequest.page();

        int size = safeRequest.size() == null
                ? DEFAULT_SIZE
                : safeRequest.size();

        boolean force = Boolean.TRUE.equals(safeRequest.force());

        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must be greater than or equal to 0"
            );
        }

        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and " + MAX_SIZE
            );
        }

        return new RenormalizationBatchRequest(
                sourceCode,
                page,
                size,
                force
        );
    }

    private String normalizeSourceCode(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return null;
        }

        return sourceCode.trim().toUpperCase(Locale.ROOT);
    }

    public record RenormalizationBatchRequest(
            String sourceCode,
            Integer page,
            Integer size,
            Boolean force
    ) {
    }

    public record RenormalizationFailure(
            String rawJobId,
            String errorType,
            String message
    ) {
    }

    public record RenormalizationBatchResponse(
            String sourceCode,
            String normalizationVersion,
            boolean force,
            int page,
            int size,
            int processed,
            long totalRawJobs,
            int totalPages,
            boolean hasNext,
            Integer nextPage,
            int created,
            int updated,
            int unchanged,
            int failed,
            int rawPayloadPurged,
            int purgeFailed,
            List<RenormalizationFailure> failures
    ) {
    }
}