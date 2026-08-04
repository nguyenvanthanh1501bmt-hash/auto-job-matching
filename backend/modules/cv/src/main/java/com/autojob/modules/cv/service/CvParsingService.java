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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Service
public class CvParsingService {

    private final RawCvRepository rawCvRepository;
    private final RawCvStatusRepository rawCvStatusRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CvParserClient parserClient;
    private final CvParserResponseValidator responseValidator;
    private final CandidateProfileMapper profileMapper;
    private final CvParserProperties parserProperties;
    private final Clock clock;

    @Autowired
    public CvParsingService(
            RawCvRepository rawCvRepository,
            RawCvStatusRepository rawCvStatusRepository,
            CandidateProfileRepository candidateProfileRepository,
            CvParserClient parserClient,
            CvParserResponseValidator responseValidator,
            CandidateProfileMapper profileMapper,
            CvParserProperties parserProperties
    ) {
        this(
                rawCvRepository,
                rawCvStatusRepository,
                candidateProfileRepository,
                parserClient,
                responseValidator,
                profileMapper,
                parserProperties,
                Clock.systemUTC()
        );
    }

    CvParsingService(
            RawCvRepository rawCvRepository,
            RawCvStatusRepository rawCvStatusRepository,
            CandidateProfileRepository candidateProfileRepository,
            CvParserClient parserClient,
            CvParserResponseValidator responseValidator,
            CandidateProfileMapper profileMapper,
            CvParserProperties parserProperties,
            Clock clock
    ) {
        this.rawCvRepository = Objects.requireNonNull(
                rawCvRepository,
                "rawCvRepository"
        );
        this.rawCvStatusRepository = Objects.requireNonNull(
                rawCvStatusRepository,
                "rawCvStatusRepository"
        );
        this.candidateProfileRepository =
                Objects.requireNonNull(
                        candidateProfileRepository,
                        "candidateProfileRepository"
                );
        this.parserClient = Objects.requireNonNull(
                parserClient,
                "parserClient"
        );
        this.responseValidator = Objects.requireNonNull(
                responseValidator,
                "responseValidator"
        );
        this.profileMapper = Objects.requireNonNull(
                profileMapper,
                "profileMapper"
        );
        this.parserProperties = Objects.requireNonNull(
                parserProperties,
                "parserProperties"
        );
        this.clock = Objects.requireNonNull(
                clock,
                "clock"
        );
    }

    public CandidateProfile parse(
            String rawCvId,
            String ownerUserId
    ) {
        requireAuthenticatedOwner(
                rawCvId,
                ownerUserId
        );

        RawCv rawCv = getOwnedRawCv(
                rawCvId,
                ownerUserId
        );

        CandidateProfile existingProfile =
                findOwnedProfile(
                        rawCv,
                        ownerUserId
                )
                        .orElse(null);

        if (hasExpectedParserVersion(existingProfile)) {
            return recoverCurrentProfile(
                    rawCv,
                    existingProfile,
                    ownerUserId
            );
        }

        boolean versionUpgrade =
                rawCv.getStatus()
                        == CvProcessingStatus.PARSED
                        && existingProfile != null;

        boolean acquired =
                rawCvStatusRepository.acquireForParsing(
                        rawCvId,
                        ownerUserId,
                        versionUpgrade
                );

        if (!acquired) {
            return resolveAcquisitionFailure(
                    rawCvId,
                    ownerUserId
            );
        }

        return executeParsing(
                rawCv,
                existingProfile,
                ownerUserId
        );
    }

    public CandidateProfile getProfile(
            String rawCvId,
            String ownerUserId
    ) {
        requireAuthenticatedOwner(
                rawCvId,
                ownerUserId
        );

        RawCv rawCv = getOwnedRawCv(
                rawCvId,
                ownerUserId
        );

        return findOwnedProfile(
                rawCv,
                ownerUserId
        ).orElseThrow(() ->
                new CvParsingException(
                        HttpStatus.NOT_FOUND,
                        "CANDIDATE_PROFILE_NOT_FOUND",
                        "Candidate profile has not been created",
                        rawCvId
                )
        );
    }

    private CandidateProfile executeParsing(
            RawCv rawCv,
            CandidateProfile existingProfile,
            String ownerUserId
    ) {
        String rawCvId = rawCv.getId();

        try {
            CvParseRequest request =
                    new CvParseRequest(
                            rawCvId,
                            rawCv.getBucket(),
                            rawCv.getObjectKey(),
                            rawCv.getOriginalFilename(),
                            rawCv.getContentType()
                    );

            CvParseResponse parserResponse =
                    parserClient.parse(request);

            CvParseResponse validatedResponse =
                    responseValidator.validate(
                            request,
                            parserResponse
                    );

            CandidateProfile mappedProfile =
                    profileMapper.toDocument(
                            rawCv,
                            validatedResponse,
                            existingProfile,
                            Instant.now(clock)
                    );

            CandidateProfile savedProfile =
                    saveProfileWithDuplicateRecovery(
                            mappedProfile,
                            rawCv,
                            ownerUserId
                    );

            return completeParsedState(
                    rawCvId,
                    ownerUserId,
                    savedProfile
            );
        } catch (CvParserClientException exception) {
            CvParsingException mapped =
                    mapParserException(
                            rawCvId,
                            exception
                    );

            markFailedSafely(
                    rawCvId,
                    ownerUserId,
                    parserLastError(exception),
                    mapped
            );

            throw mapped;
        } catch (CvParserResponseValidationException exception) {
            CvParsingException mapped =
                    new CvParsingException(
                            HttpStatus.BAD_GATEWAY,
                            "CV_PARSER_INVALID_RESPONSE",
                            "CV parser returned an invalid response",
                            rawCvId,
                            exception
                    );

            markFailedSafely(
                    rawCvId,
                    ownerUserId,
                    validationLastError(exception),
                    mapped
            );

            throw mapped;
        } catch (CvParsingException exception) {
            markFailedSafely(
                    rawCvId,
                    ownerUserId,
                    parsingExceptionLastError(exception),
                    exception
            );

            throw exception;
        } catch (RuntimeException exception) {
            CvParsingException mapped =
                    new CvParsingException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "CANDIDATE_PROFILE_SAVE_FAILED",
                            "Candidate profile could not be saved",
                            rawCvId,
                            exception
                    );

            markFailedSafely(
                    rawCvId,
                    ownerUserId,
                    "CANDIDATE_PROFILE_PERSISTENCE_FAILED",
                    mapped
            );

            throw mapped;
        }
    }

    private CandidateProfile saveProfileWithDuplicateRecovery(
            CandidateProfile profile,
            RawCv rawCv,
            String ownerUserId
    ) {
        try {
            CandidateProfile saved =
                    candidateProfileRepository.save(profile);

            if (saved == null) {
                throw new IllegalStateException(
                        "CandidateProfileRepository returned null"
                );
            }

            return saved;
        } catch (DuplicateKeyException exception) {
            CandidateProfile concurrentProfile =
                    findOwnedProfile(
                            rawCv,
                            ownerUserId
                    )
                            .orElse(null);

            if (hasExpectedParserVersion(
                    concurrentProfile
            )) {
                return concurrentProfile;
            }

            throw exception;
        }
    }

    private CandidateProfile recoverCurrentProfile(
            RawCv rawCv,
            CandidateProfile existingProfile,
            String ownerUserId
    ) {
        String rawCvId = rawCv.getId();
        CvProcessingStatus status = rawCv.getStatus();

        if (status == CvProcessingStatus.PARSED) {
            return existingProfile;
        }

        if (status == CvProcessingStatus.PARSING) {
            return completeParsedState(
                    rawCvId,
                    ownerUserId,
                    existingProfile
            );
        }

        if (status == CvProcessingStatus.UPLOADED
                || status == CvProcessingStatus.FAILED) {
            boolean acquired =
                    rawCvStatusRepository.acquireForParsing(
                            rawCvId,
                            ownerUserId,
                            false
                    );

            if (!acquired) {
                return resolveAcquisitionFailure(
                        rawCvId,
                        ownerUserId
                );
            }

            return completeParsedState(
                    rawCvId,
                    ownerUserId,
                    existingProfile
            );
        }

        throw invalidState(
                rawCvId,
                status
        );
    }

    private CandidateProfile completeParsedState(
            String rawCvId,
            String ownerUserId,
            CandidateProfile preferredProfile
    ) {
        boolean markedParsed =
                rawCvStatusRepository.markParsed(
                        rawCvId,
                        ownerUserId
                );

        if (markedParsed) {
            return preferredProfile;
        }

        /*
         * Một request concurrent có thể đã hoàn tất transition trước.
         * Khi đó đây vẫn là success nếu cả status và parserVersion
         * đã đạt trạng thái đích.
         */
        RawCv currentRawCv = getOwnedRawCv(
                rawCvId,
                ownerUserId
        );

        if (currentRawCv.getStatus()
                == CvProcessingStatus.PARSED) {
            CandidateProfile currentProfile =
                    findOwnedProfile(
                            currentRawCv,
                            ownerUserId
                    )
                            .orElse(null);

            if (hasExpectedParserVersion(
                    currentProfile
            )) {
                return currentProfile;
            }
        }

        throw new CvParsingException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "RAW_CV_STATUS_UPDATE_FAILED",
                "Candidate profile was saved but CV status could not be updated",
                rawCvId
        );
    }

    private CandidateProfile resolveAcquisitionFailure(
            String rawCvId,
            String ownerUserId
    ) {
        RawCv currentRawCv = getOwnedRawCv(
                rawCvId,
                ownerUserId
        );

        CandidateProfile currentProfile =
                findOwnedProfile(
                        currentRawCv,
                        ownerUserId
                )
                        .orElse(null);

        if (currentRawCv.getStatus()
                == CvProcessingStatus.PARSED
                && hasExpectedParserVersion(
                currentProfile
        )) {
            return currentProfile;
        }

        if (currentRawCv.getStatus()
                == CvProcessingStatus.PARSING) {
            throw new CvParsingException(
                    HttpStatus.CONFLICT,
                    "CV_PARSE_IN_PROGRESS",
                    "CV parsing is already in progress",
                    rawCvId
            );
        }

        throw invalidState(
                rawCvId,
                currentRawCv.getStatus()
        );
    }

    private RawCv getOwnedRawCv(
            String rawCvId,
            String ownerUserId
    ) {
        RawCv rawCv = rawCvRepository
                .findById(rawCvId)
                .orElseThrow(() ->
                        new CvParsingException(
                                HttpStatus.NOT_FOUND,
                                "RAW_CV_NOT_FOUND",
                                "Raw CV was not found",
                                rawCvId
                        )
                );

        if (!Objects.equals(
                ownerUserId,
                rawCv.getOwnerUserId()
        )) {
            throw new CvParsingException(
                    HttpStatus.FORBIDDEN,
                    "CV_ACCESS_DENIED",
                    "You do not have access to this CV",
                    rawCvId
            );
        }

        return rawCv;
    }

    private Optional<CandidateProfile> findOwnedProfile(
            RawCv rawCv,
            String ownerUserId
    ) {
        Optional<CandidateProfile> result =
                candidateProfileRepository.findByRawCvId(
                        rawCv.getId()
                );

        if (result.isEmpty()) {
            return Optional.empty();
        }

        CandidateProfile profile = result.get();

        boolean rawCvMatches = Objects.equals(
                rawCv.getId(),
                profile.getRawCvId()
        );

        boolean ownerMatches = Objects.equals(
                ownerUserId,
                profile.getOwnerUserId()
        );

        if (!rawCvMatches || !ownerMatches) {
            throw new CvParsingException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CANDIDATE_PROFILE_INTEGRITY_ERROR",
                    "Candidate profile ownership metadata is inconsistent",
                    rawCv.getId()
            );
        }

        return result;
    }

    private boolean hasExpectedParserVersion(
            CandidateProfile profile
    ) {
        if (profile == null) {
            return false;
        }

        return expectedParserVersion().equals(
                profile.getParserVersion()
        );
    }

    private String expectedParserVersion() {
        return parserProperties
                .getExpectedVersion()
                .trim();
    }

    private void requireAuthenticatedOwner(
            String rawCvId,
            String ownerUserId
    ) {
        if (rawCvId == null || rawCvId.isBlank()) {
            throw new CvParsingException(
                    HttpStatus.BAD_REQUEST,
                    "RAW_CV_ID_REQUIRED",
                    "Raw CV id is required",
                    rawCvId
            );
        }

        if (ownerUserId == null
                || ownerUserId.isBlank()) {
            throw new CvParsingException(
                    HttpStatus.UNAUTHORIZED,
                    "CV_AUTHENTICATION_REQUIRED",
                    "Authentication is required to access CV profiles",
                    rawCvId
            );
        }
    }

    private CvParsingException invalidState(
            String rawCvId,
            CvProcessingStatus status
    ) {
        return new CvParsingException(
                HttpStatus.CONFLICT,
                "CV_PARSE_INVALID_STATE",
                "CV cannot be parsed from its current state",
                rawCvId
        );
    }

    private CvParsingException mapParserException(
            String rawCvId,
            CvParserClientException exception
    ) {
        return switch (exception.getFailureType()) {
            case CONNECTION_REFUSED,
                 CONNECTION_FAILURE ->
                    new CvParsingException(
                            HttpStatus.BAD_GATEWAY,
                            "CV_PARSER_UNAVAILABLE",
                            "CV parser service is unavailable",
                            rawCvId,
                            exception
                    );

            case CONNECT_TIMEOUT,
                 RESPONSE_TIMEOUT ->
                    new CvParsingException(
                            HttpStatus.GATEWAY_TIMEOUT,
                            "CV_PARSER_TIMEOUT",
                            "CV parser service timed out",
                            rawCvId,
                            exception
                    );

            case MALFORMED_JSON,
                 EMPTY_RESPONSE,
                 RESPONSE_TOO_LARGE,
                 UNEXPECTED_STATUS ->
                    new CvParsingException(
                            HttpStatus.BAD_GATEWAY,
                            "CV_PARSER_INVALID_RESPONSE",
                            "CV parser service returned an invalid response",
                            rawCvId,
                            exception
                    );

            case HTTP_5XX ->
                    new CvParsingException(
                            HttpStatus.BAD_GATEWAY,
                            parserCodeOrDefault(
                                    exception,
                                    "CV_PARSER_UPSTREAM_ERROR"
                            ),
                            "CV parser service failed",
                            rawCvId,
                            exception
                    );

            case HTTP_4XX ->
                    mapParserHttp4xx(
                            rawCvId,
                            exception
                    );
        };
    }

    private CvParsingException mapParserHttp4xx(
            String rawCvId,
            CvParserClientException exception
    ) {
        String parserCode =
                safeParserCode(
                        exception.getParserCode()
                );

        if ("CV_FILE_TOO_LARGE".equals(parserCode)
                || Integer.valueOf(413).equals(
                exception.getUpstreamStatus()
        )) {
            return new CvParsingException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "CV_FILE_TOO_LARGE",
                    "CV exceeds the parser size limit",
                    rawCvId,
                    exception
            );
        }

        if ("CV_EXTRACTION_TIMEOUT".equals(
                parserCode
        )) {
            return new CvParsingException(
                    HttpStatus.GATEWAY_TIMEOUT,
                    parserCode,
                    "CV text extraction timed out",
                    rawCvId,
                    exception
            );
        }

        if ("CV_OBJECT_NOT_FOUND".equals(
                parserCode
        )) {
            return new CvParsingException(
                    HttpStatus.BAD_GATEWAY,
                    parserCode,
                    "CV source object is unavailable",
                    rawCvId,
                    exception
            );
        }

        if (isUnprocessableParserCode(
                parserCode
        )) {
            return new CvParsingException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    parserCode,
                    parserPublicMessage(parserCode),
                    rawCvId,
                    exception
            );
        }

        if ("CV_INTERNAL_ERROR".equals(
                parserCode
        )) {
            return new CvParsingException(
                    HttpStatus.BAD_GATEWAY,
                    parserCode,
                    "CV parser service failed",
                    rawCvId,
                    exception
            );
        }

        return new CvParsingException(
                HttpStatus.BAD_GATEWAY,
                "CV_PARSER_REQUEST_REJECTED",
                "CV parser service rejected the request",
                rawCvId,
                exception
        );
    }

    private boolean isUnprocessableParserCode(
            String parserCode
    ) {
        return "CV_INVALID_REQUEST".equals(parserCode)
                || "CV_UNSUPPORTED_FORMAT".equals(parserCode)
                || "CV_CORRUPT_FILE".equals(parserCode)
                || "CV_TEXT_NOT_EXTRACTABLE".equals(parserCode)
                || "CV_DOC_EXTRACTION_FAILED".equals(parserCode);
    }

    private String parserPublicMessage(
            String parserCode
    ) {
        return switch (parserCode) {
            case "CV_INVALID_REQUEST" ->
                    "CV metadata is invalid";

            case "CV_UNSUPPORTED_FORMAT" ->
                    "CV file format is not supported";

            case "CV_CORRUPT_FILE" ->
                    "CV file is corrupt or unreadable";

            case "CV_TEXT_NOT_EXTRACTABLE" ->
                    "CV does not contain extractable text";

            case "CV_DOC_EXTRACTION_FAILED" ->
                    "Legacy DOC text extraction failed";

            default ->
                    "CV could not be processed";
        };
    }

    private String parserCodeOrDefault(
            CvParserClientException exception,
            String defaultCode
    ) {
        String parserCode =
                safeParserCode(
                        exception.getParserCode()
                );

        return parserCode == null
                ? defaultCode
                : parserCode;
    }

    private String parserLastError(
            CvParserClientException exception
    ) {
        StringBuilder result = new StringBuilder(
                "PARSER_"
        ).append(
                exception
                        .getFailureType()
                        .name()
        );

        String parserCode =
                safeParserCode(
                        exception.getParserCode()
                );

        if (parserCode != null) {
            result.append('_')
                    .append(parserCode);
        }

        if (exception.getUpstreamStatus() != null) {
            result.append("_HTTP_")
                    .append(
                            exception.getUpstreamStatus()
                    );
        }

        return sanitizeAndTruncate(
                result.toString()
        );
    }

    private String validationLastError(
            CvParserResponseValidationException exception
    ) {
        String field = sanitizeField(
                exception.getField()
        );

        String value = field == null
                ? "PARSER_RESPONSE_VALIDATION_FAILED"
                : "PARSER_RESPONSE_VALIDATION_FAILED_"
                + field;

        return sanitizeAndTruncate(value);
    }

    private String parsingExceptionLastError(
            CvParsingException exception
    ) {
        String code = safeParserCode(
                exception.getCode()
        );

        return sanitizeAndTruncate(
                code == null
                        ? "CV_PARSING_FAILED"
                        : code
        );
    }

    private void markFailedSafely(
            String rawCvId,
            String ownerUserId,
            String lastError,
            RuntimeException originalException
    ) {
        try {
            rawCvStatusRepository.markFailed(
                    rawCvId,
                    ownerUserId,
                    sanitizeAndTruncate(lastError)
            );
        } catch (RuntimeException statusException) {
            originalException.addSuppressed(
                    statusException
            );
        }
    }

    private String safeParserCode(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();

        if (trimmed.length() > 100) {
            trimmed = trimmed.substring(0, 100);
        }

        for (int index = 0;
             index < trimmed.length();
             index++) {
            char current = trimmed.charAt(index);

            boolean allowed =
                    current >= 'A'
                            && current <= 'Z'
                            || current >= '0'
                            && current <= '9'
                            || current == '_';

            if (!allowed) {
                return null;
            }
        }

        return trimmed;
    }

    private String sanitizeField(
            String field
    ) {
        if (field == null || field.isBlank()) {
            return null;
        }

        StringBuilder result = new StringBuilder();

        for (int index = 0;
             index < field.length()
                     && result.length() < 200;
             index++) {
            char current = field.charAt(index);

            boolean allowed =
                    Character.isLetterOrDigit(current)
                            || current == '.'
                            || current == '['
                            || current == ']'
                            || current == '_'
                            || current == '-';

            if (allowed) {
                result.append(current);
            }
        }

        return result.isEmpty()
                ? null
                : result.toString();
    }

    private String sanitizeAndTruncate(
            String value
    ) {
        String source = value == null
                ? "CV_PARSING_FAILED"
                : value;

        StringBuilder sanitized =
                new StringBuilder();

        boolean previousWhitespace = false;

        for (int index = 0;
             index < source.length();
             index++) {
            char current = source.charAt(index);

            if (Character.isISOControl(current)
                    || Character.isWhitespace(current)) {
                if (!previousWhitespace
                        && !sanitized.isEmpty()) {
                    sanitized.append(' ');
                }

                previousWhitespace = true;
                continue;
            }

            sanitized.append(current);
            previousWhitespace = false;
        }

        String result = sanitized
                .toString()
                .trim();

        if (result.isEmpty()) {
            result = "CV_PARSING_FAILED";
        }

        int maxLength =
                parserProperties.getMaxErrorLength();

        return result.length() <= maxLength
                ? result
                : result.substring(0, maxLength);
    }
}