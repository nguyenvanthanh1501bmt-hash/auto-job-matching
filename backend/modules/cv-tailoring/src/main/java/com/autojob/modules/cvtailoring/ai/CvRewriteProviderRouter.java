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

    private static final int MAX_TRANSIENT_RETRIES = 1;

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

        for (CvRewriteProvider provider : providers) {

            if (provider == null
                    || !provider.isAvailable()
                    || isCoolingDown(
                    provider.name()
            )) {
                continue;
            }

            Optional<RewriteResponse> result =
                    callProvider(
                            provider,
                            request,
                            usableResponse
                    );

            if (result.isPresent()) {
                return result;
            }
        }

        /*
         * Cả Gemini và Groq đều unavailable/fail.
         *
         * Caller sẽ tiếp tục với rule-based:
         * - GAP_WARNING
         * - EMPHASIZE
         */
        return Optional.empty();
    }

    private Optional<RewriteResponse> callProvider(
            CvRewriteProvider provider,
            RewriteRequest request,
            Predicate<RewriteResponse> usableResponse
    ) {
        int transientRetries = 0;

        while (true) {
            try {
                RewriteResponse response =
                        provider.generate(
                                request
                        );

                if (response != null
                        && usableResponse.test(
                        response
                )) {
                    return Optional.of(
                            response
                    );
                }

                /*
                 * Provider technically answered, but backend
                 * safety/validation rejected its business output.
                 *
                 * Do not retry the same model hoping for luck.
                 * Go directly to the next provider.
                 */
                log.warn(
                        "CV rewrite provider output rejected provider={}",
                        provider.name()
                );

                return Optional.empty();

            } catch (ProviderException exception) {

                if (shouldRetry(
                        exception
                )
                        && transientRetries
                        < MAX_TRANSIENT_RETRIES) {

                    transientRetries++;

                    log.warn(
                            "Retrying CV rewrite provider provider={} "
                                    + "reason={} attempt={}",
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

                return Optional.empty();

            } catch (RuntimeException exception) {

                /*
                 * AI provider tuyệt đối không được làm Analyze chết.
                 *
                 * Không log:
                 * - request body
                 * - CV text
                 * - JD text
                 * - API key
                 */
                log.warn(
                        "CV rewrite provider failed "
                                + "provider={} reason=unexpected",
                        provider.name(),
                        exception
                );

                return Optional.empty();
            }
        }
    }

    private boolean shouldRetry(
            ProviderException exception
    ) {
        return exception.reason()
                == CvRewriteProvider.FailureReason.SERVER_ERROR
                || exception.reason()
                == CvRewriteProvider.FailureReason.NETWORK_ERROR;
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
                    Instant.now(
                            clock
                    ).plus(
                            properties
                                    .getProviderCooldown()
                    )
            );
        }

        /*
         * Timeout:
         * -> no retry
         * -> immediately fall through to next provider.
         *
         * 429:
         * -> cooldown
         * -> immediately fall through.
         *
         * 5xx/network:
         * -> retry exactly once before reaching here.
         */
        log.warn(
                "CV rewrite provider failed "
                        + "provider={} reason={} status={}",
                provider.name(),
                exception.reason(),
                exception.statusCode()
        );
    }

    private boolean shouldCooldown(
            ProviderException exception
    ) {
        return exception.reason()
                == CvRewriteProvider.FailureReason.RATE_LIMIT
                || exception.reason()
                == CvRewriteProvider.FailureReason.AUTHENTICATION;
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
}