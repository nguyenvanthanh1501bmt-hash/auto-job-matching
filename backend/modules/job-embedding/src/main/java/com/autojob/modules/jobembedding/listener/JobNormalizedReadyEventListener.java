package com.autojob.modules.jobembedding.listener;

import com.autojob.common.events.JobNormalizedReadyEvent;
import com.autojob.modules.jobembedding.domain.JobEmbedding;
import com.autojob.modules.jobembedding.service.JobEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobNormalizedReadyEventListener {

    private final JobEmbeddingService jobEmbeddingService;

    @EventListener
    public void onJobNormalizedReady(
            JobNormalizedReadyEvent event
    ) {
        String normalizedJobId =
                event.getNormalizedJobId();

        log.info(
                "Received JobNormalizedReadyEvent "
                        + "normalizedJobId={} rawJobId={} "
                        + "sourceCode={} normalizationVersion={}",
                normalizedJobId,
                event.getRawJobId(),
                event.getSourceCode(),
                event.getNormalizationVersion()
        );

        try {
            JobEmbedding result =
                    jobEmbeddingService.embed(
                            normalizedJobId,
                            false
                    );

            log.info(
                    "Handled JobNormalizedReadyEvent "
                            + "normalizedJobId={} embeddingVersion={} "
                            + "textHash={} qdrantPointId={} status={}",
                    normalizedJobId,
                    result.getEmbeddingVersion(),
                    result.getTextHash(),
                    result.getQdrantPointId(),
                    result.getStatus()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to handle JobNormalizedReadyEvent "
                            + "normalizedJobId={} sourceCode={} "
                            + "errorType={} message={}",
                    normalizedJobId,
                    event.getSourceCode(),
                    exception.getClass().getSimpleName(),
                    safeMessage(exception)
            );

            /*
             * Không propagate lỗi embedding về normalizer.
             */
        }
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        String normalized = message
                .replaceAll("\\s+", " ")
                .trim();

        if (normalized.length() > 500) {
            return normalized.substring(0, 500);
        }

        return normalized;
    }
}