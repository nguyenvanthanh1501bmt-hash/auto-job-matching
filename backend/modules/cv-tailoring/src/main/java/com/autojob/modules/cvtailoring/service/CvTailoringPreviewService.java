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

    private final CvTailoringDraftService
            draftService;

    private final HybridMatchingService
            hybridMatchingService;

    private final CandidateEmbeddingGenerator
            candidateEmbeddingGenerator;

    private final MatchingEvaluationService
            matchingEvaluationService;

    private final MatchingProperties
            matchingProperties;

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

        /*
         * Phase 2:
         *
         * analysis ownership
         * + profile unchanged
         * + accepted IDs only
         * + evidence revalidation
         * + temporary CandidateProfile
         */
        CvTailoringDraftService
                .TemporaryDraft draft =
                draftService
                        .createTemporaryDraft(
                                request.analysisId(),
                                candidateProfileId,
                                normalizedJobId,
                                ownerUserId,
                                request
                                        .acceptedSuggestionIds()
                        );

        /*
         * Before phải vẫn là EXACT matching run
         * được dùng khi Analyze.
         */
        MatchingRunResult beforeRun =
                loadCurrentMatchingRun(
                        candidateProfileId,
                        ownerUserId
                );

        assertSameBaseline(
                draft.analysis(),
                beforeRun
        );

        MatchResult beforeMatch =
                beforeRun
                        .results()
                        .stream()
                        .filter(
                                Objects::nonNull
                        )
                        .filter(
                                result ->
                                        normalizedJobId.equals(
                                                result
                                                        .getNormalizedJobId()
                                        )
                        )
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.CONFLICT,
                                                "Selected job is no longer part of the current matching result. Analyze again before previewing."
                                        )
                        );

        /*
         * Generate vector trực tiếp từ Temporary CandidateProfile.
         *
         * Không persist CandidateEmbedding.
         */
        CandidateEmbeddingGenerator
                .GeneratedCandidateEmbedding generated =
                candidateEmbeddingGenerator
                        .generate(
                                draft
                                        .temporaryProfile()
                        );

        assertCompatibleTransientEmbedding(
                beforeMatch,
                generated
        );

        /*
         * REAL matching evaluation.
         *
         * MatchingEvaluationService cũng là service
         * mà normal HybridMatchingService sử dụng.
         */
        MatchingEvaluationService
                .EvaluationResult afterEvaluation =
                matchingEvaluationService
                        .evaluate(
                                draft
                                        .temporaryProfile(),

                                generated
                                        .vector(),

                                generated
                                        .embeddingVersion()
                        );

        ScoreSnapshot after =
                toAfterSnapshot(
                        normalizedJobId,
                        afterEvaluation
                );

        return new CvTailoringPreviewResponse(
                draft
                        .analysis()
                        .analysisId(),

                candidateProfileId,

                normalizedJobId,

                draft
                        .analysis()
                        .rankingVersion(),

                generated
                        .embeddingVersion(),

                draft
                        .appliedSuggestionIds(),

                toBeforeSnapshot(
                        beforeMatch
                ),

                after,

                afterEvaluation
                        .retrievedCount(),

                afterEvaluation
                        .hydratedCount()
        );
    }

    private MatchingRunResult
    loadCurrentMatchingRun(
            String candidateProfileId,
            String ownerUserId
    ) {
        try {

            return hybridMatchingService
                    .getCurrent(
                            candidateProfileId,
                            ownerUserId
                    );

        } catch (
                MatchingPreconditionException exception
        ) {

            HttpStatus status =
                    switch (
                            exception.getReason()
                            ) {

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
            MatchingRunResult beforeRun
    ) {
        boolean sameRun =
                Objects.equals(
                        context
                                .candidateEmbeddingId(),

                        beforeRun
                                .candidateEmbeddingId()
                )

                        && Objects.equals(
                        context
                                .rankingVersion(),

                        beforeRun
                                .rankingVersion()
                );

        if (!sameRun) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Matching result changed after CV tailoring analysis. Analyze again before previewing."
            );
        }
    }

    private void assertCompatibleTransientEmbedding(
            MatchResult beforeMatch,
            CandidateEmbeddingGenerator
                    .GeneratedCandidateEmbedding generated
    ) {
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
                    "Temporary candidate embedding text version is not compatible with the matching engine"
            );
        }

        if (beforeMatch
                .getCandidateTextVersion()
                != null
                && !beforeMatch
                .getCandidateTextVersion()
                .isBlank()

                && !Objects.equals(
                beforeMatch
                        .getCandidateTextVersion(),

                generated
                        .textVersion()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Candidate embedding text version changed after analysis. Re-embed and analyze again."
            );
        }

        /*
         * Before và After phải dùng cùng embedding model.
         *
         * Nếu model config đổi giữa Analyze và Preview,
         * comparison không còn apples-to-apples.
         */
        if (beforeMatch
                .getEmbeddingVersion()
                != null
                && !beforeMatch
                .getEmbeddingVersion()
                .isBlank()

                && !Objects.equals(
                beforeMatch
                        .getEmbeddingVersion(),

                generated
                        .embeddingVersion()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Embedding model version changed after analysis. Re-run candidate embedding and matching before previewing."
            );
        }
    }

    private ScoreSnapshot toBeforeSnapshot(
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

    private ScoreSnapshot toAfterSnapshot(
            String normalizedJobId,
            MatchingEvaluationService
                    .EvaluationResult evaluation
    ) {
        /*
         * Job rớt khỏi Qdrant candidate pool.
         *
         * Không invent pair score.
         */
        if (!evaluation.wasRetrieved(
                normalizedJobId
        )) {

            return emptyAfterSnapshot(
                    PreviewStatus.NOT_RETRIEVED,

                    "The selected job no longer appears in the current Qdrant candidate pool for the tailored CV."
            );
        }

        HybridRankingService.RankedJob ranked =
                evaluation
                        .findRankedJob(
                                normalizedJobId
                        )
                        .orElse(
                                null
                        );

        /*
         * Job có trong retrieval nhưng không nằm
         * trong final production result.
         *
         * Có thể do:
         *
         * eligibility
         * acceptance
         * result-limit
         * orphan job hydration
         */
        if (ranked == null) {

            String reason =
                    evaluation.wasHydrated(
                            normalizedJobId
                    )
                            ? "The selected job was retrieved, but it did not survive the existing eligibility, acceptance, or result-limit rules."
                            : "The selected job was retrieved from Qdrant but could not be hydrated from the normalized job store.";

            return emptyAfterSnapshot(
                    PreviewStatus.NOT_MATCHED,
                    reason
            );
        }

        return new ScoreSnapshot(
                PreviewStatus.MATCHED,

                ranked.rank(),

                ranked
                        .score()
                        .finalScore(),

                ranked
                        .score()
                        .semanticScore(),

                ranked
                        .score()
                        .skillScore(),

                ranked
                        .score()
                        .seniorityScore(),

                ranked
                        .score()
                        .locationScore(),

                ranked
                        .score()
                        .freshnessScore(),

                ranked
                        .score()
                        .skillKnown(),

                ranked
                        .score()
                        .seniorityKnown(),

                ranked
                        .score()
                        .locationKnown(),

                ranked
                        .score()
                        .freshnessKnown(),

                ranked.matchedSkills(),

                ranked.missingSkills(),

                null
        );
    }

    private ScoreSnapshot emptyAfterSnapshot(
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
                    fieldName
                            + " must not be blank"
            );
        }
    }
}