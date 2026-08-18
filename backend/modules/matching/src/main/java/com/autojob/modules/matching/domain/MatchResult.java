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
     * ---------------------------------------------------------
     * Job snapshot.
     * ---------------------------------------------------------
     *
     * Không bắt frontend phải query normalized_jobs
     * thêm một lần cho từng matching result.
     *
     * Đồng thời snapshot giúp ta biết chính xác user đã
     * nhìn thấy thông tin job nào tại thời điểm ranking.
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

    /*
     * Lưu enum dưới dạng String để persistence snapshot
     * ít coupling hơn với enum của job-normalizer.
     */
    private String jobType;
    private String applyType;

    private String detailUrl;
    private String applyUrl;

    private Instant postedAt;
    private Instant deadlineAt;

    /*
     * ---------------------------------------------------------
     * Version snapshot.
     * ---------------------------------------------------------
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