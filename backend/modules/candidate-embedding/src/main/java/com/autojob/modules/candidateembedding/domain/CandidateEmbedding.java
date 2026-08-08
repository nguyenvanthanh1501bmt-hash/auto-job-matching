package com.autojob.modules.candidateembedding.domain;

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
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "candidate_embeddings")
@CompoundIndexes({
        @CompoundIndex(
                name = "uk_candidate_embedding_profile_version_text",
                def = "{'candidateProfileId': 1, 'embeddingVersion': 1, 'textVersion': 1}",
                unique = true
        ),
        @CompoundIndex(
                name = "idx_candidate_embedding_status_updated",
                def = "{'status': 1, 'updatedAt': 1}"
        ),
        @CompoundIndex(
                name = "idx_candidate_embedding_profile_updated",
                def = "{'candidateProfileId': 1, 'updatedAt': -1}"
        )
})
public class CandidateEmbedding {

    @Id
    private String id;

    private String candidateProfileId;
    private String rawCvId;
    private String parserVersion;

    private String textVersion;

    private String modelName;
    private String modelRevision;
    private String embeddingVersion;
    private String textHash;

    private Integer dimension;
    private Boolean normalized;

    private List<Double> vector;

    private CandidateEmbeddingStatus status;
    private String lastError;

    private Instant embeddedAt;
    private Instant createdAt;
    private Instant updatedAt;
}