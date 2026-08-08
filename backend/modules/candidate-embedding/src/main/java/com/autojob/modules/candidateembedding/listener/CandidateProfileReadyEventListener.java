package com.autojob.modules.candidateembedding.listener;

import com.autojob.common.events.CandidateProfileReadyEvent;
import com.autojob.modules.candidateembedding.service.CandidateEmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CandidateProfileReadyEventListener {

    private final CandidateEmbeddingService candidateEmbeddingService;

    @EventListener
    public void onCandidateProfileReady(
            CandidateProfileReadyEvent event
    ) {
        try {
            candidateEmbeddingService.embed(
                    event.getCandidateProfileId(),
                    false
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to handle CandidateProfileReadyEvent "
                            + "candidateProfileId={} "
                            + "rawCvId={} "
                            + "errorType={} "
                            + "message={}",
                    event.getCandidateProfileId(),
                    event.getRawCvId(),
                    exception
                            .getClass()
                            .getSimpleName(),
                    safeMessage(exception)
            );
        }
    }

    private String safeMessage(
            RuntimeException exception
    ) {
        String message =
                exception.getMessage();

        if (message == null
                || message.isBlank()) {
            return exception
                    .getClass()
                    .getSimpleName();
        }

        String normalized =
                message
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        return normalized.length() > 500
                ? normalized.substring(
                0,
                500
        )
                : normalized;
    }
}