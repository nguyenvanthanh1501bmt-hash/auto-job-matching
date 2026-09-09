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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class CvRewriteProviderRouterTest {

    private CvTailoringAiProperties properties;
    private MutableClock clock;
    private RewriteRequest request;

    @BeforeEach
    void setUp() {
        properties =
                new CvTailoringAiProperties();

        properties.setEnabled(
                true
        );

        properties.setProviderCooldown(
                Duration.ofMinutes(2)
        );

        clock =
                new MutableClock(
                        Instant.parse(
                                "2026-09-09T00:00:00Z"
                        ),
                        ZoneOffset.UTC
                );

        request =
                new RewriteRequest(
                        new JobContext(
                                "Backend Developer",
                                List.of(
                                        "Java"
                                ),
                                "",
                                ""
                        ),
                        List.of(),
                        List.of()
                );
    }

    @Test
    void rateLimitFallsBackAndCooldownSkipsPrimaryOnNextRequest() {

        RewriteResponse fallbackResponse =
                response();

        FakeProvider gemini =
                new FakeProvider(
                        "gemini",
                        new ProviderException(
                                FailureReason.RATE_LIMIT,
                                429,
                                "quota"
                        )
                );

        FakeProvider groq =
                new FakeProvider(
                        "groq",
                        fallbackResponse,
                        fallbackResponse
                );

        CvRewriteProviderRouter router =
                new CvRewriteProviderRouter(
                        List.of(
                                gemini,
                                groq
                        ),
                        properties,
                        clock
                );

        Optional<RewriteResponse> first =
                router.generate(
                        request,
                        ignored -> true
                );

        Optional<RewriteResponse> second =
                router.generate(
                        request,
                        ignored -> true
                );

        assertThat(first)
                .contains(
                        fallbackResponse
                );

        assertThat(second)
                .contains(
                        fallbackResponse
                );

        /*
         * Gemini is attempted once, receives 429,
         * then remains in cooldown.
         */
        assertThat(
                gemini.calls()
        ).isEqualTo(
                1
        );

        assertThat(
                groq.calls()
        ).isEqualTo(
                2
        );
    }

    @Test
    void serverErrorRetriesExactlyOnceBeforeUsingSuccessfulResponse() {

        RewriteResponse success =
                response();

        FakeProvider gemini =
                new FakeProvider(
                        "gemini",
                        new ProviderException(
                                FailureReason.SERVER_ERROR,
                                503,
                                "temporary"
                        ),
                        success
                );

        FakeProvider groq =
                new FakeProvider(
                        "groq",
                        response()
                );

        CvRewriteProviderRouter router =
                new CvRewriteProviderRouter(
                        List.of(
                                gemini,
                                groq
                        ),
                        properties,
                        clock
                );

        Optional<RewriteResponse> result =
                router.generate(
                        request,
                        ignored -> true
                );

        assertThat(result)
                .contains(
                        success
                );

        assertThat(
                gemini.calls()
        ).isEqualTo(
                2
        );

        assertThat(
                groq.calls()
        ).isZero();
    }

    @Test
    void timeoutDoesNotRetryAndFallsBackImmediately() {

        RewriteResponse fallback =
                response();

        FakeProvider gemini =
                new FakeProvider(
                        "gemini",
                        new ProviderException(
                                FailureReason.TIMEOUT,
                                "timeout"
                        )
                );

        FakeProvider groq =
                new FakeProvider(
                        "groq",
                        fallback
                );

        CvRewriteProviderRouter router =
                new CvRewriteProviderRouter(
                        List.of(
                                gemini,
                                groq
                        ),
                        properties,
                        clock
                );

        Optional<RewriteResponse> result =
                router.generate(
                        request,
                        ignored -> true
                );

        assertThat(result)
                .contains(
                        fallback
                );

        assertThat(
                gemini.calls()
        ).isEqualTo(
                1
        );

        assertThat(
                groq.calls()
        ).isEqualTo(
                1
        );
    }

    @Test
    void rejectedBusinessOutputFallsBackWithoutRetryingSameProvider() {

        RewriteResponse rejected =
                response();

        RewriteResponse accepted =
                response();

        FakeProvider gemini =
                new FakeProvider(
                        "gemini",
                        rejected
                );

        FakeProvider groq =
                new FakeProvider(
                        "groq",
                        accepted
                );

        CvRewriteProviderRouter router =
                new CvRewriteProviderRouter(
                        List.of(
                                gemini,
                                groq
                        ),
                        properties,
                        clock
                );

        Optional<RewriteResponse> result =
                router.generate(
                        request,
                        response ->
                                response == accepted
                );

        assertThat(result)
                .contains(
                        accepted
                );

        assertThat(
                gemini.calls()
        ).isEqualTo(
                1
        );

        assertThat(
                groq.calls()
        ).isEqualTo(
                1
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
                    List.of(
                            outcomes
                    )
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

            Object outcome =
                    outcomes.poll();

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

    private static final class MutableClock
            extends Clock {

        private Instant instant;

        private final ZoneId zone;

        private MutableClock(
                Instant instant,
                ZoneId zone
        ) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(
                ZoneId zone
        ) {
            return new MutableClock(
                    instant,
                    zone
            );
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}