package com.autojob.modules.matching.repository;

import com.autojob.modules.matching.domain.MatchResult;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MatchResultRepository
        extends MongoRepository<MatchResult, String> {

    /**
     * Đọc chính xác kết quả của một matching run.
     */
    List<MatchResult>
    findByCandidateProfileIdAndCandidateEmbeddingIdAndRankingVersionOrderByRankAsc(
            String candidateProfileId,
            String candidateEmbeddingId,
            String rankingVersion
    );

    /**
     * Đọc result của candidate theo ranking version.
     *
     * API layer sau này có thể dùng method này.
     */
    List<MatchResult>
    findByCandidateProfileIdAndRankingVersionOrderByRankAsc(
            String candidateProfileId,
            String rankingVersion
    );

    /**
     * Kiểm tra matching run này đã có result chưa.
     */
    boolean existsByCandidateProfileIdAndCandidateEmbeddingIdAndRankingVersion(
            String candidateProfileId,
            String candidateEmbeddingId,
            String rankingVersion
    );

    /**
     * Khi force rebuild cùng candidate embedding
     * và cùng ranking version thì xóa result cũ trước.
     */
    long deleteByCandidateProfileIdAndCandidateEmbeddingIdAndRankingVersion(
            String candidateProfileId,
            String candidateEmbeddingId,
            String rankingVersion
    );
}