package com.autojob.modules.cvtailoring.service;

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
import com.autojob.modules.matching.service.HybridMatchingService;
import com.autojob.modules.matching.service.HybridRankingService;
import com.autojob.modules.matching.service.MatchingEvaluationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvTailoringPreviewServiceTest {

    private static final String CANDIDATE_ID =
            "candidate-1";

    private static final String JOB_ID =
            "job-1";

    private static final String USER_ID =
            "user-1";

    private static final String ANALYSIS_ID =
            "analysis-1";

    private static final String EMBEDDING_ID =
            "embedding-1";

    private static final String RANKING_VERSION =
            "hybrid-v1";

    private static final String EMBEDDING_VERSION =
            "model@rev|prep-v1|l2";

    private static final String TEXT_VERSION =
            "candidate-text-v1";

    @Mock
    private CvTailoringDraftService
            draftService;

    @Mock
    private HybridMatchingService
            hybridMatchingService;

    @Mock
    private CandidateEmbeddingGenerator
            candidateEmbeddingGenerator;

    @Mock
    private MatchingEvaluationService
            matchingEvaluationService;

    private MatchingProperties
            matchingProperties;

    private CvTailoringPreviewService
            service;

    @BeforeEach
    void setUp() {

        matchingProperties =
                new MatchingProperties();

        matchingProperties
                .getCompatibility()
                .setCandidateTextVersion(
                        TEXT_VERSION
                );

        service =
                new CvTailoringPreviewService(
                        draftService,
                        hybridMatchingService,
                        candidateEmbeddingGenerator,
                        matchingEvaluationService,
                        matchingProperties
                );
    }

    @Test
    void returnsMatchedAfterScoreFromRealEvaluationResult() {

        CandidateProfile original =
                profile();

        CandidateProfile temporary =
                profile();

        CvTailoringAnalysisStore.AnalysisContext context =
                context();

        CvTailoringDraftService.TemporaryDraft draft =
                new CvTailoringDraftService
                        .TemporaryDraft(
                        context,
                        original,
                        temporary,
                        List.of(
                                "emphasize-0"
                        )
                );

        MatchResult beforeMatch =
                beforeMatch();

        MatchingRunResult beforeRun =
                new MatchingRunResult(
                        CANDIDATE_ID,
                        EMBEDDING_ID,
                        RANKING_VERSION,
                        0,
                        0,
                        1,
                        true,
                        List.of(
                                beforeMatch
                        )
                );

        CandidateEmbeddingGenerator
                .GeneratedCandidateEmbedding generated =
                generatedEmbedding();

        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .id(
                                JOB_ID
                        )
                        .title(
                                "Backend Developer"
                        )
                        .build();

        HybridRankingService.RankedJob ranked =
                new HybridRankingService
                        .RankedJob(
                        2,

                        job,

                        "point-1",

                        new HybridScore(
                                0.76d,
                                0.79d,
                                0.70d,
                                0.80d,
                                1.00d,
                                0.90d,
                                true,
                                true,
                                true,
                                true
                        ),

                        List.of(
                                "Java",
                                "PostgreSQL"
                        ),

                        List.of(
                                "Docker"
                        )
                );

        MatchingEvaluationService
                .EvaluationResult evaluation =
                new MatchingEvaluationService
                        .EvaluationResult(

                        new JobVectorSearchCriteria(
                                100,
                                "rule-v4",
                                EMBEDDING_VERSION,
                                "job-text-v2"
                        ),

                        100,

                        98,

                        List.of(
                                ranked
                        ),

                        Set.of(
                                JOB_ID
                        ),

                        Set.of(
                                JOB_ID
                        )
                );

        CvTailoringPreviewRequest request =
                new CvTailoringPreviewRequest(
                        ANALYSIS_ID,
                        List.of(
                                "emphasize-0"
                        )
                );

        when(
                draftService
                        .createTemporaryDraft(
                                ANALYSIS_ID,
                                CANDIDATE_ID,
                                JOB_ID,
                                USER_ID,
                                List.of(
                                        "emphasize-0"
                                )
                        )
        ).thenReturn(
                draft
        );

        when(
                hybridMatchingService
                        .getCurrent(
                                CANDIDATE_ID,
                                USER_ID
                        )
        ).thenReturn(
                beforeRun
        );

        when(
                candidateEmbeddingGenerator
                        .generate(
                                temporary
                        )
        ).thenReturn(
                generated
        );

        when(
                matchingEvaluationService
                        .evaluate(
                                temporary,
                                generated.vector(),
                                EMBEDDING_VERSION
                        )
        ).thenReturn(
                evaluation
        );

        CvTailoringPreviewResponse response =
                service.preview(
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID,
                        request
                );

        assertThat(
                response
                        .before()
                        .status()
        ).isEqualTo(
                PreviewStatus.MATCHED
        );

        assertThat(
                response
                        .before()
                        .finalScore()
        ).isEqualTo(
                0.67d
        );

        assertThat(
                response
                        .after()
                        .status()
        ).isEqualTo(
                PreviewStatus.MATCHED
        );

        assertThat(
                response
                        .after()
                        .finalScore()
        ).isEqualTo(
                0.76d
        );

        assertThat(
                response
                        .after()
                        .rank()
        ).isEqualTo(
                2
        );

        assertThat(
                response
                        .appliedSuggestionIds()
        ).containsExactly(
                "emphasize-0"
        );
    }

    @Test
    void returnsNotMatchedWhenTargetWasRetrievedButNotInFinalResult() {

        CandidateProfile original =
                profile();

        CandidateProfile temporary =
                profile();

        CvTailoringDraftService.TemporaryDraft draft =
                new CvTailoringDraftService
                        .TemporaryDraft(
                        context(),
                        original,
                        temporary,
                        List.of()
                );

        MatchingRunResult beforeRun =
                new MatchingRunResult(
                        CANDIDATE_ID,
                        EMBEDDING_ID,
                        RANKING_VERSION,
                        0,
                        0,
                        1,
                        true,
                        List.of(
                                beforeMatch()
                        )
                );

        CandidateEmbeddingGenerator
                .GeneratedCandidateEmbedding generated =
                generatedEmbedding();

        MatchingEvaluationService
                .EvaluationResult evaluation =
                new MatchingEvaluationService
                        .EvaluationResult(

                        new JobVectorSearchCriteria(
                                100,
                                "rule-v4",
                                EMBEDDING_VERSION,
                                "job-text-v2"
                        ),

                        100,

                        98,

                        List.of(),

                        Set.of(
                                JOB_ID
                        ),

                        Set.of(
                                JOB_ID
                        )
                );

        CvTailoringPreviewRequest request =
                new CvTailoringPreviewRequest(
                        ANALYSIS_ID,
                        List.of()
                );

        when(
                draftService
                        .createTemporaryDraft(
                                ANALYSIS_ID,
                                CANDIDATE_ID,
                                JOB_ID,
                                USER_ID,
                                List.of()
                        )
        ).thenReturn(
                draft
        );

        when(
                hybridMatchingService
                        .getCurrent(
                                CANDIDATE_ID,
                                USER_ID
                        )
        ).thenReturn(
                beforeRun
        );

        when(
                candidateEmbeddingGenerator
                        .generate(
                                temporary
                        )
        ).thenReturn(
                generated
        );

        when(
                matchingEvaluationService
                        .evaluate(
                                temporary,
                                generated.vector(),
                                EMBEDDING_VERSION
                        )
        ).thenReturn(
                evaluation
        );

        CvTailoringPreviewResponse response =
                service.preview(
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID,
                        request
                );

        assertThat(
                response
                        .after()
                        .status()
        ).isEqualTo(
                PreviewStatus.NOT_MATCHED
        );

        assertThat(
                response
                        .after()
                        .finalScore()
        ).isNull();

        assertThat(
                response
                        .after()
                        .reason()
        ).contains(
                "eligibility"
        );
    }

    @Test
    void returnsNotRetrievedWithoutInventingAfterScore() {

        CandidateProfile original =
                profile();

        CandidateProfile temporary =
                profile();

        CvTailoringDraftService.TemporaryDraft draft =
                new CvTailoringDraftService
                        .TemporaryDraft(
                        context(),
                        original,
                        temporary,
                        List.of()
                );

        MatchingRunResult beforeRun =
                new MatchingRunResult(
                        CANDIDATE_ID,
                        EMBEDDING_ID,
                        RANKING_VERSION,
                        0,
                        0,
                        1,
                        true,
                        List.of(
                                beforeMatch()
                        )
                );

        CandidateEmbeddingGenerator
                .GeneratedCandidateEmbedding generated =
                generatedEmbedding();

        MatchingEvaluationService
                .EvaluationResult evaluation =
                new MatchingEvaluationService
                        .EvaluationResult(

                        new JobVectorSearchCriteria(
                                100,
                                "rule-v4",
                                EMBEDDING_VERSION,
                                "job-text-v2"
                        ),

                        100,

                        97,

                        List.of(),

                        Set.of(
                                "job-other"
                        ),

                        Set.of(
                                "job-other"
                        )
                );

        CvTailoringPreviewRequest request =
                new CvTailoringPreviewRequest(
                        ANALYSIS_ID,
                        List.of()
                );

        when(
                draftService
                        .createTemporaryDraft(
                                ANALYSIS_ID,
                                CANDIDATE_ID,
                                JOB_ID,
                                USER_ID,
                                List.of()
                        )
        ).thenReturn(
                draft
        );

        when(
                hybridMatchingService
                        .getCurrent(
                                CANDIDATE_ID,
                                USER_ID
                        )
        ).thenReturn(
                beforeRun
        );

        when(
                candidateEmbeddingGenerator
                        .generate(
                                temporary
                        )
        ).thenReturn(
                generated
        );

        when(
                matchingEvaluationService
                        .evaluate(
                                temporary,
                                generated.vector(),
                                EMBEDDING_VERSION
                        )
        ).thenReturn(
                evaluation
        );

        CvTailoringPreviewResponse response =
                service.preview(
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID,
                        request
                );

        assertThat(
                response
                        .after()
                        .status()
        ).isEqualTo(
                PreviewStatus.NOT_RETRIEVED
        );

        assertThat(
                response
                        .after()
                        .finalScore()
        ).isNull();

        assertThat(
                response
                        .after()
                        .rank()
        ).isNull();
    }

    private CandidateProfile profile() {

        return CandidateProfile
                .builder()

                .id(
                        CANDIDATE_ID
                )

                .ownerUserId(
                        USER_ID
                )

                .updatedAt(
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        )
                )

                .parserVersion(
                        "parser-v1"
                )

                .sourceSha256(
                        "sha-1"
                )

                .build();
    }

    private CvTailoringAnalysisStore
            .AnalysisContext context() {

        return new CvTailoringAnalysisStore
                .AnalysisContext(

                ANALYSIS_ID,

                USER_ID,

                CANDIDATE_ID,

                JOB_ID,

                EMBEDDING_ID,

                RANKING_VERSION,

                Instant.parse(
                        "2026-09-08T00:00:00Z"
                ),

                "parser-v1",

                "sha-1",

                Instant.parse(
                        "2026-09-08T00:01:00Z"
                ),

                Instant.parse(
                        "2026-09-08T00:31:00Z"
                ),

                List.of(),

                List.of()
        );
    }

    private MatchResult beforeMatch() {

        return MatchResult
                .builder()

                .candidateProfileId(
                        CANDIDATE_ID
                )

                .candidateEmbeddingId(
                        EMBEDDING_ID
                )

                .normalizedJobId(
                        JOB_ID
                )

                .rank(
                        1
                )

                .finalScore(
                        0.67d
                )

                .semanticScore(
                        0.70d
                )

                .skillScore(
                        0.55d
                )

                .seniorityScore(
                        0.80d
                )

                .locationScore(
                        1.00d
                )

                .freshnessScore(
                        0.90d
                )

                .skillKnown(
                        true
                )

                .seniorityKnown(
                        true
                )

                .locationKnown(
                        true
                )

                .freshnessKnown(
                        true
                )

                .candidateTextVersion(
                        TEXT_VERSION
                )

                .embeddingVersion(
                        EMBEDDING_VERSION
                )

                .matchedSkills(
                        List.of(
                                "Java"
                        )
                )

                .missingSkills(
                        List.of(
                                "Docker"
                        )
                )

                .build();
    }

    private CandidateEmbeddingGenerator
            .GeneratedCandidateEmbedding
    generatedEmbedding() {

        return new CandidateEmbeddingGenerator
                .GeneratedCandidateEmbedding(

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