package com.autojob.modules.jobembedding.service;

import com.autojob.common.embedding.client.EmbeddingClient;
import com.autojob.common.embedding.client.dto.EmbeddingResponse;
import com.autojob.common.embedding.config.EmbeddingProperties;
import com.autojob.common.embedding.service.EmbeddingTextHashCalculator;
import com.autojob.modules.jobembedding.config.JobEmbeddingProperties;
import com.autojob.modules.jobembedding.config.QdrantProperties;
import com.autojob.modules.jobembedding.domain.JobEmbedding;
import com.autojob.modules.jobembedding.domain.JobEmbeddingStatus;
import com.autojob.modules.jobembedding.repository.JobEmbeddingRepository;
import com.autojob.modules.jobembedding.text.JobEmbeddingTextBuilder;
import com.autojob.modules.jobembedding.vectorstore.JobVectorPoint;
import com.autojob.modules.jobembedding.vectorstore.JobVectorStore;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.normalization.TextNormalizer;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobEmbeddingServiceTest {

    private static final String JOB_ID =
            "normalized-001";

    private static final String EMBEDDING_VERSION =
            "test-model@revision-1|prep-v1|l2";

    private static final String TEXT_VERSION =
            "job-text-v2";

    private static final Instant NOW =
            Instant.parse(
                    "2026-07-21T04:00:00Z"
            );

    @Mock
    private NormalizedJobRepository
            normalizedJobRepository;

    @Mock
    private JobEmbeddingRepository
            jobEmbeddingRepository;

    @Mock
    private EmbeddingClient
            embeddingClient;

    @Mock
    private JobVectorStore
            jobVectorStore;

    private EmbeddingTextHashCalculator
            hashCalculator;

    private JobEmbeddingTextBuilder
            textBuilder;

    private JobEmbeddingPointIdFactory
            pointIdFactory;

    private JobEmbeddingService service;

    @BeforeEach
    void setUp() {
        hashCalculator =
                new EmbeddingTextHashCalculator();

        pointIdFactory =
                new JobEmbeddingPointIdFactory();

        EmbeddingProperties embeddingProperties =
                new EmbeddingProperties();

        embeddingProperties
                .setExpectedDimension(3);

        embeddingProperties
                .setExpectedVersion(
                        EMBEDDING_VERSION
                );

        JobEmbeddingProperties
                jobEmbeddingProperties =
                new JobEmbeddingProperties();

        jobEmbeddingProperties
                .setTextVersion(
                        TEXT_VERSION
                );

        QdrantProperties qdrantProperties =
                new QdrantProperties();

        qdrantProperties.setCollection(
                "job_vectors_v1"
        );

        qdrantProperties.setDimension(3);
        qdrantProperties.setDistance(
                "Cosine"
        );

        textBuilder =
                new JobEmbeddingTextBuilder(
                        new TextNormalizer(),
                        jobEmbeddingProperties
                );

        service =
                new JobEmbeddingService(
                        normalizedJobRepository,
                        jobEmbeddingRepository,
                        embeddingClient,
                        jobVectorStore,
                        hashCalculator,
                        textBuilder,
                        pointIdFactory,
                        embeddingProperties,
                        jobEmbeddingProperties,
                        qdrantProperties,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        )
                );
    }

    @Test
    void shouldBuildPassageAndPersistTextVersion() {
        NormalizedJob job =
                normalizedJob();

        String text =
                textBuilder.build(job);

        String textHash =
                hashCalculator.calculate(
                        text
                );

        when(
                normalizedJobRepository.findById(
                        JOB_ID
                )
        ).thenReturn(
                Optional.of(job)
        );

        when(
                jobEmbeddingRepository
                        .findByNormalizedJobIdAndEmbeddingVersionAndTextVersion(
                                JOB_ID,
                                EMBEDDING_VERSION,
                                TEXT_VERSION
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                jobEmbeddingRepository.insert(
                        any(JobEmbedding.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        when(
                embeddingClient.embed(text)
        ).thenReturn(
                validResponse(textHash)
        );

        when(
                jobEmbeddingRepository.save(
                        any(JobEmbedding.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        JobEmbedding result =
                service.embed(JOB_ID);

        assertThat(text)
                .startsWith(
                        "passage: "
                );

        assertThat(
                result.getTextVersion()
        ).isEqualTo(
                TEXT_VERSION
        );

        assertThat(
                result.getStatus()
        ).isEqualTo(
                JobEmbeddingStatus.READY
        );

        verify(
                embeddingClient
        ).embed(text);

        ArgumentCaptor<JobVectorPoint>
                pointCaptor =
                ArgumentCaptor.forClass(
                        JobVectorPoint.class
                );

        verify(
                jobVectorStore
        ).upsert(
                pointCaptor.capture()
        );

        JobVectorPoint point =
                pointCaptor.getValue();

        assertThat(
                point.normalizedJobId()
        ).isEqualTo(
                JOB_ID
        );

        assertThat(
                point.textVersion()
        ).isEqualTo(
                TEXT_VERSION
        );

        assertThat(
                point.embeddingVersion()
        ).isEqualTo(
                EMBEDDING_VERSION
        );
    }

    @Test
    void shouldSkipReadyUnchangedEmbeddingWhenPointExists() {
        NormalizedJob job =
                normalizedJob();

        String text =
                textBuilder.build(job);

        String textHash =
                hashCalculator.calculate(
                        text
                );

        String pointId =
                pointIdFactory.create(
                        JOB_ID,
                        EMBEDDING_VERSION
                );

        JobEmbedding existing =
                JobEmbedding.builder()
                        .normalizedJobId(
                                JOB_ID
                        )
                        .normalizationVersion(
                                "rule-v2"
                        )
                        .embeddingVersion(
                                EMBEDDING_VERSION
                        )
                        .textVersion(
                                TEXT_VERSION
                        )
                        .textHash(
                                textHash
                        )
                        .qdrantPointId(
                                pointId
                        )
                        .status(
                                JobEmbeddingStatus.READY
                        )
                        .build();

        when(
                normalizedJobRepository.findById(
                        JOB_ID
                )
        ).thenReturn(
                Optional.of(job)
        );

        when(
                jobEmbeddingRepository
                        .findByNormalizedJobIdAndEmbeddingVersionAndTextVersion(
                                JOB_ID,
                                EMBEDDING_VERSION,
                                TEXT_VERSION
                        )
        ).thenReturn(
                Optional.of(existing)
        );

        when(
                jobVectorStore.pointExists(
                        pointId
                )
        ).thenReturn(true);

        JobEmbedding result =
                service.embed(JOB_ID);

        assertThat(result)
                .isSameAs(existing);

        verifyNoInteractions(
                embeddingClient
        );

        verify(
                jobVectorStore,
                never()
        ).upsert(any());
    }

    @Test
    void shouldUseTextVersionInRepositoryKey() {
        NormalizedJob job =
                normalizedJob();

        String text =
                textBuilder.build(job);

        String textHash =
                hashCalculator.calculate(
                        text
                );

        when(
                normalizedJobRepository.findById(
                        JOB_ID
                )
        ).thenReturn(
                Optional.of(job)
        );

        when(
                jobEmbeddingRepository
                        .findByNormalizedJobIdAndEmbeddingVersionAndTextVersion(
                                JOB_ID,
                                EMBEDDING_VERSION,
                                TEXT_VERSION
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                jobEmbeddingRepository.insert(
                        any(JobEmbedding.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        when(
                embeddingClient.embed(text)
        ).thenReturn(
                validResponse(textHash)
        );

        when(
                jobEmbeddingRepository.save(
                        any(JobEmbedding.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        service.embed(JOB_ID);

        verify(
                jobEmbeddingRepository
        )
                .findByNormalizedJobIdAndEmbeddingVersionAndTextVersion(
                        JOB_ID,
                        EMBEDDING_VERSION,
                        TEXT_VERSION
                );
    }

    @Test
    void shouldFailWhenProviderReturnsDifferentVersion() {
        NormalizedJob job =
                normalizedJob();

        String text =
                textBuilder.build(job);

        String textHash =
                hashCalculator.calculate(
                        text
                );

        when(
                normalizedJobRepository.findById(
                        JOB_ID
                )
        ).thenReturn(
                Optional.of(job)
        );

        when(
                jobEmbeddingRepository
                        .findByNormalizedJobIdAndEmbeddingVersionAndTextVersion(
                                JOB_ID,
                                EMBEDDING_VERSION,
                                TEXT_VERSION
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                jobEmbeddingRepository.insert(
                        any(JobEmbedding.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        when(
                embeddingClient.embed(text)
        ).thenReturn(
                new EmbeddingResponse(
                        List.of(
                                1.0,
                                0.0,
                                0.0
                        ),
                        3,
                        "test-model",
                        "revision-2",
                        "different-version",
                        textHash,
                        true
                )
        );

        when(
                jobEmbeddingRepository.save(
                        any(JobEmbedding.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        assertThatThrownBy(
                () -> service.embed(
                        JOB_ID
                )
        )
                .isInstanceOf(
                        JobEmbeddingProcessingException.class
                )
                .hasMessageContaining(
                        "version mismatch"
                );
    }

    private NormalizedJob normalizedJob() {
        return NormalizedJob.builder()
                .id(JOB_ID)
                .sourceCode("MOCK")
                .normalizationVersion(
                        "rule-v2"
                )
                .title(
                        "Senior Java Backend Engineer"
                )
                .skills(
                        List.of(
                                "Java",
                                "Spring Boot"
                        )
                )
                .requirementsText(
                        "3+ years Java and Spring Boot"
                )
                .build();
    }

    private EmbeddingResponse validResponse(
            String textHash
    ) {
        return new EmbeddingResponse(
                List.of(
                        1.0,
                        0.0,
                        0.0
                ),
                3,
                "test-model",
                "revision-1",
                EMBEDDING_VERSION,
                textHash,
                true
        );
    }
}