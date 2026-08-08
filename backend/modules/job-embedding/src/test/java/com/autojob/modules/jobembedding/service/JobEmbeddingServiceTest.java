package com.autojob.modules.jobembedding.service;

import com.autojob.common.embedding.client.EmbeddingClient;
import com.autojob.common.embedding.client.EmbeddingClientException;
import com.autojob.common.embedding.client.dto.EmbeddingResponse;
import com.autojob.common.embedding.config.EmbeddingProperties;
import com.autojob.common.embedding.service.EmbeddingTextHashCalculator;
import com.autojob.modules.jobembedding.config.QdrantProperties;
import com.autojob.modules.jobembedding.domain.JobEmbedding;
import com.autojob.modules.jobembedding.domain.JobEmbeddingStatus;
import com.autojob.modules.jobembedding.repository.JobEmbeddingRepository;
import com.autojob.modules.jobembedding.vectorstore.JobVectorPoint;
import com.autojob.modules.jobembedding.vectorstore.JobVectorStore;
import com.autojob.modules.jobembedding.vectorstore.JobVectorStoreException;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.exception.NormalizedJobNotFoundException;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobEmbeddingServiceTest {

    private static final String NORMALIZED_JOB_ID =
            "normalized-001";

    private static final String EMBEDDING_TEXT =
            "query: Title: Senior Java Backend Engineer";

    private static final String EMBEDDING_VERSION =
            "test-model@revision-1|prep-v1|l2";

    private static final Instant NOW =
            Instant.parse("2026-07-21T04:00:00Z");

    @Mock
    private NormalizedJobRepository normalizedJobRepository;

    @Mock
    private JobEmbeddingRepository jobEmbeddingRepository;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private JobVectorStore jobVectorStore;

    private EmbeddingTextHashCalculator hashCalculator;
    private JobEmbeddingPointIdFactory pointIdFactory;
    private EmbeddingProperties embeddingProperties;
    private QdrantProperties qdrantProperties;
    private JobEmbeddingService service;

    @BeforeEach
    void setUp() {
        hashCalculator = new EmbeddingTextHashCalculator();
        pointIdFactory = new JobEmbeddingPointIdFactory();

        embeddingProperties = new EmbeddingProperties();
        embeddingProperties.setExpectedDimension(3);
        embeddingProperties.setExpectedVersion(
                EMBEDDING_VERSION
        );
        embeddingProperties.setResponseTimeout(
                Duration.ofSeconds(30)
        );
        embeddingProperties.setMaxErrorLength(1_000);

        qdrantProperties = new QdrantProperties();
        qdrantProperties.setCollection("job_vectors_v1");
        qdrantProperties.setDimension(3);
        qdrantProperties.setDistance("Cosine");

        service = new JobEmbeddingService(
                normalizedJobRepository,
                jobEmbeddingRepository,
                embeddingClient,
                jobVectorStore,
                hashCalculator,
                pointIdFactory,
                embeddingProperties,
                qdrantProperties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldCreateProcessingThenReady() {
        NormalizedJob job = normalizedJob(EMBEDDING_TEXT);
        EmbeddingResponse response = validResponse(
                EMBEDDING_TEXT
        );

        when(normalizedJobRepository.findById(
                NORMALIZED_JOB_ID
        )).thenReturn(Optional.of(job));

        when(
                jobEmbeddingRepository
                        .findByNormalizedJobIdAndEmbeddingVersion(
                                NORMALIZED_JOB_ID,
                                EMBEDDING_VERSION
                        )
        ).thenReturn(Optional.empty());

        when(jobEmbeddingRepository.insert(
                any(JobEmbedding.class)
        )).thenAnswer(invocation -> {
            JobEmbedding processing =
                    invocation.getArgument(0);

            assertThat(processing.getStatus())
                    .isEqualTo(
                            JobEmbeddingStatus.PROCESSING
                    );

            processing.setId("embedding-001");
            return processing;
        });

        when(embeddingClient.embed(EMBEDDING_TEXT))
                .thenReturn(response);

        when(jobEmbeddingRepository.save(
                any(JobEmbedding.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        JobEmbedding result = service.embed(
                NORMALIZED_JOB_ID
        );

        assertThat(result.getStatus())
                .isEqualTo(JobEmbeddingStatus.READY);

        assertThat(result.getModelName())
                .isEqualTo("test-model");

        assertThat(result.getModelRevision())
                .isEqualTo("revision-1");

        assertThat(result.getEmbeddingVersion())
                .isEqualTo(EMBEDDING_VERSION);

        assertThat(result.getTextHash())
                .isEqualTo(
                        hashCalculator.calculate(
                                EMBEDDING_TEXT
                        )
                );

        assertThat(result.getDimension()).isEqualTo(3);
        assertThat(result.getNormalized()).isTrue();
        assertThat(result.getEmbeddedAt()).isEqualTo(NOW);
        assertThat(result.getLastError()).isNull();

        verify(jobVectorStore).ensureCollection();

        ArgumentCaptor<JobVectorPoint> pointCaptor =
                ArgumentCaptor.forClass(
                        JobVectorPoint.class
                );

        verify(jobVectorStore).upsert(
                pointCaptor.capture()
        );

        JobVectorPoint point = pointCaptor.getValue();

        assertThat(point.normalizedJobId())
                .isEqualTo(NORMALIZED_JOB_ID);

        assertThat(point.pointId())
                .isEqualTo(
                        pointIdFactory.create(
                                NORMALIZED_JOB_ID,
                                EMBEDDING_VERSION
                        )
                );

        assertThat(point.vector())
                .containsExactly(1.0, 0.0, 0.0);
    }

    @Test
    void shouldSkipReadyUnchangedEmbedding() {
        String textHash = hashCalculator.calculate(
                EMBEDDING_TEXT
        );

        String pointId = pointIdFactory.create(
                NORMALIZED_JOB_ID,
                EMBEDDING_VERSION
        );

        JobEmbedding existing = JobEmbedding.builder()
                .id("embedding-001")
                .normalizedJobId(NORMALIZED_JOB_ID)
                .embeddingVersion(EMBEDDING_VERSION)
                .textHash(textHash)
                .qdrantPointId(pointId)
                .status(JobEmbeddingStatus.READY)
                .embeddedAt(NOW.minusSeconds(60))
                .updatedAt(NOW.minusSeconds(60))
                .build();

        when(normalizedJobRepository.findById(
                NORMALIZED_JOB_ID
        )).thenReturn(
                Optional.of(normalizedJob(EMBEDDING_TEXT))
        );

        when(
                jobEmbeddingRepository
                        .findByNormalizedJobIdAndEmbeddingVersion(
                                NORMALIZED_JOB_ID,
                                EMBEDDING_VERSION
                        )
        ).thenReturn(Optional.of(existing));

        when(jobVectorStore.pointExists(pointId))
                .thenReturn(true);

        JobEmbedding result = service.embed(
                NORMALIZED_JOB_ID
        );

        assertThat(result).isSameAs(existing);
        assertThat(result.getEmbeddedAt())
                .isEqualTo(NOW.minusSeconds(60));

        verifyNoInteractions(embeddingClient);

        verify(jobVectorStore, never())
                .ensureCollection();

        verify(jobVectorStore, never())
                .upsert(any(JobVectorPoint.class));

        verify(jobEmbeddingRepository, never())
                .save(any(JobEmbedding.class));
    }

    @Test
    void shouldEmbedAgainWhenTextChangesUsingSamePointId() {
        String changedText =
                EMBEDDING_TEXT + "\nSkills: Java, Kafka";

        String pointId = pointIdFactory.create(
                NORMALIZED_JOB_ID,
                EMBEDDING_VERSION
        );

        JobEmbedding existing = JobEmbedding.builder()
                .id("embedding-001")
                .normalizedJobId(NORMALIZED_JOB_ID)
                .embeddingVersion(EMBEDDING_VERSION)
                .textHash("a".repeat(64))
                .qdrantPointId(pointId)
                .status(JobEmbeddingStatus.READY)
                .embeddedAt(NOW.minusSeconds(60))
                .updatedAt(NOW.minusSeconds(60))
                .build();

        when(normalizedJobRepository.findById(
                NORMALIZED_JOB_ID
        )).thenReturn(
                Optional.of(normalizedJob(changedText))
        );

        when(
                jobEmbeddingRepository
                        .findByNormalizedJobIdAndEmbeddingVersion(
                                NORMALIZED_JOB_ID,
                                EMBEDDING_VERSION
                        )
        ).thenReturn(Optional.of(existing));

        when(jobEmbeddingRepository.save(
                any(JobEmbedding.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        when(embeddingClient.embed(changedText))
                .thenReturn(validResponse(changedText));

        JobEmbedding result = service.embed(
                NORMALIZED_JOB_ID
        );

        assertThat(result.getStatus())
                .isEqualTo(JobEmbeddingStatus.READY);

        assertThat(result.getTextHash())
                .isEqualTo(
                        hashCalculator.calculate(changedText)
                );

        assertThat(result.getQdrantPointId())
                .isEqualTo(pointId);

        ArgumentCaptor<JobVectorPoint> pointCaptor =
                ArgumentCaptor.forClass(
                        JobVectorPoint.class
                );

        verify(jobVectorStore).upsert(
                pointCaptor.capture()
        );

        assertThat(pointCaptor.getValue().pointId())
                .isEqualTo(pointId);
    }

    @Test
    void shouldRetryPreviousFailedEmbedding() {
        JobEmbedding existing = JobEmbedding.builder()
                .id("embedding-001")
                .normalizedJobId(NORMALIZED_JOB_ID)
                .embeddingVersion(EMBEDDING_VERSION)
                .textHash(
                        hashCalculator.calculate(
                                EMBEDDING_TEXT
                        )
                )
                .status(JobEmbeddingStatus.FAILED)
                .lastError("previous failure")
                .updatedAt(NOW.minusSeconds(120))
                .build();

        when(normalizedJobRepository.findById(
                NORMALIZED_JOB_ID
        )).thenReturn(
                Optional.of(normalizedJob(EMBEDDING_TEXT))
        );

        when(
                jobEmbeddingRepository
                        .findByNormalizedJobIdAndEmbeddingVersion(
                                NORMALIZED_JOB_ID,
                                EMBEDDING_VERSION
                        )
        ).thenReturn(Optional.of(existing));

        when(jobEmbeddingRepository.save(
                any(JobEmbedding.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        when(embeddingClient.embed(EMBEDDING_TEXT))
                .thenReturn(validResponse(EMBEDDING_TEXT));

        JobEmbedding result = service.embed(
                NORMALIZED_JOB_ID
        );

        assertThat(result.getStatus())
                .isEqualTo(JobEmbeddingStatus.READY);

        assertThat(result.getLastError()).isNull();

        verify(embeddingClient).embed(EMBEDDING_TEXT);
        verify(jobVectorStore).upsert(
                any(JobVectorPoint.class)
        );
    }

    @Test
    void shouldForceRebuildReadyEmbedding() {
        String textHash = hashCalculator.calculate(
                EMBEDDING_TEXT
        );

        JobEmbedding existing = JobEmbedding.builder()
                .id("embedding-001")
                .normalizedJobId(NORMALIZED_JOB_ID)
                .embeddingVersion(EMBEDDING_VERSION)
                .textHash(textHash)
                .qdrantPointId(
                        pointIdFactory.create(
                                NORMALIZED_JOB_ID,
                                EMBEDDING_VERSION
                        )
                )
                .status(JobEmbeddingStatus.READY)
                .embeddedAt(NOW.minusSeconds(60))
                .updatedAt(NOW.minusSeconds(60))
                .build();

        when(normalizedJobRepository.findById(
                NORMALIZED_JOB_ID
        )).thenReturn(
                Optional.of(normalizedJob(EMBEDDING_TEXT))
        );

        when(
                jobEmbeddingRepository
                        .findByNormalizedJobIdAndEmbeddingVersion(
                                NORMALIZED_JOB_ID,
                                EMBEDDING_VERSION
                        )
        ).thenReturn(Optional.of(existing));

        when(jobEmbeddingRepository.save(
                any(JobEmbedding.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        when(embeddingClient.embed(EMBEDDING_TEXT))
                .thenReturn(validResponse(EMBEDDING_TEXT));

        JobEmbedding result = service.embed(
                NORMALIZED_JOB_ID,
                true
        );

        assertThat(result.getStatus())
                .isEqualTo(JobEmbeddingStatus.READY);

        assertThat(result.getEmbeddedAt()).isEqualTo(NOW);

        verify(embeddingClient).embed(EMBEDDING_TEXT);

        verify(jobVectorStore).upsert(
                any(JobVectorPoint.class)
        );
    }

    @Test
    void shouldMarkFailedWhenEmbeddingClientFails() {
        prepareNewEmbedding();

        when(embeddingClient.embed(EMBEDDING_TEXT))
                .thenThrow(
                        new EmbeddingClientException(
                                "service unavailable"
                        )
                );

        when(jobEmbeddingRepository.save(
                any(JobEmbedding.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        assertThatThrownBy(
                () -> service.embed(NORMALIZED_JOB_ID)
        )
                .isInstanceOf(
                        JobEmbeddingProcessingException.class
                )
                .hasMessageContaining(
                        "service unavailable"
                );

        ArgumentCaptor<JobEmbedding> captor =
                ArgumentCaptor.forClass(
                        JobEmbedding.class
                );

        verify(jobEmbeddingRepository).save(
                captor.capture()
        );

        assertThat(captor.getValue().getStatus())
                .isEqualTo(JobEmbeddingStatus.FAILED);

        assertThat(captor.getValue().getLastError())
                .contains("EmbeddingClientException")
                .contains("service unavailable");

        verify(jobVectorStore, never())
                .upsert(any(JobVectorPoint.class));
    }

    @Test
    void shouldMarkFailedWhenQdrantFails() {
        prepareNewEmbedding();

        when(embeddingClient.embed(EMBEDDING_TEXT))
                .thenReturn(validResponse(EMBEDDING_TEXT));

        doThrow(
                new JobVectorStoreException(
                        "qdrant unavailable"
                )
        ).when(jobVectorStore).ensureCollection();

        when(jobEmbeddingRepository.save(
                any(JobEmbedding.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        assertThatThrownBy(
                () -> service.embed(NORMALIZED_JOB_ID)
        )
                .isInstanceOf(
                        JobEmbeddingProcessingException.class
                )
                .hasMessageContaining(
                        "qdrant unavailable"
                );

        ArgumentCaptor<JobEmbedding> captor =
                ArgumentCaptor.forClass(
                        JobEmbedding.class
                );

        verify(jobEmbeddingRepository).save(
                captor.capture()
        );

        assertThat(captor.getValue().getStatus())
                .isEqualTo(JobEmbeddingStatus.FAILED);

        verify(jobVectorStore, never())
                .upsert(any(JobVectorPoint.class));
    }

    @Test
    void shouldRejectMissingNormalizedJob() {
        when(normalizedJobRepository.findById(
                NORMALIZED_JOB_ID
        )).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> service.embed(NORMALIZED_JOB_ID)
        ).isInstanceOf(
                NormalizedJobNotFoundException.class
        );

        verifyNoInteractions(
                jobEmbeddingRepository,
                embeddingClient,
                jobVectorStore
        );
    }

    @Test
    void shouldPersistFailedWhenEmbeddingTextIsBlank() {
        when(normalizedJobRepository.findById(
                NORMALIZED_JOB_ID
        )).thenReturn(
                Optional.of(normalizedJob("   "))
        );

        when(jobEmbeddingRepository.save(
                any(JobEmbedding.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        assertThatThrownBy(
                () -> service.embed(NORMALIZED_JOB_ID)
        )
                .isInstanceOf(
                        JobEmbeddingProcessingException.class
                )
                .hasMessageContaining(
                        "embeddingText must not be blank"
                );

        ArgumentCaptor<JobEmbedding> captor =
                ArgumentCaptor.forClass(
                        JobEmbedding.class
                );

        verify(jobEmbeddingRepository).save(
                captor.capture()
        );

        assertThat(captor.getValue().getStatus())
                .isEqualTo(JobEmbeddingStatus.FAILED);

        verifyNoInteractions(embeddingClient);
    }

    @Test
    void shouldRejectWrongResponseDimension() {
        prepareNewEmbedding();

        EmbeddingResponse invalid = new EmbeddingResponse(
                List.of(1.0, 0.0, 0.0, 0.0),
                4,
                "test-model",
                "revision-1",
                EMBEDDING_VERSION,
                hashCalculator.calculate(EMBEDDING_TEXT),
                true
        );

        when(embeddingClient.embed(EMBEDDING_TEXT))
                .thenReturn(invalid);

        when(jobEmbeddingRepository.save(
                any(JobEmbedding.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        assertThatThrownBy(
                () -> service.embed(NORMALIZED_JOB_ID)
        )
                .isInstanceOf(
                        JobEmbeddingProcessingException.class
                )
                .hasMessageContaining(
                        "dimension mismatch"
                );

        verify(jobVectorStore, never())
                .upsert(any(JobVectorPoint.class));
    }

    @Test
    void shouldRejectResponseTextHashMismatch() {
        prepareNewEmbedding();

        EmbeddingResponse invalid = new EmbeddingResponse(
                List.of(1.0, 0.0, 0.0),
                3,
                "test-model",
                "revision-1",
                EMBEDDING_VERSION,
                "a".repeat(64),
                true
        );

        when(embeddingClient.embed(EMBEDDING_TEXT))
                .thenReturn(invalid);

        when(jobEmbeddingRepository.save(
                any(JobEmbedding.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        assertThatThrownBy(
                () -> service.embed(NORMALIZED_JOB_ID)
        )
                .isInstanceOf(
                        JobEmbeddingProcessingException.class
                )
                .hasMessageContaining(
                        "text hash mismatch"
                );

        verify(jobVectorStore, never())
                .upsert(any(JobVectorPoint.class));
    }

    @Test
    void shouldRejectNormalizedFalse() {
        prepareNewEmbedding();

        EmbeddingResponse invalid = new EmbeddingResponse(
                List.of(1.0, 0.0, 0.0),
                3,
                "test-model",
                "revision-1",
                EMBEDDING_VERSION,
                hashCalculator.calculate(EMBEDDING_TEXT),
                false
        );

        when(embeddingClient.embed(EMBEDDING_TEXT))
                .thenReturn(invalid);

        when(jobEmbeddingRepository.save(
                any(JobEmbedding.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );

        assertThatThrownBy(
                () -> service.embed(NORMALIZED_JOB_ID)
        )
                .isInstanceOf(
                        JobEmbeddingProcessingException.class
                )
                .hasMessageContaining(
                        "not normalized"
                );

        verify(jobVectorStore, never())
                .upsert(any(JobVectorPoint.class));
    }

    @Test
    void shouldRecoverFromDuplicateKeyRaceBySkippingReadyRecord() {
        String textHash = hashCalculator.calculate(
                EMBEDDING_TEXT
        );

        String pointId = pointIdFactory.create(
                NORMALIZED_JOB_ID,
                EMBEDDING_VERSION
        );

        JobEmbedding concurrent = JobEmbedding.builder()
                .id("embedding-concurrent")
                .normalizedJobId(NORMALIZED_JOB_ID)
                .embeddingVersion(EMBEDDING_VERSION)
                .textHash(textHash)
                .qdrantPointId(pointId)
                .status(JobEmbeddingStatus.READY)
                .embeddedAt(NOW.minusSeconds(30))
                .updatedAt(NOW.minusSeconds(30))
                .build();

        when(normalizedJobRepository.findById(
                NORMALIZED_JOB_ID
        )).thenReturn(
                Optional.of(normalizedJob(EMBEDDING_TEXT))
        );

        when(
                jobEmbeddingRepository
                        .findByNormalizedJobIdAndEmbeddingVersion(
                                NORMALIZED_JOB_ID,
                                EMBEDDING_VERSION
                        )
        ).thenReturn(
                Optional.empty(),
                Optional.of(concurrent)
        );

        when(jobEmbeddingRepository.insert(
                any(JobEmbedding.class)
        )).thenThrow(
                new DuplicateKeyException("duplicate")
        );

        when(jobVectorStore.pointExists(pointId))
                .thenReturn(true);

        JobEmbedding result = service.embed(
                NORMALIZED_JOB_ID
        );

        assertThat(result).isSameAs(concurrent);

        verifyNoInteractions(embeddingClient);

        verify(jobVectorStore, never())
                .upsert(any(JobVectorPoint.class));
    }

    private void prepareNewEmbedding() {
        when(normalizedJobRepository.findById(
                NORMALIZED_JOB_ID
        )).thenReturn(
                Optional.of(normalizedJob(EMBEDDING_TEXT))
        );

        when(
                jobEmbeddingRepository
                        .findByNormalizedJobIdAndEmbeddingVersion(
                                NORMALIZED_JOB_ID,
                                EMBEDDING_VERSION
                        )
        ).thenReturn(Optional.empty());

        when(jobEmbeddingRepository.insert(
                any(JobEmbedding.class)
        )).thenAnswer(
                invocation -> invocation.getArgument(0)
        );
    }

    private NormalizedJob normalizedJob(
            String embeddingText
    ) {
        return NormalizedJob.builder()
                .id(NORMALIZED_JOB_ID)
                .rawJobId("raw-001")
                .sourceCode("MOCK")
                .normalizationVersion("rule-v1")
                .embeddingText(embeddingText)
                .build();
    }

    private EmbeddingResponse validResponse(String text) {
        return new EmbeddingResponse(
                List.of(1.0, 0.0, 0.0),
                3,
                "test-model",
                "revision-1",
                EMBEDDING_VERSION,
                hashCalculator.calculate(text),
                true
        );
    }
}