package com.autojob.modules.matching.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.jobembedding.search.JobVectorHit;
import com.autojob.modules.jobembedding.search.JobVectorSearchCriteria;
import com.autojob.modules.jobembedding.search.JobVectorSearchPort;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import com.autojob.modules.matching.config.MatchingProperties;
import com.autojob.modules.matching.domain.HybridScore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchingEvaluationServiceTest {

    @Test
    void evaluatesUsingConfiguredRetrievalAndExistingRankingService() {

        JobVectorSearchPort searchPort =
                mock(
                        JobVectorSearchPort.class
                );

        NormalizedJobRepository jobRepository =
                mock(
                        NormalizedJobRepository.class
                );

        HybridRankingService rankingService =
                mock(
                        HybridRankingService.class
                );

        MatchingProperties properties =
                new MatchingProperties();

        properties
                .getRetrieval()
                .setCandidatePoolSize(
                        100
                );

        properties
                .getRetrieval()
                .setResultLimit(
                        20
                );

        properties
                .getCompatibility()
                .setNormalizationVersion(
                        "rule-v4"
                );

        properties
                .getCompatibility()
                .setJobTextVersion(
                        "job-text-v2"
                );

        MatchingEvaluationService service =
                new MatchingEvaluationService(
                        searchPort,
                        jobRepository,
                        rankingService,
                        properties
                );

        CandidateProfile profile =
                CandidateProfile
                        .builder()
                        .id(
                                "candidate-1"
                        )
                        .build();

        List<Double> vector =
                List.of(
                        1.0d,
                        0.0d,
                        0.0d
                );

        JobVectorHit hit =
                new JobVectorHit(
                        "job-1",
                        "point-1",
                        0.91d
                );

        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .id(
                                "job-1"
                        )
                        .title(
                                "Backend Developer"
                        )
                        .build();

        HybridRankingService.RankedJob rankedJob =
                new HybridRankingService
                        .RankedJob(
                        1,
                        job,
                        "point-1",

                        new HybridScore(
                                0.80d,
                                0.82d,
                                0.75d,
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

        ArgumentCaptor<JobVectorSearchCriteria>
                criteriaCaptor =
                ArgumentCaptor.forClass(
                        JobVectorSearchCriteria.class
                );

        when(
                searchPort.search(
                        eq(
                                vector
                        ),
                        criteriaCaptor.capture()
                )
        ).thenReturn(
                List.of(
                        hit
                )
        );

        when(
                jobRepository.findAllById(
                        Set.of(
                                "job-1"
                        )
                )
        ).thenReturn(
                List.of(
                        job
                )
        );

        when(
                rankingService.rank(
                        eq(
                                profile
                        ),
                        org.mockito
                                .ArgumentMatchers
                                .anyList(),
                        eq(
                                20
                        )
                )
        ).thenReturn(
                List.of(
                        rankedJob
                )
        );

        MatchingEvaluationService
                .EvaluationResult result =
                service.evaluate(
                        profile,
                        vector,
                        "model@rev|prep-v1|l2"
                );

        assertThat(
                result.retrievedCount()
        ).isEqualTo(
                1
        );

        assertThat(
                result.hydratedCount()
        ).isEqualTo(
                1
        );

        assertThat(
                result.wasRetrieved(
                        "job-1"
                )
        ).isTrue();

        assertThat(
                result.wasHydrated(
                        "job-1"
                )
        ).isTrue();

        assertThat(
                result.findRankedJob(
                        "job-1"
                )
        ).contains(
                rankedJob
        );

        JobVectorSearchCriteria criteria =
                criteriaCaptor
                        .getValue();

        assertThat(
                criteria.limit()
        ).isEqualTo(
                100
        );

        assertThat(
                criteria.normalizationVersion()
        ).isEqualTo(
                "rule-v4"
        );

        assertThat(
                criteria.embeddingVersion()
        ).isEqualTo(
                "model@rev|prep-v1|l2"
        );

        assertThat(
                criteria.textVersion()
        ).isEqualTo(
                "job-text-v2"
        );

        verify(
                rankingService
        ).rank(
                eq(
                        profile
                ),
                org.mockito
                        .ArgumentMatchers
                        .anyList(),
                eq(
                        20
                )
        );
    }
}