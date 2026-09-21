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

    private static final RoutingPolicy STANDARD_POLICY =
            new RoutingPolicy(
                    "standard",
                    MAX_TRANSIENT_RETRIES,
                    true
            );

    /**
     * Interactive coaching is a user-blocking action.
     *
     * A single provider may already wait until the configured HTTP request
     * timeout. Retrying the same provider before trying the fallback can make
     * one button click take longer than the frontend request timeout.
     *
     * Therefore interactive routing:
     * - never retries the same provider,
     * - still falls back to the next provider,
     * - still returns the first structurally valid rejected response as
     *   best-effort so the caller can distinguish "AI answered but safety
     *   rejected it" (422) from "no provider answered" (503).
     */
    private static final RoutingPolicy INTERACTIVE_POLICY =
            new RoutingPolicy(
                    "interactive",
                    0,
                    true
            );

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

    /**
     * Normal CV analysis routing.
     *
     * Existing behavior is preserved: transient server/network failures may be
     * retried once before falling back to another provider.
     */
    public Optional<RewriteResponse> generate(
            RewriteRequest request,
            Predicate<RewriteResponse> usableResponse
    ) {
        return generate(
                request,
                usableResponse,
                STANDARD_POLICY
        );
    }

    /**
     * Fast-fail routing for an interactive coaching rewrite.
     *
     * This intentionally avoids same-provider transient retries because the
     * user is waiting on a single UI action. The next configured provider is
     * still attempted as a fallback.
     */
    public Optional<RewriteResponse> generateInteractive(
            RewriteRequest request,
            Predicate<RewriteResponse> usableResponse
    ) {
        return generate(
                request,
                usableResponse,
                INTERACTIVE_POLICY
        );
    }

    private Optional<RewriteResponse> generate(
            RewriteRequest request,
            Predicate<RewriteResponse> usableResponse,
            RoutingPolicy policy
    ) {
        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        Objects.requireNonNull(
                usableResponse,
                "usableResponse must not be null"
        );

        Objects.requireNonNull(
                policy,
                "policy must not be null"
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
                            usableResponse,
                            policy
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
             * Keep the first structurally valid response as best effort.
             *
             * This is important for both flows:
             *
             * - normal Analyze can still preserve safe coaching if all
             *   rewrites were rejected;
             * - interactive coaching can distinguish a safety rejection (422)
             *   from complete provider unavailability (503).
             */
            if (policy.allowBestEffort()
                    && bestEffortResponse == null) {
                bestEffortResponse =
                        attempt.response();
            }
        }

        if (bestEffortResponse != null) {

            log.info(
                    "Using best-effort CV tailoring provider response "
                            + "after all providers were exhausted mode={}",
                    policy.mode()
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
            Predicate<RewriteResponse> usableResponse,
            RoutingPolicy policy
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
                                    + "provider={} mode={}",
                            provider.name(),
                            policy.mode()
                    );

                    return null;
                }

                boolean usable =
                        usableResponse.test(
                                response
                        );

                if (!usable) {

                    /*
                     * Provider answered, but backend business validation did
                     * not accept the rewrite output.
                     *
                     * Do not retry the same model hoping for luck. Go directly
                     * to the next provider.
                     */
                    log.warn(
                            "CV rewrite provider output rejected "
                                    + "provider={} mode={}",
                            provider.name(),
                            policy.mode()
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
                        < policy.maxTransientRetries()) {

                    transientRetries++;

                    log.warn(
                            "Retrying CV rewrite provider "
                                    + "provider={} reason={} attempt={} mode={}",
                            provider.name(),
                            exception.reason(),
                            transientRetries + 1,
                            policy.mode()
                    );

                    continue;
                }

                handleFailure(
                        provider,
                        exception,
                        policy.mode()
                );

                return null;

            } catch (RuntimeException exception) {

                /*
                 * A provider implementation failure must never escape the
                 * routing boundary and break CV analysis/workspace state.
                 *
                 * Never log:
                 * - CV text
                 * - JD text
                 * - request body
                 * - API keys
                 */
                log.warn(
                        "CV rewrite provider failed "
                                + "provider={} reason=unexpected mode={}",
                        provider.name(),
                        policy.mode(),
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
            ProviderException exception,
            String mode
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
                        + "provider={} reason={} status={} detail={} mode={}",
                provider.name(),
                exception.reason(),
                exception.statusCode(),
                safeDetail(
                        exception.getMessage()
                ),
                mode
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

    private record RoutingPolicy(
            String mode,
            int maxTransientRetries,
            boolean allowBestEffort
    ) {
    }

    private record ProviderAttempt(
            RewriteResponse response,
            boolean usable
    ) {
    }
}
