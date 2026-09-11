package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.candidateembedding.service.CandidateEmbeddingGenerator;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewRequest;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewResponse.PreviewStatus;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewResponse.ScoreSnapshot;
import com.autojob.modules.matching.config.MatchingProperties;
import com.autojob.modules.matching.contract.MatchingRunResult;
import com.autojob.modules.matching.domain.MatchResult;
import com.autojob.modules.matching.service.HybridMatchingService;
import com.autojob.modules.matching.service.HybridRankingService;
import com.autojob.modules.matching.service.MatchingEvaluationService;
import com.autojob.modules.matching.service.MatchingPreconditionException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
public class CvTailoringPreviewService {

    private final CvTailoringDraftService draftService;
    private final HybridMatchingService hybridMatchingService;
    private final CandidateEmbeddingGenerator candidateEmbeddingGenerator;
    private final MatchingEvaluationService matchingEvaluationService;
    private final MatchingProperties matchingProperties;

    public CvTailoringPreviewService(
            CvTailoringDraftService draftService,
            HybridMatchingService hybridMatchingService,
            CandidateEmbeddingGenerator candidateEmbeddingGenerator,
            MatchingEvaluationService matchingEvaluationService,
            MatchingProperties matchingProperties
    ) {
        this.draftService =
                Objects.requireNonNull(
                        draftService,
                        "draftService must not be null"
                );

        this.hybridMatchingService =
                Objects.requireNonNull(
                        hybridMatchingService,
                        "hybridMatchingService must not be null"
                );

        this.candidateEmbeddingGenerator =
                Objects.requireNonNull(
                        candidateEmbeddingGenerator,
                        "candidateEmbeddingGenerator must not be null"
                );

        this.matchingEvaluationService =
                Objects.requireNonNull(
                        matchingEvaluationService,
                        "matchingEvaluationService must not be null"
                );

        this.matchingProperties =
                Objects.requireNonNull(
                        matchingProperties,
                        "matchingProperties must not be null"
                );
    }

    public CvTailoringPreviewResponse preview(
            String candidateProfileId,
            String normalizedJobId,
            String ownerUserId,
            CvTailoringPreviewRequest request
    ) {
        Objects.requireNonNull(
                request,
                "request must not be null"
        );

        requireText(
                request.analysisId(),
                "analysisId"
        );

        CvTailoringDraftService.TemporaryDraft draft =
                draftService.createTemporaryDraft(
                        request.analysisId(),
                        candidateProfileId,
                        normalizedJobId,
                        ownerUserId,
                        request.acceptedSuggestionIds()
                );

        MatchingRunResult baselineRun =
                loadCurrentMatchingRun(
                        candidateProfileId,
                        ownerUserId
                );

        MatchResult baselineMatch =
                baselineRun.results()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(
                                result ->
                                        normalizedJobId.equals(
                                                result.getNormalizedJobId()
                                        )
                        )
                        .findFirst()
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Selected job is no longer part "
                                                + "of the current matching result. "
                                                + "Analyze again before previewing."
                                )
                        );

        assertSameBaseline(
                draft.analysis(),
                baselineRun,
                baselineMatch
        );

        /*
         * Legacy analysis:
         *
         * Context được tạo từ MatchResult cũ không có
         * generatedAt thì không thể xác định exact matching run.
         *
         * Giữ behavior cũ cho những context này để:
         *
         * - dữ liệu cũ vẫn dùng được
         * - rolling deployment không làm preview chết
         * - test fixtures cũ vẫn đúng
         *
         * Fresh analyses từ production MatchingService có
         * generatedAt và sẽ luôn đi strict path phía dưới.
         */
        if (draft.analysis().matchingGeneratedAt() == null) {
            return previewLegacyContext(
                    candidateProfileId,
                    normalizedJobId,
                    draft,
                    baselineMatch
            );
        }

        /*
         * Strict path:
         *
         * Generate embedding cho cả original và tailored CV.
         * Sau đó evaluate hai profile cùng thời điểm để tránh
         * lấy score persist cũ đem so với score mới.
         */
        CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
                originalEmbedding =
                candidateEmbeddingGenerator.generate(
                        draft.originalProfile()
                );

        assertCompatibleTransientEmbedding(
                baselineMatch,
                originalEmbedding
        );

        CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
                temporaryEmbedding;

        if (draft.appliedSuggestionIds().isEmpty()) {
            temporaryEmbedding =
                    originalEmbedding;
        } else {
            temporaryEmbedding =
                    candidateEmbeddingGenerator.generate(
                            draft.temporaryProfile()
                    );

            assertCompatibleTransientEmbedding(
                    baselineMatch,
                    temporaryEmbedding
            );

            assertSameEmbeddingFamily(
                    originalEmbedding,
                    temporaryEmbedding
            );
        }

        MatchingEvaluationService.EvaluationResult
                beforeEvaluation =
                matchingEvaluationService.evaluate(
                        draft.originalProfile(),
                        originalEmbedding.vector(),
                        originalEmbedding.embeddingVersion()
                );

        ScoreSnapshot before =
                toEvaluationSnapshot(
                        normalizedJobId,
                        beforeEvaluation,
                        "original CV"
                );

        MatchingEvaluationService.EvaluationResult
                afterEvaluation;

        /*
         * Không chọn suggestion nào:
         *
         * before và after phải chính xác giống nhau.
         * Không chạy lại lần hai để tránh environmental drift.
         */
        if (draft.appliedSuggestionIds().isEmpty()) {
            afterEvaluation =
                    beforeEvaluation;
        } else {
            afterEvaluation =
                    matchingEvaluationService.evaluate(
                            draft.temporaryProfile(),
                            temporaryEmbedding.vector(),
                            temporaryEmbedding.embeddingVersion()
                    );
        }

        ScoreSnapshot after =
                toEvaluationSnapshot(
                        normalizedJobId,
                        afterEvaluation,
                        "tailored CV"
                );

        return new CvTailoringPreviewResponse(
                draft.analysis().analysisId(),
                candidateProfileId,
                normalizedJobId,
                draft.analysis().rankingVersion(),
                temporaryEmbedding.embeddingVersion(),
                draft.appliedSuggestionIds(),
                before,
                after,
                afterEvaluation.retrievedCount(),
                afterEvaluation.hydratedCount()
        );
    }

    /*
     * Backward-compatible path dành riêng cho analysis context
     * được tạo trước khi exact matching-run metadata tồn tại.
     */
    private CvTailoringPreviewResponse previewLegacyContext(
            String candidateProfileId,
            String normalizedJobId,
            CvTailoringDraftService.TemporaryDraft draft,
            MatchResult baselineMatch
    ) {
        CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
                generated =
                candidateEmbeddingGenerator.generate(
                        draft.temporaryProfile()
                );

        assertCompatibleTransientEmbedding(
                baselineMatch,
                generated
        );

        MatchingEvaluationService.EvaluationResult
                afterEvaluation =
                matchingEvaluationService.evaluate(
                        draft.temporaryProfile(),
                        generated.vector(),
                        generated.embeddingVersion()
                );

        ScoreSnapshot after =
                toEvaluationSnapshot(
                        normalizedJobId,
                        afterEvaluation,
                        "tailored CV"
                );

        return new CvTailoringPreviewResponse(
                draft.analysis().analysisId(),
                candidateProfileId,
                normalizedJobId,
                draft.analysis().rankingVersion(),
                generated.embeddingVersion(),
                draft.appliedSuggestionIds(),
                toPersistedBaselineSnapshot(
                        baselineMatch
                ),
                after,
                afterEvaluation.retrievedCount(),
                afterEvaluation.hydratedCount()
        );
    }

    private MatchingRunResult loadCurrentMatchingRun(
            String candidateProfileId,
            String ownerUserId
    ) {
        try {
            return hybridMatchingService.getCurrent(
                    candidateProfileId,
                    ownerUserId
            );

        } catch (MatchingPreconditionException exception) {
            HttpStatus status =
                    switch (exception.getReason()) {
                        case AUTHENTICATION_REQUIRED ->
                                HttpStatus.UNAUTHORIZED;

                        case CANDIDATE_PROFILE_NOT_FOUND,
                             MATCH_RESULT_NOT_FOUND ->
                                HttpStatus.NOT_FOUND;

                        case READY_CANDIDATE_EMBEDDING_NOT_FOUND,
                             CANDIDATE_EMBEDDING_STALE,
                             CANDIDATE_EMBEDDING_INVALID ->
                                HttpStatus.CONFLICT;
                    };

            throw new ResponseStatusException(
                    status,
                    exception.getMessage(),
                    exception
            );
        }
    }

    private void assertSameBaseline(
            CvTailoringAnalysisStore.AnalysisContext context,
            MatchingRunResult currentRun,
            MatchResult currentTargetMatch
    ) {
        boolean sameLogicalRun =
                Objects.equals(
                        context.candidateEmbeddingId(),
                        currentRun.candidateEmbeddingId()
                )
                        && Objects.equals(
                        context.rankingVersion(),
                        currentRun.rankingVersion()
                );

        if (!sameLogicalRun) {
            throw baselineChanged();
        }

        /*
         * Context mới bind thêm generatedAt.
         *
         * Vì force matching có thể tạo run mới nhưng vẫn giữ:
         *
         * candidateEmbeddingId
         * rankingVersion
         *
         * nên generatedAt giúp phân biệt exact run.
         */
        if (context.matchingGeneratedAt() != null
                && !Objects.equals(
                context.matchingGeneratedAt(),
                currentTargetMatch.getGeneratedAt()
        )) {

            throw baselineChanged();
        }
    }

    private ResponseStatusException baselineChanged() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Matching result changed after CV tailoring analysis. "
                        + "Analyze again before previewing."
        );
    }

    private void assertCompatibleTransientEmbedding(
            MatchResult baselineMatch,
            CandidateEmbeddingGenerator.GeneratedCandidateEmbedding generated
    ) {
        Objects.requireNonNull(
                generated,
                "generated candidate embedding must not be null"
        );

        String requiredCandidateTextVersion =
                matchingProperties
                        .getCompatibility()
                        .getCandidateTextVersion();

        if (!Objects.equals(
                requiredCandidateTextVersion,
                generated.textVersion()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Temporary candidate embedding text version "
                            + "is not compatible with the matching engine"
            );
        }

        if (baselineMatch.getCandidateTextVersion() != null
                && !baselineMatch
                .getCandidateTextVersion()
                .isBlank()
                && !Objects.equals(
                baselineMatch.getCandidateTextVersion(),
                generated.textVersion()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Candidate embedding text version changed "
                            + "after analysis. Re-embed and analyze again."
            );
        }

        if (baselineMatch.getEmbeddingVersion() != null
                && !baselineMatch
                .getEmbeddingVersion()
                .isBlank()
                && !Objects.equals(
                baselineMatch.getEmbeddingVersion(),
                generated.embeddingVersion()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Embedding model version changed after analysis. "
                            + "Re-run candidate embedding and matching "
                            + "before previewing."
            );
        }
    }

    private void assertSameEmbeddingFamily(
            CandidateEmbeddingGenerator.GeneratedCandidateEmbedding original,
            CandidateEmbeddingGenerator.GeneratedCandidateEmbedding tailored
    ) {
        boolean sameFamily =
                Objects.equals(
                        original.embeddingVersion(),
                        tailored.embeddingVersion()
                )
                        && Objects.equals(
                        original.textVersion(),
                        tailored.textVersion()
                )
                        && original.dimension()
                        == tailored.dimension();

        if (!sameFamily) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Embedding configuration changed while generating "
                            + "the tailoring preview. "
                            + "Analyze again before previewing."
            );
        }
    }

    private ScoreSnapshot toPersistedBaselineSnapshot(
            MatchResult match
    ) {
        return new ScoreSnapshot(
                PreviewStatus.MATCHED,
                match.getRank(),
                match.getFinalScore(),
                match.getSemanticScore(),
                match.getSkillScore(),
                match.getSeniorityScore(),
                match.getLocationScore(),
                match.getFreshnessScore(),
                match.getSkillKnown(),
                match.getSeniorityKnown(),
                match.getLocationKnown(),
                match.getFreshnessKnown(),
                match.getMatchedSkills(),
                match.getMissingSkills(),
                null
        );
    }

    private ScoreSnapshot toEvaluationSnapshot(
            String normalizedJobId,
            MatchingEvaluationService.EvaluationResult evaluation,
            String profileLabel
    ) {
        Objects.requireNonNull(
                evaluation,
                "evaluation must not be null"
        );

        if (!evaluation.wasRetrieved(normalizedJobId)) {
            return emptySnapshot(
                    PreviewStatus.NOT_RETRIEVED,
                    "The selected job does not appear in "
                            + "the current Qdrant candidate pool for the "
                            + profileLabel
                            + "."
            );
        }

        HybridRankingService.RankedJob ranked =
                evaluation
                        .findRankedJob(normalizedJobId)
                        .orElse(null);

        if (ranked == null) {
            String reason =
                    evaluation.wasHydrated(normalizedJobId)
                            ? "The selected job was retrieved for the "
                            + profileLabel
                            + ", but it did not survive the existing "
                            + "eligibility, acceptance, or result-limit rules."
                            : "The selected job was retrieved from Qdrant "
                            + "for the "
                            + profileLabel
                            + " but could not be hydrated from "
                            + "the normalized job store.";

            return emptySnapshot(
                    PreviewStatus.NOT_MATCHED,
                    reason
            );
        }

        return new ScoreSnapshot(
                PreviewStatus.MATCHED,
                ranked.rank(),
                ranked.score().finalScore(),
                ranked.score().semanticScore(),
                ranked.score().skillScore(),
                ranked.score().seniorityScore(),
                ranked.score().locationScore(),
                ranked.score().freshnessScore(),
                ranked.score().skillKnown(),
                ranked.score().seniorityKnown(),
                ranked.score().locationKnown(),
                ranked.score().freshnessKnown(),
                ranked.matchedSkills(),
                ranked.missingSkills(),
                null
        );
    }

    private ScoreSnapshot emptySnapshot(
            PreviewStatus status,
            String reason
    ) {
        return new ScoreSnapshot(
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                reason
        );
    }

    private void requireText(
            String value,
            String fieldName
    ) {
        if (value == null
                || value.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must not be blank"
            );
        }
    }
}