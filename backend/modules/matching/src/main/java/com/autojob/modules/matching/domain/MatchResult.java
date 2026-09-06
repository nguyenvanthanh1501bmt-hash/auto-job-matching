package com.autojob.modules.matching.domain;

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
@Document(collection = "match_results")
@CompoundIndexes({
        @CompoundIndex(
                name = "uk_match_result_run_job",
                def = "{'candidateProfileId': 1, "
                        + "'candidateEmbeddingId': 1, "
                        + "'rankingVersion': 1, "
                        + "'normalizedJobId': 1}",
                unique = true
        ),
        @CompoundIndex(
                name = "idx_match_result_candidate_rank",
                def = "{'candidateProfileId': 1, "
                        + "'rankingVersion': 1, "
                        + "'rank': 1}"
        ),
        @CompoundIndex(
                name = "idx_match_result_generated_at",
                def = "{'generatedAt': -1}"
        )
})
public class MatchResult {

    @Id
    private String id;

    /*
     * Candidate identity.
     */
    private String rawCvId;
    private String candidateProfileId;
    private String candidateEmbeddingId;

    /*
     * Job identity.
     */
    private String normalizedJobId;
    private String qdrantPointId;

    /*
     * Job snapshot.
     */
    private String sourceCode;
    private String sourceJobId;

    private String jobTitle;
    private String companyName;

    @Builder.Default
    private List<String> locations =
            List.of();

    private String locationText;
    private String salaryText;

    private String jobType;
    private String applyType;

    private String detailUrl;
    private String applyUrl;

    private Instant postedAt;
    private Instant deadlineAt;

    /*
     * Version snapshot.
     */
    private String parserVersion;
    private String normalizationVersion;
    private String embeddingVersion;
    private String candidateTextVersion;
    private String jobTextVersion;
    private String rankingVersion;

    /*
     * Rank sau hybrid reranking.
     */
    private int rank;

    /*
     * Full scoring breakdown.
     */
    private double finalScore;
    private double semanticScore;
    private double skillScore;
    private double seniorityScore;
    private double locationScore;
    private double freshnessScore;

    /*
     * =========================================================
     * Score knowledge metadata
     * =========================================================
     *
     * Dùng Boolean thay vì primitive boolean có chủ đích:
     *
     * true
     * -> component có evidence thật.
     *
     * false
     * -> component không đủ dữ liệu.
     *    Numeric score chỉ là neutral/display value.
     *
     * null
     * -> document cũ được tạo trước khi schema này tồn tại.
     *
     * Nhờ vậy không cần dùng magic number 0.50 để đoán UNKNOWN.
     */
    private Boolean skillKnown;
    private Boolean seniorityKnown;
    private Boolean locationKnown;
    private Boolean freshnessKnown;

    /*
     * Explainability.
     */
    @Builder.Default
    private List<String> matchedSkills =
            List.of();

    @Builder.Default
    private List<String> missingSkills =
            List.of();

    /*
     * Matching run timestamp.
     */
    private Instant generatedAt;
}