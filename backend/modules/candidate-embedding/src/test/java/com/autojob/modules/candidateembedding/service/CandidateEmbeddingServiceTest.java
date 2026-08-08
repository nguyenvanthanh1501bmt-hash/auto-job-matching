package com.autojob.modules.candidateembedding.service;

import com.autojob.common.embedding.client.EmbeddingClient;
import com.autojob.common.embedding.client.EmbeddingClientException;
import com.autojob.common.embedding.client.dto.EmbeddingResponse;
import com.autojob.common.embedding.config.EmbeddingProperties;
import com.autojob.common.embedding.service.EmbeddingTextHashCalculator;
import com.autojob.modules.candidateembedding.api.CandidateEmbeddingController;
import com.autojob.modules.candidateembedding.config.CandidateEmbeddingProperties;
import com.autojob.modules.candidateembedding.domain.CandidateEmbedding;
import com.autojob.modules.candidateembedding.domain.CandidateEmbeddingStatus;
import com.autojob.modules.candidateembedding.repository.CandidateEmbeddingRepository;
import com.autojob.modules.candidateembedding.text.CandidateEmbeddingTextBuilder;
import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateEmbeddingServiceTest {

    private static final String PROFILE_ID = "profile-001";
    private static final String RAW_CV_ID = "raw-cv-001";
    private static final String TEXT_VERSION = "candidate-text-v1";
    private static final String VERSION = "test-model@rev-1|prep-v1|l2";

    private static final String TEXT =
            "query: Target roles: Senior Java Backend Engineer";

    private static final Instant NOW =
            Instant.parse("2026-08-08T03:00:00Z");

    @Mock
    private CandidateProfileRepository candidateProfileRepository;

    @Mock
    private CandidateEmbeddingRepository candidateEmbeddingRepository;

    @Mock
    private CandidateEmbeddingTextBuilder textBuilder;

    @Mock
    private EmbeddingClient embeddingClient;

    private EmbeddingTextHashCalculator hashCalculator;
    private EmbeddingProperties embeddingProperties;
    private CandidateEmbeddingProperties candidateProperties;
    private CandidateEmbeddingService service;

    @BeforeEach
    void setUp() {
        hashCalculator =
                new EmbeddingTextHashCalculator();

        embeddingProperties =
                new EmbeddingProperties();

        embeddingProperties.setExpectedDimension(3);
        embeddingProperties.setExpectedVersion(VERSION);
        embeddingProperties.setResponseTimeout(
                Duration.ofSeconds(30)
        );
        embeddingProperties.setMaxErrorLength(
                1_000
        );

        candidateProperties =
                new CandidateEmbeddingProperties();

        candidateProperties.setTextVersion(
                TEXT_VERSION
        );

        service =
                new CandidateEmbeddingService(
                        candidateProfileRepository,
                        candidateEmbeddingRepository,
                        textBuilder,
                        embeddingClient,
                        hashCalculator,
                        embeddingProperties,
                        candidateProperties,
                        Clock.fixed(
                                NOW,
                                ZoneOffset.UTC
                        )
                );

        lenient()
                .when(
                        candidateEmbeddingRepository.insert(
                                any(CandidateEmbedding.class)
                        )
                )
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );

        lenient()
                .when(
                        candidateEmbeddingRepository.save(
                                any(CandidateEmbedding.class)
                        )
                )
                .thenAnswer(
                        invocation ->
                                invocation.getArgument(0)
                );
    }

    @Test
    void shouldReturnNotFoundWhenCandidateProfileDoesNotExist() {
        when(
                candidateProfileRepository.findById(
                        PROFILE_ID
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () -> service.embed(PROFILE_ID)
        )
                .isInstanceOf(
                        CandidateEmbeddingNotFoundException.class
                )
                .hasMessageContaining(
                        PROFILE_ID
                );

        verifyNoInteractions(
                textBuilder,
                embeddingClient
        );
    }

    @Test
    void shouldRejectBlankEmbeddingText() {
        stubProfileAndText("   ");

        assertThatThrownBy(
                () -> service.embed(PROFILE_ID)
        )
                .isInstanceOf(
                        CandidateEmbeddingProcessingException.class
                )
                .hasMessageContaining(
                        "must not be blank"
                );

        verifyNoInteractions(
                embeddingClient
        );
    }

    @Test
    void shouldCreateProcessingThenReadyForNewEmbedding() {
        stubProfileAndText(TEXT);

        when(
                candidateEmbeddingRepository
                        .findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
                                PROFILE_ID,
                                VERSION,
                                TEXT_VERSION
                        )
        ).thenReturn(
                Optional.empty()
        );

        when(
                embeddingClient.embed(TEXT)
        ).thenReturn(
                validResponse(TEXT)
        );

        CandidateEmbedding result =
                service.embed(PROFILE_ID);

        assertThat(
                result.getStatus()
        ).isEqualTo(
                CandidateEmbeddingStatus.READY
        );

        verify(
                candidateEmbeddingRepository
        ).insert(
                any(CandidateEmbedding.class)
        );

        verify(
                embeddingClient
        ).embed(TEXT);
    }

    @Test
    void shouldPersistVector() {
        stubSuccessfulNewEmbedding(TEXT);

        ArgumentCaptor<CandidateEmbedding> captor =
                ArgumentCaptor.forClass(
                        CandidateEmbedding.class
                );

        service.embed(PROFILE_ID);

        verify(
                candidateEmbeddingRepository
        ).save(
                captor.capture()
        );

        assertThat(
                captor
                        .getValue()
                        .getVector()
        ).containsExactly(
                1.0,
                0.0,
                0.0
        );
    }

    @Test
    void shouldPersistEmbeddingMetadata() {
        stubSuccessfulNewEmbedding(TEXT);

        ArgumentCaptor<CandidateEmbedding> captor =
                ArgumentCaptor.forClass(
                        CandidateEmbedding.class
                );

        service.embed(PROFILE_ID);

        verify(
                candidateEmbeddingRepository
        ).save(
                captor.capture()
        );

        CandidateEmbedding saved =
                captor.getValue();

        assertThat(
                saved.getModelName()
        ).isEqualTo(
                "test-model"
        );

        assertThat(
                saved.getModelRevision()
        ).isEqualTo(
                "rev-1"
        );

        assertThat(
                saved.getEmbeddingVersion()
        ).isEqualTo(
                VERSION
        );

        assertThat(
                saved.getDimension()
        ).isEqualTo(
                3
        );

        assertThat(
                saved.getNormalized()
        ).isTrue();

        assertThat(
                saved.getEmbeddedAt()
        ).isEqualTo(
                NOW
        );
    }

    @Test
    void shouldSkipReadyUnchangedEmbeddingWhenForceFalse() {
        stubProfileAndText(TEXT);

        CandidateEmbedding existing =
                readyEmbedding(TEXT);

        when(
                candidateEmbeddingRepository
                        .findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
                                PROFILE_ID,
                                VERSION,
                                TEXT_VERSION
                        )
        ).thenReturn(
                Optional.of(existing)
        );

        CandidateEmbedding result =
                service.embed(
                        PROFILE_ID,
                        false
                );

        assertThat(result)
                .isSameAs(existing);

        verifyNoInteractions(
                embeddingClient
        );

        verify(
                candidateEmbeddingRepository,
                never()
        ).save(any());
    }

    @Test
    void shouldRebuildReadyEmbeddingWhenForceTrue() {
        stubProfileAndText(TEXT);

        CandidateEmbedding existing =
                readyEmbedding(TEXT);

        when(
                candidateEmbeddingRepository
                        .findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
                                PROFILE_ID,
                                VERSION,
                                TEXT_VERSION
                        )
        ).thenReturn(
                Optional.of(existing)
        );

        when(
                embeddingClient.embed(TEXT)
        ).thenReturn(
                validResponse(TEXT)
        );

        CandidateEmbedding result =
                service.embed(
                        PROFILE_ID,
                        true
                );

        assertThat(
                result.getStatus()
        ).isEqualTo(
                CandidateEmbeddingStatus.READY
        );

        verify(
                embeddingClient
        ).embed(TEXT);
    }

    @Test
    void shouldReembedWhenTextChanges() {
        String changedText =
                "query: Skills: Java, Kafka, MongoDB";

        stubProfileAndText(
                changedText
        );

        CandidateEmbedding existing =
                readyEmbedding(TEXT);

        when(
                candidateEmbeddingRepository
                        .findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
                                PROFILE_ID,
                                VERSION,
                                TEXT_VERSION
                        )
        ).thenReturn(
                Optional.of(existing)
        );

        when(
                embeddingClient.embed(
                        changedText
                )
        ).thenReturn(
                validResponse(
                        changedText
                )
        );

        CandidateEmbedding result =
                service.embed(
                        PROFILE_ID,
                        false
                );

        assertThat(
                result.getTextHash()
        ).isEqualTo(
                hashCalculator.calculate(
                        changedText
                )
        );

        verify(
                embeddingClient
        ).embed(
                changedText
        );
    }

    @Test
    void shouldMarkFailedWhenEmbeddingVersionMismatches() {
        stubProcessing(TEXT);

        EmbeddingResponse invalid =
                new EmbeddingResponse(
                        List.of(
                                1.0,
                                0.0,
                                0.0
                        ),
                        3,
                        "test-model",
                        "rev-2",
                        "other-model@rev-2|prep-v1|l2",
                        hashCalculator.calculate(TEXT),
                        true
                );

        when(
                embeddingClient.embed(TEXT)
        ).thenReturn(
                invalid
        );

        assertFailedAndPersisted();
    }

    @Test
    void shouldMarkFailedWhenTextHashMismatches() {
        stubProcessing(TEXT);

        EmbeddingResponse invalid =
                new EmbeddingResponse(
                        List.of(
                                1.0,
                                0.0,
                                0.0
                        ),
                        3,
                        "test-model",
                        "rev-1",
                        VERSION,
                        "b".repeat(64),
                        true
                );

        when(
                embeddingClient.embed(TEXT)
        ).thenReturn(
                invalid
        );

        assertFailedAndPersisted();
    }

    @Test
    void shouldMarkFailedWhenDimensionIsInvalid() {
        stubProcessing(TEXT);

        EmbeddingResponse invalid =
                new EmbeddingResponse(
                        List.of(
                                1.0,
                                0.0
                        ),
                        2,
                        "test-model",
                        "rev-1",
                        VERSION,
                        hashCalculator.calculate(TEXT),
                        true
                );

        when(
                embeddingClient.embed(TEXT)
        ).thenReturn(
                invalid
        );

        assertFailedAndPersisted();
    }

    @Test
    void shouldMarkFailedWhenEmbeddingClientFails() {
        stubProcessing(TEXT);

        when(
                embeddingClient.embed(TEXT)
        ).thenThrow(
                new EmbeddingClientException(
                        "embedding dependency unavailable"
                )
        );

        assertFailedAndPersisted();
    }

    @Test
    void shouldRecoverFromDuplicateKeyDuringConcurrentInsert() {
        stubProfileAndText(TEXT);

        CandidateEmbedding concurrent =
                processingEmbedding(TEXT);

        concurrent.setUpdatedAt(
                NOW.minus(
                        Duration.ofMinutes(2)
                )
        );

        when(
                candidateEmbeddingRepository
                        .findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
                                PROFILE_ID,
                                VERSION,
                                TEXT_VERSION
                        )
        ).thenReturn(
                Optional.empty(),
                Optional.of(concurrent)
        );

        when(
                candidateEmbeddingRepository.insert(
                        any(CandidateEmbedding.class)
                )
        ).thenThrow(
                new DuplicateKeyException(
                        "duplicate"
                )
        );

        when(
                embeddingClient.embed(TEXT)
        ).thenReturn(
                validResponse(TEXT)
        );

        CandidateEmbedding result =
                service.embed(PROFILE_ID);

        assertThat(
                result.getStatus()
        ).isEqualTo(
                CandidateEmbeddingStatus.READY
        );

        verify(
                embeddingClient
        ).embed(TEXT);
    }

    @Test
    void shouldSkipFreshProcessingWithoutDuplicateRequest() {
        stubProfileAndText(TEXT);

        CandidateEmbedding processing =
                processingEmbedding(TEXT);

        processing.setUpdatedAt(
                NOW.minusSeconds(30)
        );

        when(
                candidateEmbeddingRepository
                        .findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
                                PROFILE_ID,
                                VERSION,
                                TEXT_VERSION
                        )
        ).thenReturn(
                Optional.of(processing)
        );

        CandidateEmbedding result =
                service.embed(PROFILE_ID);

        assertThat(result)
                .isSameAs(processing);

        assertThat(
                result.getStatus()
        ).isEqualTo(
                CandidateEmbeddingStatus.PROCESSING
        );

        verifyNoInteractions(
                embeddingClient
        );
    }

    @Test
    void shouldRetryStaleProcessing() {
        stubProfileAndText(TEXT);

        CandidateEmbedding processing =
                processingEmbedding(TEXT);

        processing.setUpdatedAt(
                NOW.minusSeconds(61)
        );

        when(
                candidateEmbeddingRepository
                        .findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
                                PROFILE_ID,
                                VERSION,
                                TEXT_VERSION
                        )
        ).thenReturn(
                Optional.of(processing)
        );

        when(
                embeddingClient.embed(TEXT)
        ).thenReturn(
                validResponse(TEXT)
        );

        CandidateEmbedding result =
                service.embed(PROFILE_ID);

        assertThat(
                result.getStatus()
        ).isEqualTo(
                CandidateEmbeddingStatus.READY
        );

        verify(
                embeddingClient
        ).embed(TEXT);
    }

    @Test
    void shouldSanitizeAndLimitLastError() {
        embeddingProperties.setMaxErrorLength(
                100
        );

        stubProcessing(TEXT);

        when(
                embeddingClient.embed(TEXT)
        ).thenThrow(
                new EmbeddingClientException(
                        "Bearer top-secret "
                                + "password=hunter2 "
                                + "token=abc123 "
                                + "x".repeat(300)
                )
        );

        ArgumentCaptor<CandidateEmbedding> captor =
                ArgumentCaptor.forClass(
                        CandidateEmbedding.class
                );

        assertThatThrownBy(
                () -> service.embed(PROFILE_ID)
        ).isInstanceOf(
                CandidateEmbeddingProcessingException.class
        );

        verify(
                candidateEmbeddingRepository
        ).save(
                captor.capture()
        );

        String lastError =
                captor
                        .getValue()
                        .getLastError();

        assertThat(lastError)
                .hasSizeLessThanOrEqualTo(100)
                .contains("[REDACTED]")
                .doesNotContain("top-secret")
                .doesNotContain("hunter2")
                .doesNotContain("abc123");
    }

    @Test
    void shouldReturnLatestEmbedding() {
        CandidateEmbedding latest =
                readyEmbedding(TEXT);

        when(
                candidateEmbeddingRepository
                        .findFirstByCandidateProfileIdOrderByUpdatedAtDesc(
                                PROFILE_ID
                        )
        ).thenReturn(
                Optional.of(latest)
        );

        assertThat(
                service.getLatest(PROFILE_ID)
        ).isSameAs(
                latest
        );
    }

    @Test
    void apiResponseTypeMustNotContainVector() {
        List<String> fields =
                Arrays.stream(
                                CandidateEmbeddingController
                                        .CandidateEmbeddingResponse
                                        .class
                                        .getRecordComponents()
                        )
                        .map(
                                RecordComponent::getName
                        )
                        .toList();

        assertThat(fields)
                .doesNotContain(
                        "vector"
                );
    }

    private void stubSuccessfulNewEmbedding(
            String text
    ) {
        stubProcessing(text);

        when(
                embeddingClient.embed(text)
        ).thenReturn(
                validResponse(text)
        );
    }

    private void stubProcessing(
            String text
    ) {
        stubProfileAndText(text);

        when(
                candidateEmbeddingRepository
                        .findByCandidateProfileIdAndEmbeddingVersionAndTextVersion(
                                PROFILE_ID,
                                VERSION,
                                TEXT_VERSION
                        )
        ).thenReturn(
                Optional.empty()
        );
    }

    private void stubProfileAndText(
            String text
    ) {
        CandidateProfile profile =
                profile();

        when(
                candidateProfileRepository.findById(
                        PROFILE_ID
                )
        ).thenReturn(
                Optional.of(profile)
        );

        when(
                textBuilder.build(profile)
        ).thenReturn(
                text
        );
    }

    private void assertFailedAndPersisted() {
        ArgumentCaptor<CandidateEmbedding> captor =
                ArgumentCaptor.forClass(
                        CandidateEmbedding.class
                );

        assertThatThrownBy(
                () -> service.embed(PROFILE_ID)
        ).isInstanceOf(
                CandidateEmbeddingProcessingException.class
        );

        verify(
                candidateEmbeddingRepository
        ).save(
                captor.capture()
        );

        CandidateEmbedding failed =
                captor.getValue();

        assertThat(
                failed.getStatus()
        ).isEqualTo(
                CandidateEmbeddingStatus.FAILED
        );

        assertThat(
                failed.getLastError()
        ).isNotBlank();

        assertThat(
                failed.getVector()
        ).isNull();
    }

    private CandidateProfile profile() {
        return CandidateProfile
                .builder()
                .id(PROFILE_ID)
                .rawCvId(RAW_CV_ID)
                .parserVersion("rule-v1")
                .build();
    }

    private CandidateEmbedding readyEmbedding(
            String text
    ) {
        CandidateEmbedding embedding =
                processingEmbedding(text);

        embedding.setStatus(
                CandidateEmbeddingStatus.READY
        );

        embedding.setModelName(
                "test-model"
        );

        embedding.setModelRevision(
                "rev-1"
        );

        embedding.setNormalized(
                true
        );

        embedding.setVector(
                List.of(
                        1.0,
                        0.0,
                        0.0
                )
        );

        embedding.setEmbeddedAt(
                NOW.minusSeconds(60)
        );

        return embedding;
    }

    private CandidateEmbedding processingEmbedding(
            String text
    ) {
        return CandidateEmbedding
                .builder()
                .candidateProfileId(
                        PROFILE_ID
                )
                .rawCvId(
                        RAW_CV_ID
                )
                .parserVersion(
                        "rule-v1"
                )
                .textVersion(
                        TEXT_VERSION
                )
                .embeddingVersion(
                        VERSION
                )
                .textHash(
                        hashCalculator.calculate(
                                text
                        )
                )
                .dimension(3)
                .status(
                        CandidateEmbeddingStatus.PROCESSING
                )
                .createdAt(
                        NOW.minus(
                                Duration.ofMinutes(5)
                        )
                )
                .updatedAt(NOW)
                .build();
    }

    private EmbeddingResponse validResponse(
            String text
    ) {
        return new EmbeddingResponse(
                List.of(
                        1.0,
                        0.0,
                        0.0
                ),
                3,
                "test-model",
                "rev-1",
                VERSION,
                hashCalculator.calculate(text),
                true
        );
    }
}