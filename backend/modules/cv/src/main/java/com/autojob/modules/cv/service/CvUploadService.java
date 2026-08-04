package com.autojob.modules.cv.service;

import com.autojob.modules.cv.config.CvStorageProperties;
import com.autojob.modules.cv.domain.CvProcessingStatus;
import com.autojob.modules.cv.domain.RawCv;
import com.autojob.modules.cv.repository.RawCvRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CvUploadService {

    private static final DateTimeFormatter DATE_PATH =
            DateTimeFormatter
                    .ofPattern("yyyy/MM/dd")
                    .withZone(ZoneOffset.UTC);

    private final RawCvRepository rawCvRepository;
    private final CvFileValidator fileValidator;
    private final CvObjectStorage objectStorage;
    private final CvStorageProperties storageProperties;

    public RawCv upload(
            MultipartFile file,
            String ownerUserId,
            String sourceIp
    ) {
        requireOwner(ownerUserId);

        CvFileValidator.ValidatedCvFile validated =
                fileValidator.validate(file);

        Instant uploadedAt = Instant.now();
        String rawCvId =
                UUID.randomUUID().toString();

        String objectKey = buildObjectKey(
                rawCvId,
                uploadedAt,
                validated.safeFilename()
        );

        String sha256 =
                sha256(validated.bytes());

        /*
         * Object được upload trước khi lưu metadata.
         *
         * Nếu Mongo save thất bại, object được xóa best-effort
         * để tránh tạo object mồ côi trong MinIO.
         */
        objectStorage.put(
                objectKey,
                validated.contentType(),
                validated.bytes()
        );

        RawCv rawCv = RawCv.builder()
                .id(rawCvId)
                .ownerUserId(ownerUserId)
                .bucket(storageProperties.getBucket())
                .objectKey(objectKey)
                .originalFilename(
                        validated.safeFilename()
                )
                .extension(validated.extension())
                .contentType(
                        validated.contentType()
                )
                .sizeBytes(
                        validated.bytes().length
                )
                .sha256(sha256)
                .status(
                        CvProcessingStatus.UPLOADED
                )
                .uploadedFromIp(
                        truncate(sourceIp, 100)
                )
                .uploadedAt(uploadedAt)
                .build();

        try {
            return rawCvRepository.save(rawCv);
        } catch (RuntimeException exception) {
            objectStorage.deleteQuietly(objectKey);

            throw new CvUploadException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "CV_METADATA_SAVE_FAILED",
                    "CV was uploaded but metadata could not be saved",
                    exception
            );
        }
    }

    public RawCv getById(
            String rawCvId,
            String ownerUserId
    ) {
        requireOwner(ownerUserId);

        if (rawCvId == null || rawCvId.isBlank()) {
            throw new CvUploadException(
                    HttpStatus.BAD_REQUEST,
                    "RAW_CV_ID_REQUIRED",
                    "Raw CV id is required"
            );
        }

        RawCv rawCv = rawCvRepository
                .findById(rawCvId)
                .orElseThrow(() ->
                        new CvUploadException(
                                HttpStatus.NOT_FOUND,
                                "RAW_CV_NOT_FOUND",
                                "Raw CV was not found"
                        )
                );

        if (!Objects.equals(
                ownerUserId,
                rawCv.getOwnerUserId()
        )) {
            throw new CvUploadException(
                    HttpStatus.FORBIDDEN,
                    "CV_ACCESS_DENIED",
                    "You do not have access to this CV"
            );
        }

        return rawCv;
    }

    private void requireOwner(
            String ownerUserId
    ) {
        if (ownerUserId == null
                || ownerUserId.isBlank()) {
            throw new CvUploadException(
                    HttpStatus.UNAUTHORIZED,
                    "CV_AUTHENTICATION_REQUIRED",
                    "Authentication is required to access CVs"
            );
        }
    }

    private String buildObjectKey(
            String rawCvId,
            Instant uploadedAt,
            String safeFilename
    ) {
        return "raw/"
                + DATE_PATH.format(uploadedAt)
                + "/"
                + rawCvId
                + "/"
                + safeFilename;
    }

    private String sha256(
            byte[] bytes
    ) {
        try {
            byte[] digest = MessageDigest
                    .getInstance("SHA-256")
                    .digest(bytes);

            return HexFormat
                    .of()
                    .formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is not available",
                    exception
            );
        }
    }

    private String truncate(
            String value,
            int maxLength
    ) {
        if (value == null) {
            return null;
        }

        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }
}