package com.autojob.modules.jobcrawler.service;

import com.autojob.common.events.JobRawCollectedEvent;
import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.repository.RawJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class RawJobService {

    /**
     * Legacy compatibility only.
     *
     * Automatic RawJob expiration is disabled.
     *
     * Older callers trong repo vẫn có thể truyền retentionDays,
     * nhưng RawJobService sẽ bỏ qua hoàn toàn giá trị đó.
     */
    @Deprecated
    public static final int DEFAULT_RAW_RETENTION_DAYS = 30;

    private final RawJobRepository rawJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RawJob upsertSeen(
            RawJob incoming
    ) {
        validateIncoming(incoming);

        return rawJobRepository
                .findByFingerprint(
                        incoming.getFingerprint()
                )
                .map(
                        existing ->
                                updateExisting(
                                        existing,
                                        incoming
                                )
                )
                .orElseGet(
                        () -> insertNew(incoming)
                );
    }

    /**
     * Legacy compatibility overload.
     *
     * rawRetentionDays cố ý bị ignore.
     *
     * Không có bất kỳ expiration nào được tạo từ tham số này.
     */
    @Deprecated
    public RawJob upsertSeen(
            RawJob incoming,
            int rawRetentionDays
    ) {
        return upsertSeen(incoming);
    }

    private RawJob insertNew(
            RawJob incoming
    ) {
        Instant now =
                observationTime(incoming);

        incoming.setFirstSeenAt(now);
        incoming.setLastSeenAt(now);
        incoming.setCollectedAt(now);

        /*
         * Clear metadata expiration cũ.
         *
         * Không có TTL index nên RawJob sẽ được giữ lại.
         */
        incoming.setExpiresAt(null);

        RawJob saved;

        try {
            saved = rawJobRepository.save(
                    incoming
            );
        } catch (DuplicateKeyException exception) {
            return rawJobRepository
                    .findByFingerprint(
                            incoming.getFingerprint()
                    )
                    .map(
                            existing ->
                                    updateExisting(
                                            existing,
                                            incoming
                                    )
                    )
                    .orElseThrow(
                            () -> exception
                    );
        }

        publishRawCollectedEvent(saved);

        log.info(
                "Inserted raw job id={}, "
                        + "source={}, "
                        + "sourceJobId={}, "
                        + "title={}",
                saved.getId(),
                saved.getSourceCode(),
                saved.getSourceJobId(),
                saved.getTitle()
        );

        return saved;
    }

    private RawJob updateExisting(
            RawJob existing,
            RawJob incoming
    ) {
        Instant observedAt =
                observationTime(incoming);

        Instant originalFirstSeenAt =
                existing.getFirstSeenAt();

        copyBusinessFields(
                existing,
                incoming
        );

        existing.setLastSeenAt(
                observedAt
        );

        existing.setCollectedAt(
                observedAt
        );

        existing.setFirstSeenAt(
                originalFirstSeenAt
        );

        /*
         * Nếu document cũ còn expiresAt từ trước,
         * crawler lần kế tiếp sẽ clear nó.
         */
        existing.setExpiresAt(null);

        RawJob saved =
                rawJobRepository.save(
                        existing
                );

        publishRawCollectedEvent(saved);

        log.info(
                "Updated raw job id={}, "
                        + "source={}, "
                        + "sourceJobId={}, "
                        + "lastSeenAt={}",
                saved.getId(),
                saved.getSourceCode(),
                saved.getSourceJobId(),
                saved.getLastSeenAt()
        );

        return saved;
    }

    private void copyBusinessFields(
            RawJob existing,
            RawJob incoming
    ) {
        existing.setSourceUrl(
                incoming.getSourceUrl()
        );

        existing.setListUrl(
                incoming.getListUrl()
        );

        existing.setDetailUrl(
                incoming.getDetailUrl()
        );

        existing.setApplyUrl(
                incoming.getApplyUrl()
        );

        existing.setApplyType(
                incoming.getApplyType()
        );

        existing.setTitle(
                incoming.getTitle()
        );

        existing.setCompanyName(
                incoming.getCompanyName()
        );

        existing.setSalaryText(
                incoming.getSalaryText()
        );

        existing.setLocationText(
                incoming.getLocationText()
        );

        existing.setExperienceText(
                incoming.getExperienceText()
        );

        existing.setSeniorityText(
                incoming.getSeniorityText()
        );

        existing.setJobTypeText(
                incoming.getJobTypeText()
        );

        existing.setDeadlineText(
                incoming.getDeadlineText()
        );

        existing.setPostedText(
                incoming.getPostedText()
        );

        existing.setSkills(
                incoming.getSkills()
        );

        existing.setDescriptionText(
                incoming.getDescriptionText()
        );

        existing.setRequirementsText(
                incoming.getRequirementsText()
        );

        existing.setBenefitsText(
                incoming.getBenefitsText()
        );

        existing.setRawHtml(
                incoming.getRawHtml()
        );

        existing.setRawText(
                incoming.getRawText()
        );

        existing.setRawPayloadPurgedAt(
                null
        );
    }

    private void publishRawCollectedEvent(
            RawJob rawJob
    ) {
        eventPublisher.publishEvent(
                new JobRawCollectedEvent(
                        rawJob.getId(),
                        rawJob.getSourceCode(),
                        rawJob.getSourceJobId(),
                        rawJob.getFingerprint(),
                        rawJob.getCollectedAt()
                )
        );
    }

    private Instant observationTime(
            RawJob incoming
    ) {
        return incoming.getCollectedAt() != null
                ? incoming.getCollectedAt()
                : Instant.now();
    }

    private void validateIncoming(
            RawJob incoming
    ) {
        if (incoming == null) {
            throw new IllegalArgumentException(
                    "incoming raw job must not be null"
            );
        }

        if (incoming.getFingerprint() == null
                || incoming.getFingerprint().isBlank()) {
            throw new IllegalArgumentException(
                    "raw job fingerprint must not be blank"
            );
        }
    }
}