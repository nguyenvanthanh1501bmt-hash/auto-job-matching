package com.autojob.modules.cv.service;

import com.autojob.modules.cv.client.CvParserClient;
import com.autojob.modules.cv.client.CvParserClientException;
import com.autojob.modules.cv.client.CvParserResponseValidationException;
import com.autojob.modules.cv.client.dto.CvParseRequest;
import com.autojob.modules.cv.client.dto.CvParseResponse;
import com.autojob.modules.cv.config.CvParserProperties;
import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.domain.CvProcessingStatus;
import com.autojob.modules.cv.domain.RawCv;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import com.autojob.modules.cv.repository.RawCvRepository;
import com.autojob.modules.cv.repository.RawCvStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvParsingServiceTest {

    private static final String RAW_CV_ID =
            "raw-cv-001";

    private static final String OWNER_USER_ID =
            "user-001";

    private static final String OTHER_USER_ID =
            "user-002";

    private static final Instant NOW = Instant.parse(
            "2026-08-04T04:00:00Z"
    );

    private static final Clock FIXED_CLOCK =
            Clock.fixed(
                    NOW,
                    ZoneOffset.UTC
            );

    @Mock
    private RawCvRepository rawCvRepository;

    @Mock
    private RawCvStatusRepository rawCvStatusRepository;

    @Mock
    private CandidateProfileRepository
            candidateProfileRepository;

    @Mock
    private CvParserClient parserClient;

    @Mock
    private CvParserResponseValidator responseValidator;

    @Mock
    private CandidateProfileMapper profileMapper;

    private CvParserProperties parserProperties;
    private CvParsingService service;

    @BeforeEach
    void setUp() {
        parserProperties =
                new CvParserProperties();

        parserProperties.setExpectedVersion(
                "rule-v1"
        );
        parserProperties.setMaxErrorLength(
                1_000
        );

        service = new CvParsingService(
                rawCvRepository,
                rawCvStatusRepository,
                candidateProfileRepository,
                parserClient,
                responseValidator,
                profileMapper,
                parserProperties,
                FIXED_CLOCK
        );
    }

    @Test
    void shouldRequireAuthentication() {
        assertParsingException(
                () -> service.parse(
                        RAW_CV_ID,
                        null
                ),
                HttpStatus.UNAUTHORIZED,
                "CV_AUTHENTICATION_REQUIRED"
        );

        verifyNoInteractions(
                rawCvRepository,
                rawCvStatusRepository,
                candidateProfileRepository,
                parserClient
        );
    }

    @Test
    void shouldReturnNotFoundWhenRawCvDoesNotExist() {
        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(Optional.empty());

        assertParsingException(
                () -> service.parse(
                        RAW_CV_ID,
                        OWNER_USER_ID
                ),
                HttpStatus.NOT_FOUND,
                "RAW_CV_NOT_FOUND"
        );

        verifyNoInteractions(parserClient);
    }

    @Test
    void shouldRejectOwnershipFailure() {
        RawCv rawCv = rawCv(
                CvProcessingStatus.UPLOADED
        );
        rawCv.setOwnerUserId(OTHER_USER_ID);

        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(Optional.of(rawCv));

        assertParsingException(
                () -> service.parse(
                        RAW_CV_ID,
                        OWNER_USER_ID
                ),
                HttpStatus.FORBIDDEN,
                "CV_ACCESS_DENIED"
        );

        verifyNoInteractions(
                candidateProfileRepository,
                rawCvStatusRepository,
                parserClient
        );
    }

    @Test
    void shouldParseAndSaveCandidateProfile() {
        RawCv rawCv = rawCv(
                CvProcessingStatus.UPLOADED
        );

        CvParseResponse parserResponse =
                parserResponse();

        CandidateProfile mappedProfile =
                currentProfile();

        stubSuccessfulParse(
                rawCv,
                null,
                parserResponse,
                mappedProfile
        );

        CandidateProfile result = service.parse(
                RAW_CV_ID,
                OWNER_USER_ID
        );

        assertThat(result)
                .isSameAs(mappedProfile);

        ArgumentCaptor<CvParseRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        CvParseRequest.class
                );

        verify(parserClient).parse(
                requestCaptor.capture()
        );

        CvParseRequest request =
                requestCaptor.getValue();

        assertThat(request.rawCvId())
                .isEqualTo(RAW_CV_ID);
        assertThat(request.bucket())
                .isEqualTo("autojob-cvs");
        assertThat(request.objectKey())
                .isEqualTo(
                        "raw/2026/08/04/raw-cv-001/candidate.pdf"
                );
        assertThat(request.originalFilename())
                .isEqualTo("candidate.pdf");
        assertThat(request.contentType())
                .isEqualTo("application/pdf");

        verify(rawCvStatusRepository)
                .acquireForParsing(
                        RAW_CV_ID,
                        OWNER_USER_ID,
                        false
                );

        verify(candidateProfileRepository)
                .save(mappedProfile);

        verify(rawCvStatusRepository)
                .markParsed(
                        RAW_CV_ID,
                        OWNER_USER_ID
                );

        verify(
                rawCvStatusRepository,
                never()
        ).markFailed(
                eq(RAW_CV_ID),
                eq(OWNER_USER_ID),
                any(String.class)
        );
    }

    @Test
    void shouldReturnExistingProfileIdempotently() {
        RawCv rawCv = rawCv(
                CvProcessingStatus.PARSED
        );

        CandidateProfile existing =
                currentProfile();

        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(Optional.of(rawCv));

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(Optional.of(existing));

        CandidateProfile result = service.parse(
                RAW_CV_ID,
                OWNER_USER_ID
        );

        assertThat(result).isSameAs(existing);

        verifyNoInteractions(
                parserClient,
                responseValidator,
                profileMapper,
                rawCvStatusRepository
        );

        verify(
                candidateProfileRepository,
                never()
        ).save(any(CandidateProfile.class));
    }

    @Test
    void shouldRecoverPartialSaveWithoutCallingParserAgain() {
        RawCv failedRawCv = rawCv(
                CvProcessingStatus.FAILED
        );

        failedRawCv.setLastError(
                "RAW_CV_STATUS_UPDATE_FAILED"
        );

        CandidateProfile existing =
                currentProfile();

        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(
                Optional.of(failedRawCv)
        );

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(Optional.of(existing));

        when(
                rawCvStatusRepository
                        .acquireForParsing(
                                RAW_CV_ID,
                                OWNER_USER_ID,
                                false
                        )
        ).thenReturn(true);

        when(
                rawCvStatusRepository.markParsed(
                        RAW_CV_ID,
                        OWNER_USER_ID
                )
        ).thenReturn(true);

        CandidateProfile result = service.parse(
                RAW_CV_ID,
                OWNER_USER_ID
        );

        assertThat(result).isSameAs(existing);

        verifyNoInteractions(
                parserClient,
                responseValidator,
                profileMapper
        );

        verify(
                candidateProfileRepository,
                never()
        ).save(any(CandidateProfile.class));
    }

    @Test
    void shouldRetryFromFailedStatus() {
        RawCv rawCv = rawCv(
                CvProcessingStatus.FAILED
        );

        CvParseResponse parserResponse =
                parserResponse();

        CandidateProfile mappedProfile =
                currentProfile();

        stubSuccessfulParse(
                rawCv,
                null,
                parserResponse,
                mappedProfile
        );

        CandidateProfile result = service.parse(
                RAW_CV_ID,
                OWNER_USER_ID
        );

        assertThat(result).isSameAs(mappedProfile);

        verify(rawCvStatusRepository)
                .acquireForParsing(
                        RAW_CV_ID,
                        OWNER_USER_ID,
                        false
                );

        verify(parserClient)
                .parse(any(CvParseRequest.class));
    }

    @Test
    void shouldRejectConcurrentParseRequest() {
        RawCv initiallyUploaded = rawCv(
                CvProcessingStatus.UPLOADED
        );

        RawCv concurrentlyParsing = rawCv(
                CvProcessingStatus.PARSING
        );

        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(
                Optional.of(initiallyUploaded),
                Optional.of(concurrentlyParsing)
        );

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(
                Optional.empty(),
                Optional.empty()
        );

        when(
                rawCvStatusRepository
                        .acquireForParsing(
                                RAW_CV_ID,
                                OWNER_USER_ID,
                                false
                        )
        ).thenReturn(false);

        assertParsingException(
                () -> service.parse(
                        RAW_CV_ID,
                        OWNER_USER_ID
                ),
                HttpStatus.CONFLICT,
                "CV_PARSE_IN_PROGRESS"
        );

        verifyNoInteractions(parserClient);
    }

    @Test
    void shouldRejectAlreadyParsingStatus() {
        RawCv parsingRawCv = rawCv(
                CvProcessingStatus.PARSING
        );

        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(
                Optional.of(parsingRawCv),
                Optional.of(parsingRawCv)
        );

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(
                Optional.empty(),
                Optional.empty()
        );

        when(
                rawCvStatusRepository
                        .acquireForParsing(
                                RAW_CV_ID,
                                OWNER_USER_ID,
                                false
                        )
        ).thenReturn(false);

        assertParsingException(
                () -> service.parse(
                        RAW_CV_ID,
                        OWNER_USER_ID
                ),
                HttpStatus.CONFLICT,
                "CV_PARSE_IN_PROGRESS"
        );

        verifyNoInteractions(parserClient);
    }

    @Test
    void shouldReparseProfileWithOldParserVersion() {
        RawCv rawCv = rawCv(
                CvProcessingStatus.PARSED
        );

        CandidateProfile oldProfile =
                currentProfile();
        oldProfile.setParserVersion("rule-v0");

        CandidateProfile updatedProfile =
                currentProfile();

        CvParseResponse parserResponse =
                parserResponse();

        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(Optional.of(rawCv));

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(Optional.of(oldProfile));

        when(
                rawCvStatusRepository
                        .acquireForParsing(
                                RAW_CV_ID,
                                OWNER_USER_ID,
                                true
                        )
        ).thenReturn(true);

        when(
                parserClient.parse(
                        any(CvParseRequest.class)
                )
        ).thenReturn(parserResponse);

        when(
                responseValidator.validate(
                        any(CvParseRequest.class),
                        eq(parserResponse)
                )
        ).thenReturn(parserResponse);

        when(
                profileMapper.toDocument(
                        eq(rawCv),
                        eq(parserResponse),
                        eq(oldProfile),
                        eq(NOW)
                )
        ).thenReturn(updatedProfile);

        when(
                candidateProfileRepository.save(
                        updatedProfile
                )
        ).thenReturn(updatedProfile);

        when(
                rawCvStatusRepository.markParsed(
                        RAW_CV_ID,
                        OWNER_USER_ID
                )
        ).thenReturn(true);

        CandidateProfile result = service.parse(
                RAW_CV_ID,
                OWNER_USER_ID
        );

        assertThat(result)
                .isSameAs(updatedProfile);

        verify(rawCvStatusRepository)
                .acquireForParsing(
                        RAW_CV_ID,
                        OWNER_USER_ID,
                        true
                );

        verify(parserClient)
                .parse(any(CvParseRequest.class));
    }

    @Test
    void shouldMarkFailedWhenParserReturnsDomainError() {
        RawCv rawCv = rawCv(
                CvProcessingStatus.UPLOADED
        );

        stubParseAcquisition(rawCv);

        when(
                parserClient.parse(
                        any(CvParseRequest.class)
                )
        ).thenThrow(
                CvParserClientException.httpError(
                        RAW_CV_ID,
                        422,
                        "CV_TEXT_NOT_EXTRACTABLE"
                )
        );

        when(
                rawCvStatusRepository.markFailed(
                        eq(RAW_CV_ID),
                        eq(OWNER_USER_ID),
                        any(String.class)
                )
        ).thenReturn(true);

        assertParsingException(
                () -> service.parse(
                        RAW_CV_ID,
                        OWNER_USER_ID
                ),
                HttpStatus.UNPROCESSABLE_ENTITY,
                "CV_TEXT_NOT_EXTRACTABLE"
        );

        ArgumentCaptor<String> errorCaptor =
                ArgumentCaptor.forClass(
                        String.class
                );

        verify(rawCvStatusRepository)
                .markFailed(
                        eq(RAW_CV_ID),
                        eq(OWNER_USER_ID),
                        errorCaptor.capture()
                );

        assertThat(errorCaptor.getValue())
                .contains(
                        "CV_TEXT_NOT_EXTRACTABLE"
                )
                .doesNotContain(
                        "CV content"
                );
    }

    @Test
    void shouldMarkFailedWhenParserTimesOut() {
        RawCv rawCv = rawCv(
                CvProcessingStatus.UPLOADED
        );

        stubParseAcquisition(rawCv);

        when(
                parserClient.parse(
                        any(CvParseRequest.class)
                )
        ).thenThrow(
                CvParserClientException
                        .responseTimeout(
                                RAW_CV_ID,
                                new RuntimeException(
                                        "private timeout detail"
                                )
                        )
        );

        when(
                rawCvStatusRepository.markFailed(
                        eq(RAW_CV_ID),
                        eq(OWNER_USER_ID),
                        any(String.class)
                )
        ).thenReturn(true);

        assertParsingException(
                () -> service.parse(
                        RAW_CV_ID,
                        OWNER_USER_ID
                ),
                HttpStatus.GATEWAY_TIMEOUT,
                "CV_PARSER_TIMEOUT"
        );

        ArgumentCaptor<String> errorCaptor =
                ArgumentCaptor.forClass(
                        String.class
                );

        verify(rawCvStatusRepository)
                .markFailed(
                        eq(RAW_CV_ID),
                        eq(OWNER_USER_ID),
                        errorCaptor.capture()
                );

        assertThat(errorCaptor.getValue())
                .isEqualTo(
                        "PARSER_RESPONSE_TIMEOUT"
                )
                .doesNotContain(
                        "private timeout detail"
                );
    }

    @Test
    void shouldMarkFailedForMalformedParserResponse() {
        RawCv rawCv = rawCv(
                CvProcessingStatus.UPLOADED
        );

        CvParseResponse parserResponse =
                parserResponse();

        stubParseAcquisition(rawCv);

        when(
                parserClient.parse(
                        any(CvParseRequest.class)
                )
        ).thenReturn(parserResponse);

        when(
                responseValidator.validate(
                        any(CvParseRequest.class),
                        eq(parserResponse)
                )
        ).thenThrow(
                new CvParserResponseValidationException(
                        "profile.workExperiences[0].durationMonths",
                        "must be non-negative"
                )
        );

        when(
                rawCvStatusRepository.markFailed(
                        eq(RAW_CV_ID),
                        eq(OWNER_USER_ID),
                        any(String.class)
                )
        ).thenReturn(true);

        assertParsingException(
                () -> service.parse(
                        RAW_CV_ID,
                        OWNER_USER_ID
                ),
                HttpStatus.BAD_GATEWAY,
                "CV_PARSER_INVALID_RESPONSE"
        );

        ArgumentCaptor<String> errorCaptor =
                ArgumentCaptor.forClass(
                        String.class
                );

        verify(rawCvStatusRepository)
                .markFailed(
                        eq(RAW_CV_ID),
                        eq(OWNER_USER_ID),
                        errorCaptor.capture()
                );

        assertThat(errorCaptor.getValue())
                .contains(
                        "profile.workExperiences[0].durationMonths"
                )
                .doesNotContain(
                        "must be non-negative"
                );
    }

    @Test
    void shouldTruncateSanitizedLastError() {
        parserProperties.setMaxErrorLength(100);

        RawCv rawCv = rawCv(
                CvProcessingStatus.UPLOADED
        );

        stubParseAcquisition(rawCv);

        String oversizedCode =
                "CV_" + "A".repeat(200);

        when(
                parserClient.parse(
                        any(CvParseRequest.class)
                )
        ).thenThrow(
                CvParserClientException.httpError(
                        RAW_CV_ID,
                        422,
                        oversizedCode
                )
        );

        when(
                rawCvStatusRepository.markFailed(
                        eq(RAW_CV_ID),
                        eq(OWNER_USER_ID),
                        any(String.class)
                )
        ).thenReturn(true);

        assertThatThrownBy(() ->
                service.parse(
                        RAW_CV_ID,
                        OWNER_USER_ID
                )
        ).isInstanceOf(
                CvParsingException.class
        );

        ArgumentCaptor<String> errorCaptor =
                ArgumentCaptor.forClass(
                        String.class
                );

        verify(rawCvStatusRepository)
                .markFailed(
                        eq(RAW_CV_ID),
                        eq(OWNER_USER_ID),
                        errorCaptor.capture()
                );

        assertThat(errorCaptor.getValue())
                .hasSizeLessThanOrEqualTo(100)
                .doesNotContain("\n")
                .doesNotContain("\r");
    }

    @Test
    void shouldRecoverDuplicateProfileWithoutCreatingAnotherDocument() {
        RawCv rawCv = rawCv(
                CvProcessingStatus.UPLOADED
        );

        CvParseResponse parserResponse =
                parserResponse();

        CandidateProfile mappedProfile =
                currentProfile();

        CandidateProfile concurrentProfile =
                currentProfile();
        concurrentProfile.setId(
                "profile-concurrent"
        );

        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(Optional.of(rawCv));

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(
                Optional.empty(),
                Optional.of(concurrentProfile)
        );

        when(
                rawCvStatusRepository
                        .acquireForParsing(
                                RAW_CV_ID,
                                OWNER_USER_ID,
                                false
                        )
        ).thenReturn(true);

        when(
                parserClient.parse(
                        any(CvParseRequest.class)
                )
        ).thenReturn(parserResponse);

        when(
                responseValidator.validate(
                        any(CvParseRequest.class),
                        eq(parserResponse)
                )
        ).thenReturn(parserResponse);

        when(
                profileMapper.toDocument(
                        eq(rawCv),
                        eq(parserResponse),
                        isNull(),
                        eq(NOW)
                )
        ).thenReturn(mappedProfile);

        when(
                candidateProfileRepository.save(
                        mappedProfile
                )
        ).thenThrow(
                new DuplicateKeyException(
                        "duplicate rawCvId"
                )
        );

        when(
                rawCvStatusRepository.markParsed(
                        RAW_CV_ID,
                        OWNER_USER_ID
                )
        ).thenReturn(true);

        CandidateProfile result = service.parse(
                RAW_CV_ID,
                OWNER_USER_ID
        );

        assertThat(result)
                .isSameAs(concurrentProfile);

        verify(candidateProfileRepository)
                .save(mappedProfile);

        verify(rawCvStatusRepository)
                .markParsed(
                        RAW_CV_ID,
                        OWNER_USER_ID
                );
    }

    @Test
    void shouldMarkFailedWhenCandidateProfileSaveFails() {
        RawCv rawCv = rawCv(
                CvProcessingStatus.UPLOADED
        );

        CvParseResponse parserResponse =
                parserResponse();

        CandidateProfile mappedProfile =
                currentProfile();

        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(Optional.of(rawCv));

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(Optional.empty());

        when(
                rawCvStatusRepository
                        .acquireForParsing(
                                RAW_CV_ID,
                                OWNER_USER_ID,
                                false
                        )
        ).thenReturn(true);

        when(
                parserClient.parse(
                        any(CvParseRequest.class)
                )
        ).thenReturn(parserResponse);

        when(
                responseValidator.validate(
                        any(CvParseRequest.class),
                        eq(parserResponse)
                )
        ).thenReturn(parserResponse);

        when(
                profileMapper.toDocument(
                        eq(rawCv),
                        eq(parserResponse),
                        isNull(),
                        eq(NOW)
                )
        ).thenReturn(mappedProfile);

        when(
                candidateProfileRepository.save(
                        mappedProfile
                )
        ).thenThrow(
                new RuntimeException(
                        "database contains private detail"
                )
        );

        when(
                rawCvStatusRepository.markFailed(
                        eq(RAW_CV_ID),
                        eq(OWNER_USER_ID),
                        any(String.class)
                )
        ).thenReturn(true);

        assertParsingException(
                () -> service.parse(
                        RAW_CV_ID,
                        OWNER_USER_ID
                ),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "CANDIDATE_PROFILE_SAVE_FAILED"
        );

        ArgumentCaptor<String> errorCaptor =
                ArgumentCaptor.forClass(
                        String.class
                );

        verify(rawCvStatusRepository)
                .markFailed(
                        eq(RAW_CV_ID),
                        eq(OWNER_USER_ID),
                        errorCaptor.capture()
                );

        assertThat(errorCaptor.getValue())
                .isEqualTo(
                        "CANDIDATE_PROFILE_PERSISTENCE_FAILED"
                )
                .doesNotContain(
                        "private detail"
                );
    }

    @Test
    void shouldMarkFailedWhenParsedStatusUpdateFails() {
        RawCv uploadedRawCv = rawCv(
                CvProcessingStatus.UPLOADED
        );

        RawCv stillParsingRawCv = rawCv(
                CvProcessingStatus.PARSING
        );

        CvParseResponse parserResponse =
                parserResponse();

        CandidateProfile mappedProfile =
                currentProfile();

        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(
                Optional.of(uploadedRawCv),
                Optional.of(stillParsingRawCv)
        );

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(Optional.empty());

        when(
                rawCvStatusRepository
                        .acquireForParsing(
                                RAW_CV_ID,
                                OWNER_USER_ID,
                                false
                        )
        ).thenReturn(true);

        when(
                parserClient.parse(
                        any(CvParseRequest.class)
                )
        ).thenReturn(parserResponse);

        when(
                responseValidator.validate(
                        any(CvParseRequest.class),
                        eq(parserResponse)
                )
        ).thenReturn(parserResponse);

        when(
                profileMapper.toDocument(
                        eq(uploadedRawCv),
                        eq(parserResponse),
                        isNull(),
                        eq(NOW)
                )
        ).thenReturn(mappedProfile);

        when(
                candidateProfileRepository.save(
                        mappedProfile
                )
        ).thenReturn(mappedProfile);

        when(
                rawCvStatusRepository.markParsed(
                        RAW_CV_ID,
                        OWNER_USER_ID
                )
        ).thenReturn(false);

        when(
                rawCvStatusRepository.markFailed(
                        eq(RAW_CV_ID),
                        eq(OWNER_USER_ID),
                        any(String.class)
                )
        ).thenReturn(true);

        assertParsingException(
                () -> service.parse(
                        RAW_CV_ID,
                        OWNER_USER_ID
                ),
                HttpStatus.INTERNAL_SERVER_ERROR,
                "RAW_CV_STATUS_UPDATE_FAILED"
        );

        verify(rawCvStatusRepository)
                .markFailed(
                        RAW_CV_ID,
                        OWNER_USER_ID,
                        "RAW_CV_STATUS_UPDATE_FAILED"
                );
    }

    @Test
    void shouldAcceptConcurrentCompletionWhenStatusIsAlreadyParsed() {
        RawCv uploadedRawCv = rawCv(
                CvProcessingStatus.UPLOADED
        );

        RawCv parsedRawCv = rawCv(
                CvProcessingStatus.PARSED
        );

        CvParseResponse parserResponse =
                parserResponse();

        CandidateProfile mappedProfile =
                currentProfile();

        CandidateProfile concurrentProfile =
                currentProfile();
        concurrentProfile.setId(
                "profile-concurrent"
        );

        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(
                Optional.of(uploadedRawCv),
                Optional.of(parsedRawCv)
        );

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(
                Optional.empty(),
                Optional.of(concurrentProfile)
        );

        when(
                rawCvStatusRepository
                        .acquireForParsing(
                                RAW_CV_ID,
                                OWNER_USER_ID,
                                false
                        )
        ).thenReturn(true);

        when(
                parserClient.parse(
                        any(CvParseRequest.class)
                )
        ).thenReturn(parserResponse);

        when(
                responseValidator.validate(
                        any(CvParseRequest.class),
                        eq(parserResponse)
                )
        ).thenReturn(parserResponse);

        when(
                profileMapper.toDocument(
                        eq(uploadedRawCv),
                        eq(parserResponse),
                        isNull(),
                        eq(NOW)
                )
        ).thenReturn(mappedProfile);

        when(
                candidateProfileRepository.save(
                        mappedProfile
                )
        ).thenReturn(mappedProfile);

        when(
                rawCvStatusRepository.markParsed(
                        RAW_CV_ID,
                        OWNER_USER_ID
                )
        ).thenReturn(false);

        CandidateProfile result = service.parse(
                RAW_CV_ID,
                OWNER_USER_ID
        );

        assertThat(result)
                .isSameAs(concurrentProfile);

        verify(
                rawCvStatusRepository,
                never()
        ).markFailed(
                eq(RAW_CV_ID),
                eq(OWNER_USER_ID),
                any(String.class)
        );
    }

    @Test
    void shouldGetOwnedCandidateProfile() {
        RawCv rawCv = rawCv(
                CvProcessingStatus.PARSED
        );

        CandidateProfile profile =
                currentProfile();

        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(Optional.of(rawCv));

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(Optional.of(profile));

        CandidateProfile result =
                service.getProfile(
                        RAW_CV_ID,
                        OWNER_USER_ID
                );

        assertThat(result).isSameAs(profile);
    }

    @Test
    void shouldReturnNotFoundWhenProfileDoesNotExist() {
        RawCv rawCv = rawCv(
                CvProcessingStatus.UPLOADED
        );

        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(Optional.of(rawCv));

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(Optional.empty());

        assertParsingException(
                () -> service.getProfile(
                        RAW_CV_ID,
                        OWNER_USER_ID
                ),
                HttpStatus.NOT_FOUND,
                "CANDIDATE_PROFILE_NOT_FOUND"
        );
    }

    private void stubSuccessfulParse(
            RawCv rawCv,
            CandidateProfile existingProfile,
            CvParseResponse parserResponse,
            CandidateProfile mappedProfile
    ) {
        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(Optional.of(rawCv));

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(
                Optional.ofNullable(existingProfile)
        );

        when(
                rawCvStatusRepository
                        .acquireForParsing(
                                RAW_CV_ID,
                                OWNER_USER_ID,
                                false
                        )
        ).thenReturn(true);

        when(
                parserClient.parse(
                        any(CvParseRequest.class)
                )
        ).thenReturn(parserResponse);

        when(
                responseValidator.validate(
                        any(CvParseRequest.class),
                        eq(parserResponse)
                )
        ).thenReturn(parserResponse);

        when(
                profileMapper.toDocument(
                        eq(rawCv),
                        eq(parserResponse),
                        nullable(
                                CandidateProfile.class
                        ),
                        eq(NOW)
                )
        ).thenReturn(mappedProfile);

        when(
                candidateProfileRepository.save(
                        mappedProfile
                )
        ).thenReturn(mappedProfile);

        when(
                rawCvStatusRepository.markParsed(
                        RAW_CV_ID,
                        OWNER_USER_ID
                )
        ).thenReturn(true);
    }

    private void stubParseAcquisition(
            RawCv rawCv
    ) {
        when(
                rawCvRepository.findById(
                        RAW_CV_ID
                )
        ).thenReturn(Optional.of(rawCv));

        when(
                candidateProfileRepository
                        .findByRawCvId(
                                RAW_CV_ID
                        )
        ).thenReturn(Optional.empty());

        when(
                rawCvStatusRepository
                        .acquireForParsing(
                                RAW_CV_ID,
                                OWNER_USER_ID,
                                false
                        )
        ).thenReturn(true);
    }

    private RawCv rawCv(
            CvProcessingStatus status
    ) {
        return RawCv.builder()
                .id(RAW_CV_ID)
                .ownerUserId(OWNER_USER_ID)
                .bucket("autojob-cvs")
                .objectKey(
                        "raw/2026/08/04/raw-cv-001/candidate.pdf"
                )
                .originalFilename(
                        "candidate.pdf"
                )
                .extension("pdf")
                .contentType("application/pdf")
                .sizeBytes(125_000L)
                .sha256("sha256-value")
                .status(status)
                .uploadedAt(
                        Instant.parse(
                                "2026-08-04T03:00:00Z"
                        )
                )
                .build();
    }

    private CandidateProfile currentProfile() {
        return CandidateProfile.builder()
                .id("profile-001")
                .rawCvId(RAW_CV_ID)
                .ownerUserId(OWNER_USER_ID)
                .fullName("Nguyễn Minh Anh")
                .headline("Senior Accountant")
                .contact(
                        new CandidateProfile
                                .ContactInformation(
                                "minh.anh@example.com",
                                "+84901234567",
                                null,
                                "Ho Chi Minh City",
                                null,
                                "Vietnam",
                                null
                        )
                )
                .links(List.of())
                .targetJobTitles(
                        List.of(
                                "Chief Accountant"
                        )
                )
                .targetIndustries(
                        List.of("Manufacturing")
                )
                .preferredLocations(
                        List.of(
                                "Ho Chi Minh City"
                        )
                )
                .preferredWorkModes(
                        List.of(
                                CandidateProfile
                                        .WorkMode
                                        .ONSITE
                        )
                )
                .preferredEmploymentTypes(
                        List.of(
                                CandidateProfile
                                        .EmploymentType
                                        .FULL_TIME
                        )
                )
                .skills(List.of())
                .workExperiences(List.of())
                .projects(List.of())
                .educations(List.of())
                .certifications(List.of())
                .licenses(List.of())
                .languages(List.of())
                .awards(List.of())
                .publications(List.of())
                .volunteerExperiences(List.of())
                .activities(List.of())
                .trainingCourses(List.of())
                .interests(List.of())
                .recentJobTitles(List.of())
                .recentCompanies(List.of())
                .detectedLanguage(
                        CandidateProfile
                                .DetectedLanguage
                                .VI
                )
                .rawText(
                        "Senior Accountant profile"
                )
                .sections(List.of())
                .parserVersion("rule-v1")
                .parserWarnings(List.of())
                .parseQuality(
                        new CandidateProfile
                                .ParseQuality(
                                0.9,
                                1.0,
                                0.8,
                                0.9,
                                List.of(),
                                List.of()
                        )
                )
                .sourceBucket("autojob-cvs")
                .sourceObjectKey(
                        "raw/2026/08/04/raw-cv-001/candidate.pdf"
                )
                .sourceOriginalFilename(
                        "candidate.pdf"
                )
                .sourceContentType(
                        "application/pdf"
                )
                .sourceSizeBytes(125_000L)
                .sourceSha256("sha256-value")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private CvParseResponse parserResponse() {
        return new CvParseResponse(
                RAW_CV_ID,
                "rule-v1",
                1,
                CvParseResponse
                        .DetectedLanguage
                        .VI,
                null,
                List.of()
        );
    }

    private void assertParsingException(
            ThrowingOperation operation,
            HttpStatus expectedStatus,
            String expectedCode
    ) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(
                        CvParsingException.class
                )
                .satisfies(throwable -> {
                    CvParsingException exception =
                            (CvParsingException)
                                    throwable;

                    assertThat(
                            exception.getStatus()
                    ).isEqualTo(expectedStatus);

                    assertThat(
                            exception.getCode()
                    ).isEqualTo(expectedCode);

                    assertThat(
                            exception.getRawCvId()
                    ).isEqualTo(RAW_CV_ID);
                });
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run();
    }
}