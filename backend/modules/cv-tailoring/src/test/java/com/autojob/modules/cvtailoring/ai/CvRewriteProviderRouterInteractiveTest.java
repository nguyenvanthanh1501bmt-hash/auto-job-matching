package com.autojob.modules.cvtailoring.ai;

import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.FailureReason;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.JobContext;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.ProviderException;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteRequest;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteResponse;
import com.autojob.modules.cvtailoring.config.CvTailoringAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class CvRewriteProviderRouterInteractiveTest {

    private CvTailoringAiProperties properties;
    private RewriteRequest request;

    @BeforeEach
    void setUp() {
        properties = new CvTailoringAiProperties();
        properties.setEnabled(true);
        properties.setProviderCooldown(
                Duration.ofMinutes(2)
        );

        request = new RewriteRequest(
                new JobContext(
                        "Backend Developer",
                        List.of("Java"),
                        "",
                        ""
                ),
                List.of(),
                List.of()
        );
    }

    @Test
    void interactiveRoutingDoesNotRetryTransientFailureBeforeFallback() {
        RewriteResponse fallback = response();

        FakeProvider primary = new FakeProvider(
                "primary",
                new ProviderException(
                        FailureReason.SERVER_ERROR,
                        503,
                        "temporary"
                ),
                response()
        );

        FakeProvider secondary = new FakeProvider(
                "secondary",
                fallback
        );

        CvRewriteProviderRouter router = router(
                primary,
                secondary
        );

        Optional<RewriteResponse> result =
                router.generateInteractive(
                        request,
                        ignored -> true
                );

        assertThat(result).contains(fallback);
        assertThat(primary.calls()).isEqualTo(1);
        assertThat(secondary.calls()).isEqualTo(1);
    }

    @Test
    void interactiveRoutingReturnsRejectedBestEffortWhenFallbackFails() {
        RewriteResponse rejected = response();

        FakeProvider primary = new FakeProvider(
                "primary",
                rejected
        );

        FakeProvider secondary = new FakeProvider(
                "secondary",
                new ProviderException(
                        FailureReason.SERVER_ERROR,
                        503,
                        "temporary"
                )
        );

        CvRewriteProviderRouter router = router(
                primary,
                secondary
        );

        Optional<RewriteResponse> result =
                router.generateInteractive(
                        request,
                        ignored -> false
                );

        assertThat(result).contains(rejected);
        assertThat(primary.calls()).isEqualTo(1);
        assertThat(secondary.calls()).isEqualTo(1);
    }

    private CvRewriteProviderRouter router(
            CvRewriteProvider... providers
    ) {
        return new CvRewriteProviderRouter(
                List.of(providers),
                properties,
                Clock.fixed(
                        Instant.parse(
                                "2026-09-21T00:00:00Z"
                        ),
                        ZoneOffset.UTC
                )
        );
    }

    private RewriteResponse response() {
        return new RewriteResponse(
                List.of()
        );
    }

    private static final class FakeProvider
            implements CvRewriteProvider {

        private final String name;
        private final Queue<Object> outcomes =
                new ArrayDeque<>();
        private int calls;

        private FakeProvider(
                String name,
                Object... outcomes
        ) {
            this.name = name;
            this.outcomes.addAll(
                    List.of(outcomes)
            );
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public RewriteResponse generate(
                RewriteRequest request
        ) {
            calls++;

            Object outcome = outcomes.poll();

            if (outcome == null) {
                throw new AssertionError(
                        "Unexpected provider call: "
                                + name
                );
            }

            if (outcome
                    instanceof ProviderException exception) {
                throw exception;
            }

            return (RewriteResponse) outcome;
        }

        private int calls() {
            return calls;
        }
    }
}
