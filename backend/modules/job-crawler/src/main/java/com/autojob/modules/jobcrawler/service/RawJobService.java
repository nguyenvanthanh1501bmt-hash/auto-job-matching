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

    private final RawJobRepository rawJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RawJob upsertSeen(RawJob incoming) {
        return rawJobRepository.findByFingerprint(incoming.getFingerprint())
                .map(existing -> updateExisting(existing, incoming))
                .orElseGet(() -> insertNew(incoming));
    }

    private RawJob insertNew(RawJob incoming) {
        try {
            Instant now = incoming.getCollectedAt() != null
                    ? incoming.getCollectedAt()
                    : Instant.now();

            incoming.setFirstSeenAt(incoming.getFirstSeenAt() != null ? incoming.getFirstSeenAt() : now);
            incoming.setLastSeenAt(incoming.getLastSeenAt() != null ? incoming.getLastSeenAt() : now);
            incoming.setCollectedAt(now);

            RawJob saved = rawJobRepository.save(incoming);

            eventPublisher.publishEvent(new JobRawCollectedEvent(
                    saved.getId(),
                    saved.getSourceCode(),
                    saved.getSourceJobId(),
                    saved.getFingerprint(),
                    saved.getCollectedAt()
            ));

            log.info(
                    "Inserted raw job id={}, source={}, sourceJobId={}, title={}",
                    saved.getId(),
                    saved.getSourceCode(),
                    saved.getSourceJobId(),
                    saved.getTitle()
            );

            return saved;
        } catch (DuplicateKeyException e) {
            return rawJobRepository.findByFingerprint(incoming.getFingerprint())
                    .map(existing -> updateExisting(existing, incoming))
                    .orElseThrow(() -> e);
        }
    }

    private RawJob updateExisting(RawJob existing, RawJob incoming) {
        Instant now = Instant.now();

        existing.setLastSeenAt(incoming.getLastSeenAt() != null ? incoming.getLastSeenAt() : now);
        existing.setCollectedAt(incoming.getCollectedAt() != null ? incoming.getCollectedAt() : now);
        existing.setExpiresAt(incoming.getExpiresAt());

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

        RawJob saved = rawJobRepository.save(existing);

        log.info(
                "Updated raw job seen id={}, source={}, sourceJobId={}, lastSeenAt={}",
                saved.getId(),
                saved.getSourceCode(),
                saved.getSourceJobId(),
                saved.getLastSeenAt()
        );

        return saved;
    }
}