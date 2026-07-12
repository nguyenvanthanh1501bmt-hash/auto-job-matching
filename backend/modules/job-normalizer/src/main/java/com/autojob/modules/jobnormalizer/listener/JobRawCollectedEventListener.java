package com.autojob.modules.jobnormalizer.listener;

import com.autojob.common.events.JobRawCollectedEvent;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.service.JobNormalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobRawCollectedEventListener {

    private final JobNormalizationService jobNormalizationService;

    @EventListener
    public void onJobRawCollected(JobRawCollectedEvent event) {
        String rawJobId = event.getRawJobId();

        log.info(
                "Received JobRawCollectedEvent rawJobId={}, "
                        + "sourceCode={}, sourceJobId={}, fingerprint={}",
                rawJobId,
                event.getSourceCode(),
                event.getSourceJobId(),
                event.getFingerprint()
        );

        try {
            NormalizedJob normalizedJob =
                    jobNormalizationService.normalizeByRawJobId(rawJobId);

            log.info(
                    "Handled JobRawCollectedEvent rawJobId={}, "
                            + "normalizedJobId={}, normalizationVersion={}, "
                            + "status=SUCCESS",
                    rawJobId,
                    normalizedJob.getId(),
                    normalizedJob.getNormalizationVersion()
            );
        } catch (RuntimeException exception) {
            /*
             * Không swallow exception.
             *
             * ApplicationEventPublisher đang chạy synchronous,
             * nên lỗi normalize sẽ được propagate về luồng crawler.
             */
            log.error(
                    "Failed to handle JobRawCollectedEvent "
                            + "rawJobId={}, sourceCode={}, status=FAILED",
                    rawJobId,
                    event.getSourceCode(),
                    exception
            );

            throw exception;
        }
    }
}