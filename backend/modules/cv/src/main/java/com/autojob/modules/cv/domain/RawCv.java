package com.autojob.modules.cv.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("raw_cvs")
@CompoundIndexes({
        @CompoundIndex(
                name = "idx_raw_cvs_owner_uploaded_at",
                def = "{'ownerUserId': 1, 'uploadedAt': -1}"
        ),
        @CompoundIndex(
                name = "idx_raw_cvs_sha256",
                def = "{'sha256': 1}"
        )
})
public class RawCv {

    @Id
    private String id;

    /**
     * null nếu upload public và request không có JWT.
     */
    private String ownerUserId;

    private String bucket;
    private String objectKey;

    private String originalFilename;
    private String extension;
    private String contentType;
    private long sizeBytes;
    private String sha256;

    @Builder.Default
    private CvProcessingStatus status =
            CvProcessingStatus.UPLOADED;

    private String lastError;
    private String uploadedFromIp;
    private Instant uploadedAt;
}