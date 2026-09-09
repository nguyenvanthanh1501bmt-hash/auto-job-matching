package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.candidateembedding.repository.CandidateEmbeddingRepository;
import com.autojob.modules.candidateembedding.service.CandidateEmbeddingGenerator;
import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewRequest;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewResponse.PreviewStatus;
import com.autojob.modules.jobembedding.search.JobVectorSearchCriteria;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import com.autojob.modules.matching.contract.MatchingRunResult;
import com.autojob.modules.matching.domain.HybridScore;
import com.autojob.modules.matching.domain.MatchResult;
import com.autojob.modules.matching.repository.MatchResultRepository;
import com.autojob.modules.matching.service.HybridMatchingService;
import com.autojob.modules.matching.service.HybridRankingService;
import com.autojob.modules.matching.service.MatchingEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CvTailoringPreviewHardeningTest {

    private static final String USER_ID = "user-1";
    private static final String CANDIDATE_ID = "candidate-1";
    private static final String JOB_ID = "job-1";
    private static final String ANALYSIS_ID = "analysis-1";
    private static final String EMBEDDING_ID = "embedding-1";
    private static final String RANKING_VERSION = "hybrid-v1";
    private static final String EMBEDDING_VERSION =
            "model@rev|prep-v1|l2";
    private static final String TEXT_VERSION =
            "candidate-text-v1";

    private CvTailoringDraftService draftService;
    private HybridMatchingService hybridMatchingService;
    private CandidateEmbeddingGenerator candidateEmbeddingGenerator;
    private MatchingEvaluationService matchingEvaluationService;
    private CvTailoringPreviewService service;

    @BeforeEach
    void setUp() {
        draftService = mock(CvTailoringDraftService.class);
        hybridMatchingService = mock(HybridMatchingService.class);
        candidateEmbeddingGenerator =
                mock(CandidateEmbeddingGenerator.class);
        matchingEvaluationService =
                mock(MatchingEvaluationService.class);

        MatchingProperties properties =
                new MatchingProperties();

        properties
                .getCompatibility()
                .setCandidateTextVersion(TEXT_VERSION);

        service = new CvTailoringPreviewService(
                draftService,
                hybridMatchingService,
                candidateEmbeddingGenerator,
                matchingEvaluationService,
                properties
        );
    }

    @Test
    void rejectsPreviewWhenMatchingRunChangedAfterAnalyze() {
        CandidateProfile original = profile();
        CandidateProfile temporary = profile();

        CvTailoringDraftService.TemporaryDraft draft =
                new CvTailoringDraftService.TemporaryDraft(
                        context(
                                EMBEDDING_ID,
                                RANKING_VERSION
                        ),
                        original,
                        temporary,
                        List.of()
                );

        MatchingRunResult changedRun =
                new MatchingRunResult(
                        CANDIDATE_ID,
                        "embedding-2",
                        RANKING_VERSION,
                        0,
                        0,
                        1,
                        true,
                        List.of(
                                beforeMatch("embedding-2")
                        )
                );

        CvTailoringPreviewRequest request =
                new CvTailoringPreviewRequest(
                        ANALYSIS_ID,
                        List.of()
                );

        when(
                draftService.createTemporaryDraft(
                        ANALYSIS_ID,
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID,
                        List.of()
                )
        ).thenReturn(draft);

        when(
                hybridMatchingService.getCurrent(
                        CANDIDATE_ID,
                        USER_ID
                )
        ).thenReturn(changedRun);

        assertThatThrownBy(
                () -> service.preview(
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID,
                        request
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
                        "Matching result changed after CV tailoring analysis"
                );

        verifyNoInteractions(
                candidateEmbeddingGenerator,
                matchingEvaluationService
        );
    }

    @Test
    void returnsRealLowerAfterScoreWhenTailoringDecreasesMatch() {
        CandidateProfile original = profile();
        CandidateProfile temporary = profile();

        CvTailoringDraftService.TemporaryDraft draft =
                new CvTailoringDraftService.TemporaryDraft(
                        context(
                                EMBEDDING_ID,
                                RANKING_VERSION
                        ),
                        original,
                        temporary,
                        List.of("rewrite-1")
                );

        MatchResult beforeMatch =
                beforeMatch(EMBEDDING_ID);

        MatchingRunResult beforeRun =
                new MatchingRunResult(
                        CANDIDATE_ID,
                        EMBEDDING_ID,
                        RANKING_VERSION,
                        0,
                        0,
                        1,
                        true,
                        List.of(beforeMatch)
                );

        CandidateEmbeddingGenerator.GeneratedCandidateEmbedding generated =
                generatedEmbedding();

        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .id(JOB_ID)
                        .title("Backend Developer")
                        .build();

        HybridRankingService.RankedJob ranked =
                new HybridRankingService.RankedJob(
                        3,
                        job,
                        "point-1",
                        new HybridScore(
                                0.64d,
                                0.66d,
                                0.50d,
                                0.80d,
                                1.00d,
                                0.90d,
                                true,
                                true,
                                true,
                                true
                        ),
                        List.of("Java"),
                        List.of("Docker")
                );

        MatchingEvaluationService.EvaluationResult evaluation =
                new MatchingEvaluationService.EvaluationResult(
                        new JobVectorSearchCriteria(
                                100,
                                "rule-v4",
                                EMBEDDING_VERSION,
                                "job-text-v2"
                        ),
                        100,
                        98,
                        List.of(ranked),
                        Set.of(JOB_ID),
                        Set.of(JOB_ID)
                );

        CvTailoringPreviewRequest request =
                new CvTailoringPreviewRequest(
                        ANALYSIS_ID,
                        List.of("rewrite-1")
                );

        when(
                draftService.createTemporaryDraft(
                        ANALYSIS_ID,
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID,
                        List.of("rewrite-1")
                )
        ).thenReturn(draft);

        when(
                hybridMatchingService.getCurrent(
                        CANDIDATE_ID,
                        USER_ID
                )
        ).thenReturn(beforeRun);

        when(
                candidateEmbeddingGenerator.generate(temporary)
        ).thenReturn(generated);

        when(
                matchingEvaluationService.evaluate(
                        temporary,
                        generated.vector(),
                        EMBEDDING_VERSION
                )
        ).thenReturn(evaluation);

        CvTailoringPreviewResponse response =
                service.preview(
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID,
                        request
                );

        assertThat(response.before().status())
                .isEqualTo(PreviewStatus.MATCHED);

        assertThat(response.before().finalScore())
                .isEqualTo(0.67d);

        assertThat(response.after().status())
                .isEqualTo(PreviewStatus.MATCHED);

        assertThat(response.after().finalScore())
                .isEqualTo(0.64d);

        assertThat(response.after().finalScore())
                .isLessThan(response.before().finalScore());
    }

    @Test
    void previewPathHasNoPersistenceRepositoryDependency() {
        assertThat(fieldTypes(CvTailoringPreviewService.class))
                .doesNotContain(
                        CandidateEmbeddingRepository.class,
                        MatchResultRepository.class
                );

        assertThat(fieldTypes(CandidateEmbeddingGenerator.class))
                .doesNotContain(CandidateEmbeddingRepository.class);

        assertThat(fieldTypes(MatchingEvaluationService.class))
                .doesNotContain(MatchResultRepository.class);
    }

    private Set<Class<?>> fieldTypes(Class<?> type) {
        return Arrays
                .stream(type.getDeclaredFields())
                .map(Field::getType)
                .collect(
                        java.util.stream.Collectors.toSet()
                );
    }

    private CandidateProfile profile() {
        return CandidateProfile
                .builder()
                .id(CANDIDATE_ID)
                .ownerUserId(USER_ID)
                .updatedAt(
                        Instant.parse("2026-09-09T00:00:00Z")
                )
                .parserVersion("parser-v1")
                .sourceSha256("sha-1")
                .build();
    }

    private CvTailoringAnalysisStore.AnalysisContext context(
            String embeddingId,
            String rankingVersion
    ) {
        return new CvTailoringAnalysisStore.AnalysisContext(
                ANALYSIS_ID,
                USER_ID,
                CANDIDATE_ID,
                JOB_ID,
                embeddingId,
                rankingVersion,
                Instant.parse("2026-09-09T00:00:00Z"),
                "parser-v1",
                "sha-1",
                Instant.parse("2026-09-09T00:01:00Z"),
                Instant.parse("2026-09-09T00:31:00Z"),
                List.of(),
                List.of()
        );
    }

    private MatchResult beforeMatch(String embeddingId) {
        return MatchResult
                .builder()
                .candidateProfileId(CANDIDATE_ID)
                .candidateEmbeddingId(embeddingId)
                .normalizedJobId(JOB_ID)
                .rank(1)
                .finalScore(0.67d)
                .semanticScore(0.70d)
                .skillScore(0.55d)
                .seniorityScore(0.80d)
                .locationScore(1.00d)
                .freshnessScore(0.90d)
                .skillKnown(true)
                .seniorityKnown(true)
                .locationKnown(true)
                .freshnessKnown(true)
                .candidateTextVersion(TEXT_VERSION)
                .embeddingVersion(EMBEDDING_VERSION)
                .matchedSkills(List.of("Java"))
                .missingSkills(List.of("Docker"))
                .build();
    }

    private CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
    generatedEmbedding() {
        return new CandidateEmbeddingGenerator.GeneratedCandidateEmbedding(
                List.of(
                        1.0d,
                        0.0d,
                        0.0d
                ),
                3,
                "model",
                "rev",
                EMBEDDING_VERSION,
                TEXT_VERSION,
                "hash-1",
                true
        );
    }
}