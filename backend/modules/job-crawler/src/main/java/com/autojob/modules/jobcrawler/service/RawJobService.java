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
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RawJobService {

    public static final int DEFAULT_RAW_RETENTION_DAYS = 30;

    private final RawJobRepository rawJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RawJob upsertSeen(RawJob incoming) {
        return upsertSeen(incoming, DEFAULT_RAW_RETENTION_DAYS);
    }

    public RawJob upsertSeen(
            RawJob incoming,
            int rawRetentionDays
    ) {
        validateIncoming(incoming, rawRetentionDays);

        return rawJobRepository.findByFingerprint(
                        incoming.getFingerprint()
                )
                .map(existing -> updateExisting(existing, incoming))
                .orElseGet(
                        () -> insertNew(incoming, rawRetentionDays)
                );
    }

    private RawJob insertNew(
            RawJob incoming,
            int rawRetentionDays
    ) {
        Instant now = observationTime(incoming);

        incoming.setFirstSeenAt(now);
        incoming.setLastSeenAt(now);
        incoming.setCollectedAt(now);
        incoming.setExpiresAt(
                now.plus(rawRetentionDays, ChronoUnit.DAYS)
        );

        RawJob saved;

        try {
            saved = rawJobRepository.save(incoming);
        } catch (DuplicateKeyException exception) {
            return rawJobRepository
                    .findByFingerprint(incoming.getFingerprint())
                    .map(existing -> updateExisting(existing, incoming))
                    .orElseThrow(() -> exception);
        }

        publishRawCollectedEvent(saved);

        log.info(
                "Inserted raw job id={}, source={}, sourceJobId={}, title={}, expiresAt={}",
                saved.getId(),
                saved.getSourceCode(),
                saved.getSourceJobId(),
                saved.getTitle(),
                saved.getExpiresAt()
        );

        return saved;
    }

    private RawJob updateExisting(
            RawJob existing,
            RawJob incoming
    ) {
        Instant observedAt = observationTime(incoming);
        Instant originalFirstSeenAt = existing.getFirstSeenAt();
        Instant originalExpiresAt = existing.getExpiresAt();

        copyBusinessFields(existing, incoming);

        existing.setLastSeenAt(observedAt);
        existing.setCollectedAt(observedAt);

        existing.setFirstSeenAt(originalFirstSeenAt);
        existing.setExpiresAt(originalExpiresAt);

        RawJob saved = rawJobRepository.save(existing);

        publishRawCollectedEvent(saved);

        log.info(
                "Updated raw job id={}, source={}, sourceJobId={}, lastSeenAt={}, expiresAt={}",
                saved.getId(),
                saved.getSourceCode(),
                saved.getSourceJobId(),
                saved.getLastSeenAt(),
                saved.getExpiresAt()
        );

        return saved;
    }

    private void copyBusinessFields(
            RawJob existing,
            RawJob incoming
    ) {
        existing.setSourceUrl(incoming.getSourceUrl());
        existing.setListUrl(incoming.getListUrl());
        existing.setDetailUrl(incoming.getDetailUrl());
        existing.setApplyUrl(incoming.getApplyUrl());
        existing.setApplyType(incoming.getApplyType());

        existing.setTitle(incoming.getTitle());
        existing.setCompanyName(incoming.getCompanyName());
        existing.setSalaryText(incoming.getSalaryText());
        existing.setLocationText(incoming.getLocationText());
        existing.setExperienceText(incoming.getExperienceText());
        existing.setSeniorityText(incoming.getSeniorityText());
        existing.setJobTypeText(incoming.getJobTypeText());
        existing.setDeadlineText(incoming.getDeadlineText());
        existing.setPostedText(incoming.getPostedText());

        existing.setSkills(incoming.getSkills());
        existing.setDescriptionText(incoming.getDescriptionText());
        existing.setRequirementsText(incoming.getRequirementsText());
        existing.setBenefitsText(incoming.getBenefitsText());

        existing.setRawHtml(incoming.getRawHtml());
        existing.setRawText(incoming.getRawText());
        existing.setRawPayloadPurgedAt(null);
    }

    private void publishRawCollectedEvent(RawJob rawJob) {
        eventPublisher.publishEvent(new JobRawCollectedEvent(
                rawJob.getId(),
                rawJob.getSourceCode(),
                rawJob.getSourceJobId(),
                rawJob.getFingerprint(),
                rawJob.getCollectedAt()
        ));
    }

    private Instant observationTime(RawJob incoming) {
        return incoming.getCollectedAt() != null
                ? incoming.getCollectedAt()
                : Instant.now();
    }

    private void validateIncoming(
            RawJob incoming,
            int rawRetentionDays
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

        if (rawRetentionDays < 1) {
            throw new IllegalArgumentException(
                    "rawRetentionDays must be greater than or equal to 1"
            );
        }
    }
}