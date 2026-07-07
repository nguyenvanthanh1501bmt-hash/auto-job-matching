package com.autojob.modules.jobcrawler.service;

import com.autojob.common.events.JobRawCollectedEvent;
import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.domain.RawJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RawJobService {

    private final RawJobRepository rawJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    public RawJob saveIfNew(RawJob rawJob) {
        return rawJobRepository.findByFingerprint(rawJob.getFingerprint())
                .orElseGet(() -> {
                    RawJob saved = rawJobRepository.save(rawJob);

                    eventPublisher.publishEvent(new JobRawCollectedEvent(
                            saved.getId(),
                            saved.getSourceCode(),
                            saved.getFingerprint(),
                            saved.getCollectedAt()
                    ));

                    log.info("Saved raw job id={}, title={}, source={}",
                            saved.getId(), saved.getTitle(), saved.getSourceCode());

                    return saved;
                });
    }
}