package com.autojob.modules.cvtailoring.ai;

import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.ProviderException;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteRequest;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteResponse;
import com.autojob.modules.cvtailoring.config.CvTailoringAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

@Component
public class CvRewriteProviderRouter {

    private static final Logger log =
            LoggerFactory.getLogger(
                    CvRewriteProviderRouter.class
            );

    private static final int MAX_TRANSIENT_RETRIES =
            1;

    private final List<CvRewriteProvider> providers;

    private final CvTailoringAiProperties properties;

    private final Clock clock;

    private final Map<String, Instant> cooldownUntil =
            new ConcurrentHashMap<>();

    public CvRewriteProviderRouter(
            List<CvRewriteProvider> providers,
            CvTailoringAiProperties properties,
            Clock clock
    ) {
        this.providers =
                List.copyOf(
                        Objects.requireNonNull(
                                providers,
                                "providers must not be null"
                        )
                );

        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock must not be null"
                );
    }

    public Optional<RewriteResponse> generate(
            RewriteRequest request,
            Predicate<RewriteResponse> usableResponse
    ) {
        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        Objects.requireNonNull(
                usableResponse,
                "usableResponse must not be null"
        );

        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        RewriteResponse bestEffortResponse =
                null;

        for (CvRewriteProvider provider :
                providers) {

            if (provider == null
                    || !provider.isAvailable()
                    || isCoolingDown(
                    provider.name()
            )) {
                continue;
            }

            ProviderAttempt attempt =
                    callProvider(
                            provider,
                            request,
                            usableResponse
                    );

            if (attempt == null) {
                continue;
            }

            if (attempt.usable()) {
                return Optional.of(
                        attempt.response()
                );
            }

            /*
             * Keep the first structurally valid response as
             * best effort.
             *
             * Example:
             *
             * Provider A:
             * - generated valid coaching,
             * - but every rewrite was rejected.
             *
             * Provider B:
             * - later times out / rate-limits / fails.
             *
             * We still want to preserve safe coaching from A
             * rather than return an entirely empty result.
             */
            if (bestEffortResponse == null) {
                bestEffortResponse =
                        attempt.response();
            }
        }

        if (bestEffortResponse != null) {

            log.info(
                    "Using best-effort CV tailoring provider response "
                            + "after all providers were exhausted"
            );

            return Optional.of(
                    bestEffortResponse
            );
        }

        return Optional.empty();
    }

    private ProviderAttempt callProvider(
            CvRewriteProvider provider,
            RewriteRequest request,
            Predicate<RewriteResponse> usableResponse
    ) {
        int transientRetries =
                0;

        while (true) {

            try {
                RewriteResponse response =
                        provider.generate(
                                request
                        );

                if (response == null) {

                    log.warn(
                            "CV rewrite provider returned null "
                                    + "provider={}",
                            provider.name()
                    );

                    return null;
                }

                boolean usable =
                        usableResponse.test(
                                response
                        );

                if (!usable) {

                    /*
                     * Provider answered, but backend
                     * business validation did not accept
                     * the rewrite output.
                     *
                     * Do not retry the same model hoping
                     * for luck.
                     *
                     * Go directly to the next provider.
                     */
                    log.warn(
                            "CV rewrite provider output rejected "
                                    + "provider={}",
                            provider.name()
                    );
                }

                return new ProviderAttempt(
                        response,
                        usable
                );

            } catch (ProviderException exception) {

                if (shouldRetry(
                        exception
                )
                        && transientRetries
                        < MAX_TRANSIENT_RETRIES) {

                    transientRetries++;

                    log.warn(
                            "Retrying CV rewrite provider "
                                    + "provider={} reason={} attempt={}",
                            provider.name(),
                            exception.reason(),
                            transientRetries + 1
                    );

                    continue;
                }

                handleFailure(
                        provider,
                        exception
                );

                return null;

            } catch (RuntimeException exception) {

                /*
                 * AI provider must never make
                 * Tailoring Analyze fail.
                 *
                 * Never log:
                 * - CV text
                 * - JD text
                 * - request body
                 * - API keys
                 */
                log.warn(
                        "CV rewrite provider failed "
                                + "provider={} reason=unexpected",
                        provider.name(),
                        exception
                );

                return null;
            }
        }
    }

    private boolean shouldRetry(
            ProviderException exception
    ) {
        return exception.reason()
                == CvRewriteProvider
                .FailureReason
                .SERVER_ERROR

                || exception.reason()
                == CvRewriteProvider
                .FailureReason
                .NETWORK_ERROR;
    }

    private void handleFailure(
            CvRewriteProvider provider,
            ProviderException exception
    ) {
        if (shouldCooldown(
                exception
        )) {

            cooldownUntil.put(
                    provider.name(),
                    Instant
                            .now(
                                    clock
                            )
                            .plus(
                                    properties
                                            .getProviderCooldown()
                            )
            );
        }

        log.warn(
                "CV rewrite provider failed "
                        + "provider={} reason={} status={} detail={}",
                provider.name(),
                exception.reason(),
                exception.statusCode(),
                safeDetail(
                        exception.getMessage()
                )
        );
    }

    private boolean shouldCooldown(
            ProviderException exception
    ) {
        return exception.reason()
                == CvRewriteProvider
                .FailureReason
                .RATE_LIMIT

                || exception.reason()
                == CvRewriteProvider
                .FailureReason
                .AUTHENTICATION;
    }

    private boolean isCoolingDown(
            String providerName
    ) {
        Instant until =
                cooldownUntil.get(
                        providerName
                );

        if (until == null) {
            return false;
        }

        Instant now =
                Instant.now(
                        clock
                );

        if (!now.isBefore(
                until
        )) {

            cooldownUntil.remove(
                    providerName,
                    until
            );

            return false;
        }

        return true;
    }

    private String safeDetail(
            String message
    ) {
        if (message == null
                || message.isBlank()) {

            return "n/a";
        }

        String compact =
                message
                        .replace(
                                '\n',
                                ' '
                        )
                        .replace(
                                '\r',
                                ' '
                        )
                        .trim();

        if (compact.length()
                <= 500) {

            return compact;
        }

        return compact.substring(
                0,
                500
        ) + "...";
    }

    private record ProviderAttempt(
            RewriteResponse response,
            boolean usable
    ) {
    }
}