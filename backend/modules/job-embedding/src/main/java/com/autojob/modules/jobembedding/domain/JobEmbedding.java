package com.autojob.modules.jobembedding.domain;

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
@Document(collection = "job_embeddings")
@CompoundIndexes({
        @CompoundIndex(
                name = "uk_job_embedding_job_version",
                def = "{'normalizedJobId': 1, 'embeddingVersion': 1}",
                unique = true
        ),
        @CompoundIndex(
                name = "idx_job_embedding_status_updated",
                def = "{'status': 1, 'updatedAt': -1}"
        ),
        @CompoundIndex(
                name = "idx_job_embedding_job_updated",
                def = "{'normalizedJobId': 1, 'updatedAt': -1}"
        )
})
public class JobEmbedding {

    @Id
    private String id;

    private String normalizedJobId;
    private String normalizationVersion;

    private String modelName;
    private String modelRevision;
    private String embeddingVersion;
    private String textHash;

    private Integer dimension;
    private Boolean normalized;

    private String qdrantCollection;
    private String qdrantPointId;

    private JobEmbeddingStatus status;
    private String lastError;

    private Instant embeddedAt;
    private Instant createdAt;
    private Instant updatedAt;
}