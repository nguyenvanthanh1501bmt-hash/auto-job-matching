package com.autojob.modules.matching.service;

import com.autojob.modules.candidateembedding.domain.CandidateEmbedding;
import com.autojob.modules.candidateembedding.domain.CandidateEmbeddingStatus;
import com.autojob.modules.candidateembedding.repository.CandidateEmbeddingRepository;
import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import com.autojob.modules.jobembedding.search.JobVectorSearchCriteria;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.config.MatchingProperties;
import com.autojob.modules.matching.contract.MatchingRunResult;
import com.autojob.modules.matching.domain.HybridScore;
import com.autojob.modules.matching.domain.MatchResult;
import com.autojob.modules.matching.repository.MatchResultRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridMatchingServiceTest {

    private static final Instant NOW =
            Instant.parse(
                    "2026-09-08T01:00:00Z"
            );

    @Mock
    private CandidateProfileRepository
            candidateProfileRepository;

    @Mock
    private CandidateEmbeddingRepository
            candidateEmbeddingRepository;

    @Mock
    private MatchingEvaluationService
            matchingEvaluationService;

    @Mock
    private MatchResultRepository
            matchResultRepository;

    private MatchingProperties
            properties;

    private HybridMatchingService
            service;

    @BeforeEach
    void setUp() {

        properties =
                new MatchingProperties();

        properties.setVersion(
                "hybrid-test-v1"
        );

        properties
                .getCompatibility()
                .setCandidateTextVersion(
                        "candidate-text-v1"
                );

        service =
                new HybridMatchingService(
                        candidateProfileRepository,
                        candidateEmbeddingRepository,
                        matchingEvaluationService,
                        matchResultRepository,
                        properties,

                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        )
                );
    }

    @Test
    void forceRunUsesSharedEvaluationAndPersistsItsRankedJobs() {

        CandidateProfile profile =
                CandidateProfile
                        .builder()

                        .id(
                                "candidate-1"
                        )

                        .rawCvId(
                                "raw-1"
                        )

                        .ownerUserId(
                                "user-1"
                        )

                        .parserVersion(
                                "parser-v1"
                        )

                        .updatedAt(
                                Instant.parse(
                                        "2026-09-08T00:00:00Z"
                                )
                        )

                        .build();

        CandidateEmbedding embedding =
                CandidateEmbedding
                        .builder()

                        .id(
                                "embedding-1"
                        )

                        .candidateProfileId(
                                "candidate-1"
                        )

                        .rawCvId(
                                "raw-1"
                        )

                        .parserVersion(
                                "parser-v1"
                        )

                        .textVersion(
                                "candidate-text-v1"
                        )

                        .embeddingVersion(
                                "model@rev|prep-v1|l2"
                        )

                        .dimension(
                                3
                        )

                        .normalized(
                                true
                        )

                        .vector(
                                List.of(
                                        1.0d,
                                        0.0d,
                                        0.0d
                                )
                        )

                        .status(
                                CandidateEmbeddingStatus.READY
                        )

                        .embeddedAt(
                                Instant.parse(
                                        "2026-09-08T00:30:00Z"
                                )
                        )

                        .build();

        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .id(
                                "job-1"
                        )
                        .title(
                                "Backend Developer"
                        )
                        .companyName(
                                "Example Co"
                        )
                        .build();

        HybridRankingService.RankedJob rankedJob =
                new HybridRankingService
                        .RankedJob(
                        1,

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
                                "Java"
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
                                "model@rev|prep-v1|l2",
                                "job-text-v2"
                        ),

                        100,

                        98,

                        List.of(
                                rankedJob
                        ),

                        Set.of(
                                "job-1"
                        ),

                        Set.of(
                                "job-1"
                        )
                );

        when(
                candidateProfileRepository
                        .findById(
                                "candidate-1"
                        )
        ).thenReturn(
                Optional.of(
                        profile
                )
        );

        when(
                candidateEmbeddingRepository
                        .findFirstByCandidateProfileIdAndStatusAndTextVersionOrderByUpdatedAtDesc(
                                "candidate-1",
                                CandidateEmbeddingStatus.READY,
                                "candidate-text-v1"
                        )
        ).thenReturn(
                Optional.of(
                        embedding
                )
        );

        when(
                matchingEvaluationService
                        .evaluate(
                                profile,
                                embedding.getVector(),
                                embedding
                                        .getEmbeddingVersion()
                        )
        ).thenReturn(
                evaluation
        );

        when(
                matchResultRepository
                        .saveAll(
                                org.mockito
                                        .ArgumentMatchers
                                        .anyList()
                        )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(
                                0
                        )
        );

        MatchingRunResult result =
                service.run(
                        "candidate-1",
                        "user-1",
                        true
                );

        assertThat(
                result.retrievedCount()
        ).isEqualTo(
                100
        );

        assertThat(
                result.loadedJobCount()
        ).isEqualTo(
                98
        );

        assertThat(
                result.matchedCount()
        ).isEqualTo(
                1
        );

        MatchResult saved =
                result
                        .results()
                        .getFirst();

        assertThat(
                saved.getFinalScore()
        ).isEqualTo(
                0.76d
        );

        assertThat(
                saved.getSkillKnown()
        ).isTrue();

        assertThat(
                saved.getSeniorityKnown()
        ).isTrue();

        assertThat(
                saved.getLocationKnown()
        ).isTrue();

        assertThat(
                saved.getFreshnessKnown()
        ).isTrue();

        verify(
                matchingEvaluationService
        ).evaluate(
                profile,
                embedding.getVector(),
                embedding
                        .getEmbeddingVersion()
        );

        verify(
                matchResultRepository
        ).deleteByCandidateProfileIdAndCandidateEmbeddingIdAndRankingVersion(
                "candidate-1",
                "embedding-1",
                "hybrid-test-v1"
        );
    }
}