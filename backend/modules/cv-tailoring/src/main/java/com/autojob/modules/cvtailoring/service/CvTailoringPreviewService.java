package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.candidateembedding.service.CandidateEmbeddingGenerator;
import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.config.CvTailoringPreviewProperties;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class CvTailoringPreviewService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    CvTailoringPreviewService.class
            );

    private final CvTailoringDraftService draftService;

    private final HybridMatchingService hybridMatchingService;

    private final CandidateEmbeddingGenerator
            candidateEmbeddingGenerator;

    private final MatchingEvaluationService
            matchingEvaluationService;

    private final MatchingProperties matchingProperties;

    private final CvTailoringPreviewProperties
            previewProperties;

    /**
     * Constructor Spring production sử dụng.
     *
     * Regression gate mặc định được bật.
     */
    @Autowired
    public CvTailoringPreviewService(
            CvTailoringDraftService draftService,
            HybridMatchingService hybridMatchingService,
            CandidateEmbeddingGenerator candidateEmbeddingGenerator,
            MatchingEvaluationService matchingEvaluationService,
            MatchingProperties matchingProperties,
            CvTailoringPreviewProperties previewProperties
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

        this.previewProperties =
                Objects.requireNonNull(
                        previewProperties,
                        "previewProperties must not be null"
                );
    }

    /**
     * Backward-compatible constructor.
     *
     * Các test fixture hiện tại đang new service trực tiếp
     * bằng constructor 5 tham số này.
     *
     * Giữ behavior cũ cho các fixture đó để không phá
     * hàng loạt test contract cũ.
     *
     * Spring production KHÔNG dùng constructor này vì
     * constructor 6 tham số phía trên đã có @Autowired.
     */
    public CvTailoringPreviewService(
            CvTailoringDraftService draftService,
            HybridMatchingService hybridMatchingService,
            CandidateEmbeddingGenerator candidateEmbeddingGenerator,
            MatchingEvaluationService matchingEvaluationService,
            MatchingProperties matchingProperties
    ) {
        this(
                draftService,
                hybridMatchingService,
                candidateEmbeddingGenerator,
                matchingEvaluationService,
                matchingProperties,
                disabledRegressionGateProperties()
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
         * Draft đầu tiên xác thực:
         *
         * - analysisId
         * - ownership
         * - selected suggestion IDs
         * - candidate/job context
         *
         * Không persistence.
         */
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

                                                "Selected job is no longer "
                                                        + "part of the current "
                                                        + "matching result. "
                                                        + "Analyze again before "
                                                        + "previewing."
                                        )
                        );

        assertSameBaseline(
                draft.analysis(),
                baselineRun,
                baselineMatch
        );

        /*
         * Legacy context.
         *
         * Context cũ không có matchingGeneratedAt nên
         * không thể đảm bảo exact before/after transient
         * comparison.
         *
         * Fresh production Analyze luôn đi strict path
         * phía dưới.
         */
        if (draft
                .analysis()
                .matchingGeneratedAt()
                == null) {

            return previewLegacyContext(
                    candidateProfileId,
                    normalizedJobId,
                    draft,
                    baselineMatch
            );
        }

        /*
         * =========================
         * STRICT PRODUCTION PATH
         * =========================
         *
         * Baseline cũng được generate embedding tạm thời
         * và evaluate lại.
         *
         * Nhờ vậy Before và After dùng:
         *
         * - cùng embedding model
         * - cùng candidate text version
         * - cùng matching code
         * - cùng thời điểm
         *
         * Không đem persisted score cũ so với score mới.
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

        /*
         * Không chọn suggestion:
         *
         * before === after tuyệt đối.
         */
        if (draft
                .appliedSuggestionIds()
                .isEmpty()) {

            return new CvTailoringPreviewResponse(
                    draft
                            .analysis()
                            .analysisId(),

                    candidateProfileId,

                    normalizedJobId,

                    draft
                            .analysis()
                            .rankingVersion(),

                    originalEmbedding
                            .embeddingVersion(),

                    List.of(),

                    before,

                    before,

                    beforeEvaluation
                            .retrievedCount(),

                    beforeEvaluation
                            .hydratedCount()
            );
        }

        /*
         * Production safety gate.
         *
         * Đây là phần sửa bug:
         *
         * BEFORE 61.19
         * AFTER  60.31
         *
         * Từ giờ suggestion làm giảm target-job score
         * sẽ bị loại khỏi appliedSuggestionIds.
         */
        if (previewProperties
                .isPreventScoreRegression()) {

            RegressionGateResult gateResult =
                    evaluateNonRegressingSuggestions(
                            candidateProfileId,
                            normalizedJobId,
                            ownerUserId,
                            draft,
                            baselineMatch,
                            originalEmbedding,
                            beforeEvaluation
                    );

            ScoreSnapshot after =
                    toEvaluationSnapshot(
                            normalizedJobId,
                            gateResult
                                    .evaluation(),
                            "tailored CV"
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

                    gateResult
                            .embedding()
                            .embeddingVersion(),

                    gateResult
                            .appliedSuggestionIds(),

                    before,

                    after,

                    gateResult
                            .evaluation()
                            .retrievedCount(),

                    gateResult
                            .evaluation()
                            .hydratedCount()
            );
        }

        /*
         * Compatibility path dành cho những test fixture
         * instantiate constructor cũ.
         */
        EvaluatedTemporaryProfile evaluated =
                evaluateTemporaryProfile(
                        draft.temporaryProfile(),
                        baselineMatch,
                        originalEmbedding
                );

        ScoreSnapshot after =
                toEvaluationSnapshot(
                        normalizedJobId,
                        evaluated.evaluation(),
                        "tailored CV"
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

                evaluated
                        .embedding()
                        .embeddingVersion(),

                draft
                        .appliedSuggestionIds(),

                before,

                after,

                evaluated
                        .evaluation()
                        .retrievedCount(),

                evaluated
                        .evaluation()
                        .hydratedCount()
        );
    }

    /**
     * Greedy target-job quality gate.
     *
     * Ví dụ:
     *
     * baseline = 61.19
     *
     * suggestion A:
     * 61.19 -> 60.31
     * => reject A
     *
     * suggestion B:
     * 61.19 -> 61.80
     * => accept B
     *
     * suggestion C:
     * B + C:
     * 61.80 -> 61.65
     * => reject C
     *
     * Final:
     * 61.19 -> 61.80
     *
     * Vì mỗi bước accepted phải non-regressing,
     * combination cuối cùng cũng không thể thấp hơn
     * baseline.
     *
     * Không gọi LLM ở đây.
     */
    private RegressionGateResult
    evaluateNonRegressingSuggestions(
            String candidateProfileId,
            String normalizedJobId,
            String ownerUserId,
            CvTailoringDraftService.TemporaryDraft initialDraft,
            MatchResult baselineMatch,
            CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
                    originalEmbedding,
            MatchingEvaluationService.EvaluationResult
                    beforeEvaluation
    ) {
        HybridRankingService.RankedJob currentTarget =
                beforeEvaluation
                        .findRankedJob(
                                normalizedJobId
                        )
                        .orElse(
                                null
                        );

        /*
         * Nếu chính transient baseline không còn target
         * job trong ranked result thì không có numeric
         * baseline đáng tin để quality-gate.
         *
         * Safe choice: apply 0 suggestion.
         */
        if (currentTarget == null) {

            log.info(
                    "CV tailoring preview regression gate "
                            + "skipped all changes "
                            + "reason=baseline-target-not-ranked "
                            + "requested={}",
                    initialDraft
                            .appliedSuggestionIds()
                            .size()
            );

            return new RegressionGateResult(
                    List.of(),
                    originalEmbedding,
                    beforeEvaluation
            );
        }

        List<String> acceptedIds =
                new ArrayList<>();

        CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
                currentEmbedding =
                originalEmbedding;

        MatchingEvaluationService.EvaluationResult
                currentEvaluation =
                beforeEvaluation;

        double epsilon =
                Math.max(
                        0.0d,
                        previewProperties
                                .getScoreEpsilon()
                );

        /*
         * Giữ đúng thứ tự suggestion FE gửi lên.
         *
         * FE hiện đã ưu tiên POSITION / REWRITE,
         * vì vậy quality gate cũng đánh giá theo thứ tự
         * recommendation đó.
         */
        for (String suggestionId :
                initialDraft
                        .appliedSuggestionIds()) {

            List<String> trialIds =
                    new ArrayList<>(
                            acceptedIds
                    );

            trialIds.add(
                    suggestionId
            );

            /*
             * Apply:
             *
             * previously accepted suggestions
             * +
             * current candidate suggestion
             */
            CvTailoringDraftService.TemporaryDraft
                    trialDraft =
                    draftService.createTemporaryDraft(
                            initialDraft
                                    .analysis()
                                    .analysisId(),

                            candidateProfileId,

                            normalizedJobId,

                            ownerUserId,

                            trialIds
                    );

            EvaluatedTemporaryProfile trial =
                    evaluateTemporaryProfile(
                            trialDraft
                                    .temporaryProfile(),

                            baselineMatch,

                            originalEmbedding
                    );

            HybridRankingService.RankedJob trialTarget =
                    trial
                            .evaluation()
                            .findRankedJob(
                                    normalizedJobId
                            )
                            .orElse(
                                    null
                            );

            if (!isNonRegressing(
                    currentTarget,
                    trialTarget,
                    epsilon
            )) {

                /*
                 * Chỉ log ID + score/rank.
                 *
                 * Không log CV text/JD/personal data.
                 */
                log.info(
                        "CV tailoring preview dropped selected "
                                + "suggestion "
                                + "suggestionId={} "
                                + "currentScore={} "
                                + "trialScore={} "
                                + "currentRank={} "
                                + "trialRank={}",

                        suggestionId,

                        currentTarget
                                .score()
                                .finalScore(),

                        trialTarget == null
                                ? null
                                : trialTarget
                                .score()
                                .finalScore(),

                        currentTarget.rank(),

                        trialTarget == null
                                ? null
                                : trialTarget.rank()
                );

                continue;
            }

            /*
             * Suggestion an toàn về score.
             */
            acceptedIds.add(
                    suggestionId
            );

            currentEmbedding =
                    trial.embedding();

            currentEvaluation =
                    trial.evaluation();

            currentTarget =
                    trialTarget;
        }

        log.info(
                "CV tailoring preview regression gate "
                        + "completed "
                        + "requested={} "
                        + "applied={} "
                        + "dropped={}",

                initialDraft
                        .appliedSuggestionIds()
                        .size(),

                acceptedIds.size(),

                initialDraft
                        .appliedSuggestionIds()
                        .size()
                        - acceptedIds.size()
        );

        return new RegressionGateResult(
                List.copyOf(
                        acceptedIds
                ),

                currentEmbedding,

                currentEvaluation
        );
    }

    /**
     * Rule tổng quát.
     *
     * Không quan tâm CV thuộc:
     *
     * - software
     * - logistics
     * - finance
     * - HR
     * - engineering
     * - marketing
     * - ...
     *
     * Chỉ so target-job matching result.
     */
    private boolean isNonRegressing(
            HybridRankingService.RankedJob current,
            HybridRankingService.RankedJob trial,
            double epsilon
    ) {
        /*
         * Suggestion làm target biến mất khỏi final ranked
         * result => reject.
         */
        if (current == null
                || trial == null) {

            return false;
        }

        double currentScore =
                current
                        .score()
                        .finalScore();

        double trialScore =
                trial
                        .score()
                        .finalScore();

        double delta =
                trialScore
                        - currentScore;

        /*
         * Final score giảm quá epsilon => reject.
         */
        if (delta < -epsilon) {

            return false;
        }

        /*
         * Nếu score thực chất bằng nhau:
         *
         * không chấp nhận việc rank job mục tiêu xấu đi.
         *
         * Example:
         *
         * score 61.19 rank #4
         * ->
         * score 61.19 rank #5
         *
         * => reject.
         */
        if (Math.abs(
                delta
        ) <= epsilon
                && trial.rank()
                > current.rank()) {

            return false;
        }

        return true;
    }

    private EvaluatedTemporaryProfile
    evaluateTemporaryProfile(
            CandidateProfile profile,
            MatchResult baselineMatch,
            CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
                    originalEmbedding
    ) {
        CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
                generated =
                candidateEmbeddingGenerator.generate(
                        profile
                );

        assertCompatibleTransientEmbedding(
                baselineMatch,
                generated
        );

        assertSameEmbeddingFamily(
                originalEmbedding,
                generated
        );

        MatchingEvaluationService.EvaluationResult
                evaluation =
                matchingEvaluationService.evaluate(
                        profile,
                        generated.vector(),
                        generated.embeddingVersion()
                );

        return new EvaluatedTemporaryProfile(
                generated,
                evaluation
        );
    }

    /*
     * Backward-compatible path dành riêng cho analysis
     * context được tạo trước khi exact matching-run
     * metadata tồn tại.
     */
    private CvTailoringPreviewResponse
    previewLegacyContext(
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

                toPersistedBaselineSnapshot(
                        baselineMatch
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
                MatchingPreconditionException
                        exception
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
            MatchingRunResult currentRun,
            MatchResult currentTargetMatch
    ) {
        boolean sameLogicalRun =
                Objects.equals(
                        context
                                .candidateEmbeddingId(),

                        currentRun
                                .candidateEmbeddingId()
                )
                        && Objects.equals(
                        context
                                .rankingVersion(),

                        currentRun
                                .rankingVersion()
                );

        if (!sameLogicalRun) {

            throw baselineChanged();
        }

        /*
         * generatedAt phân biệt exact matching run.
         */
        if (context
                .matchingGeneratedAt()
                != null
                && !Objects.equals(
                context
                        .matchingGeneratedAt(),

                currentTargetMatch
                        .getGeneratedAt()
        )) {

            throw baselineChanged();
        }
    }

    private ResponseStatusException
    baselineChanged() {

        return new ResponseStatusException(
                HttpStatus.CONFLICT,

                "Matching result changed after "
                        + "CV tailoring analysis. "
                        + "Analyze again before previewing."
        );
    }

    private void
    assertCompatibleTransientEmbedding(
            MatchResult baselineMatch,
            CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
                    generated
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

                    "Temporary candidate embedding "
                            + "text version is not "
                            + "compatible with the "
                            + "matching engine"
            );
        }

        if (baselineMatch
                .getCandidateTextVersion()
                != null
                && !baselineMatch
                .getCandidateTextVersion()
                .isBlank()
                && !Objects.equals(
                baselineMatch
                        .getCandidateTextVersion(),

                generated
                        .textVersion()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,

                    "Candidate embedding text version "
                            + "changed after analysis. "
                            + "Re-embed and analyze again."
            );
        }

        if (baselineMatch
                .getEmbeddingVersion()
                != null
                && !baselineMatch
                .getEmbeddingVersion()
                .isBlank()
                && !Objects.equals(
                baselineMatch
                        .getEmbeddingVersion(),

                generated
                        .embeddingVersion()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,

                    "Embedding model version changed "
                            + "after analysis. "
                            + "Re-run candidate embedding "
                            + "and matching before previewing."
            );
        }
    }

    private void assertSameEmbeddingFamily(
            CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
                    original,

            CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
                    tailored
    ) {
        boolean sameFamily =
                Objects.equals(
                        original
                                .embeddingVersion(),

                        tailored
                                .embeddingVersion()
                )
                        && Objects.equals(
                        original
                                .textVersion(),

                        tailored
                                .textVersion()
                )
                        && original.dimension()
                        == tailored.dimension();

        if (!sameFamily) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,

                    "Embedding configuration changed "
                            + "while generating the "
                            + "tailoring preview. "
                            + "Analyze again before previewing."
            );
        }
    }

    private ScoreSnapshot
    toPersistedBaselineSnapshot(
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

    private ScoreSnapshot
    toEvaluationSnapshot(
            String normalizedJobId,
            MatchingEvaluationService.EvaluationResult evaluation,
            String profileLabel
    ) {
        Objects.requireNonNull(
                evaluation,
                "evaluation must not be null"
        );

        if (!evaluation.wasRetrieved(
                normalizedJobId
        )) {

            return emptySnapshot(
                    PreviewStatus.NOT_RETRIEVED,

                    "The selected job does not appear "
                            + "in the current Qdrant "
                            + "candidate pool for the "
                            + profileLabel
                            + "."
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

        if (ranked == null) {

            String reason =
                    evaluation.wasHydrated(
                            normalizedJobId
                    )
                            ? "The selected job was "
                            + "retrieved for the "
                            + profileLabel
                            + ", but it did not survive "
                            + "the existing eligibility, "
                            + "acceptance, or result-limit "
                            + "rules."

                            : "The selected job was "
                            + "retrieved from Qdrant for "
                            + "the "
                            + profileLabel
                            + " but could not be hydrated "
                            + "from the normalized job store.";

            return emptySnapshot(
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

                ranked
                        .matchedSkills(),

                ranked
                        .missingSkills(),

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

                    fieldName
                            + " must not be blank"
            );
        }
    }

    /**
     * Existing direct-constructor tests continue to verify
     * the historical preview behavior.
     *
     * Spring runtime uses the @Autowired constructor and
     * therefore receives the real property bean where the
     * gate defaults to true.
     */
    private static CvTailoringPreviewProperties
    disabledRegressionGateProperties() {

        CvTailoringPreviewProperties properties =
                new CvTailoringPreviewProperties();

        properties.setPreventScoreRegression(
                false
        );

        return properties;
    }

    private record EvaluatedTemporaryProfile(
            CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
            embedding,

            MatchingEvaluationService.EvaluationResult
            evaluation
    ) {
    }

    private record RegressionGateResult(
            List<String> appliedSuggestionIds,

            CandidateEmbeddingGenerator.GeneratedCandidateEmbedding
            embedding,

            MatchingEvaluationService.EvaluationResult
            evaluation
    ) {

        private RegressionGateResult {

            appliedSuggestionIds =
                    appliedSuggestionIds == null
                            ? List.of()
                            : List.copyOf(
                            appliedSuggestionIds
                    );
        }
    }
}