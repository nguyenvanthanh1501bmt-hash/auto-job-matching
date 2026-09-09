package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CvTailoringAnalysisStoreHardeningTest {

    private static final String USER_ID = "user-1";
    private static final String CANDIDATE_ID = "candidate-1";
    private static final String JOB_ID = "job-1";
    private static final String EMBEDDING_ID = "embedding-1";
    private static final String RANKING_VERSION = "hybrid-v1";

    private MutableClock clock;
    private CvTailoringAnalysisStore store;
    private CandidateProfile profile;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(
                Instant.parse("2026-09-09T00:00:00Z"),
                ZoneOffset.UTC
        );

        store = new CvTailoringAnalysisStore(clock);
        profile = profile(
                Instant.parse("2026-09-09T00:00:00Z")
        );
    }

    @Test
    void rejectsExpiredAnalysis() {
        CvTailoringAnalysisStore.AnalysisContext context = saveContext();

        clock.advance(Duration.ofMinutes(31));

        assertNotFound(
                () -> store.require(
                        context.analysisId(),
                        USER_ID,
                        CANDIDATE_ID,
                        JOB_ID
                )
        );
    }

    @Test
    void rejectsWrongOwnerWithoutLeakingAnalysisExistence() {
        CvTailoringAnalysisStore.AnalysisContext context = saveContext();

        assertNotFound(
                () -> store.require(
                        context.analysisId(),
                        "other-user",
                        CANDIDATE_ID,
                        JOB_ID
                )
        );
    }

    @Test
    void rejectsWrongCandidateWithoutLeakingAnalysisExistence() {
        CvTailoringAnalysisStore.AnalysisContext context = saveContext();

        assertNotFound(
                () -> store.require(
                        context.analysisId(),
                        USER_ID,
                        "candidate-2",
                        JOB_ID
                )
        );
    }

    @Test
    void rejectsWrongJobWithoutLeakingAnalysisExistence() {
        CvTailoringAnalysisStore.AnalysisContext context = saveContext();

        assertNotFound(
                () -> store.require(
                        context.analysisId(),
                        USER_ID,
                        CANDIDATE_ID,
                        "job-2"
                )
        );
    }

    @Test
    void rejectsProfileChangedAfterAnalyze() {
        CvTailoringAnalysisStore.AnalysisContext context = saveContext();

        CandidateProfile changed = profile(
                Instant.parse("2026-09-09T00:05:00Z")
        );

        assertThatThrownBy(
                () -> store.assertProfileUnchanged(
                        context,
                        changed
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(throwable -> {
                    ResponseStatusException exception =
                            (ResponseStatusException) throwable;

                    assertThat(exception.getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT);
                })
                .hasMessageContaining(
                        "Candidate profile changed after CV tailoring analysis"
                );
    }

    private CvTailoringAnalysisStore.AnalysisContext saveContext() {
        return store.save(
                USER_ID,
                profile,
                JOB_ID,
                EMBEDDING_ID,
                RANKING_VERSION,
                List.of(),
                List.of()
        );
    }

    private CandidateProfile profile(Instant updatedAt) {
        return CandidateProfile
                .builder()
                .id(CANDIDATE_ID)
                .ownerUserId(USER_ID)
                .updatedAt(updatedAt)
                .parserVersion("parser-v1")
                .sourceSha256("sha-1")
                .build();
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(throwable -> {
                    ResponseStatusException exception =
                            (ResponseStatusException) throwable;

                    assertThat(exception.getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND);
                })
                .hasMessageContaining(
                        "CV tailoring analysis was not found or has expired"
                );
    }

    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(
                    instant,
                    zone
            );
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}