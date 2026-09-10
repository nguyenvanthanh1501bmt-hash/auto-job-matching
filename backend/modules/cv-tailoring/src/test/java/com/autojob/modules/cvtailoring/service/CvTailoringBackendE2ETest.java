package com.autojob.modules.cvtailoring.service;

import com.autojob.common.embedding.client.EmbeddingClient;
import com.autojob.common.embedding.client.dto.EmbeddingResponse;
import com.autojob.common.embedding.config.EmbeddingProperties;
import com.autojob.common.embedding.service.EmbeddingTextHashCalculator;
import com.autojob.modules.candidateembedding.config.CandidateEmbeddingProperties;
import com.autojob.modules.candidateembedding.service.CandidateEmbeddingGenerator;
import com.autojob.modules.candidateembedding.text.CandidateEmbeddingTextBuilder;
import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.FailureReason;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.ProviderException;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteRequest;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteResponse;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteSuggestion;
import com.autojob.modules.cvtailoring.ai.CvRewriteProviderRouter;
import com.autojob.modules.cvtailoring.config.CvRewriteSafetyTaxonomyProperties;
import com.autojob.modules.cvtailoring.config.CvTailoringAiProperties;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionType;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewRequest;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringPreviewResponse.PreviewStatus;
import com.autojob.modules.jobembedding.search.JobVectorHit;
import com.autojob.modules.jobembedding.search.JobVectorSearchCriteria;
import com.autojob.modules.jobembedding.search.JobVectorSearchPort;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.normalization.SkillNormalizer;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import com.autojob.modules.matching.config.MatchingProperties;
import com.autojob.modules.matching.contract.MatchingRunResult;
import com.autojob.modules.matching.domain.HybridScore;
import com.autojob.modules.matching.domain.MatchResult;
import com.autojob.modules.matching.service.HybridMatchingService;
import com.autojob.modules.matching.service.HybridRankingService;
import com.autojob.modules.matching.service.MatchingEvaluationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvTailoringBackendE2ETest {

    private static final String USER_ID = "user-1";
    private static final String CANDIDATE_ID = "candidate-1";
    private static final String JOB_ID = "job-1";
    private static final String EMBEDDING_ID = "embedding-1";
    private static final String RANKING_VERSION = "hybrid-v1";
    private static final String TEXT_VERSION = "candidate-text-v1";

    private static final String EMBEDDING_VERSION =
            "test-model@rev|prep-v1|l2";

    private static final String SOURCE_ID =
            "work:0:responsibility:0";

    private static final Clock CLOCK =
            Clock.fixed(
                    Instant.parse(
                            "2026-09-10T08:00:00Z"
                    ),
                    ZoneOffset.UTC
            );

    @Test
    void itRewriteFlowsThroughTemporaryProfileAndSharedMatchingEvaluation() {
        Scenario scenario =
                new Scenario(
                        "Backend Developer",
                        "Developed backend services.",
                        List.of(
                                "Java",
                                "Spring Boot"
                        ),
                        List.of(
                                "Java",
                                "Spring Boot"
                        ),
                        List.of(
                                "Java",
                                "Spring Boot"
                        ),
                        List.of(),
                        "Developed backend services using Java and Spring Boot.",
                        List.of(
                                "Java",
                                "Spring Boot"
                        )
                );

        Harness harness =
                harness(
                        scenario,
                        truthfulRewrite(
                                scenario
                        )
                );

        CvTailoringAnalyzeResponse analysis =
                harness.analysisService.analyze(
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID
                );

        SuggestionItem rewrite =
                rewrites(
                        analysis
                ).getFirst();

        HybridRankingService.RankedJob afterRanked =
                new HybridRankingService.RankedJob(
                        1,
                        harness.job,
                        "point-after",
                        new HybridScore(
                                0.64d,
                                0.68d,
                                0.60d,
                                0.80d,
                                1.00d,
                                0.90d,
                                true,
                                true,
                                true,
                                true
                        ),
                        scenario.matchedSkills(),
                        scenario.missingSkills()
                );

        when(
                harness.rankingService.rank(
                        any(CandidateProfile.class),
                        anyList(),
                        anyInt()
                )
        ).thenReturn(
                List.of(
                        afterRanked
                )
        );

        CvTailoringPreviewResponse preview =
                harness.previewService.preview(
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID,
                        new CvTailoringPreviewRequest(
                                analysis.analysisId(),
                                List.of(
                                        rewrite.id()
                                )
                        )
                );

        assertThat(
                preview.before().finalScore()
        ).isEqualTo(
                0.67d
        );

        assertThat(
                preview.after().status()
        ).isEqualTo(
                PreviewStatus.MATCHED
        );

        assertThat(
                preview.after().finalScore()
        ).isEqualTo(
                0.64d
        );

        assertThat(
                preview.appliedSuggestionIds()
        ).containsExactly(
                rewrite.id()
        );

        assertThat(
                responsibility(
                        harness.profile
                )
        ).isEqualTo(
                scenario.original()
        );

        ArgumentCaptor<CandidateProfile> profileCaptor =
                ArgumentCaptor.forClass(
                        CandidateProfile.class
                );

        verify(
                harness.rankingService
        ).rank(
                profileCaptor.capture(),
                anyList(),
                anyInt()
        );

        assertThat(
                responsibility(
                        profileCaptor.getValue()
                )
        ).isEqualTo(
                scenario.suggested()
        );

        verify(
                harness.embeddingClient
        ).embed(
                any(String.class)
        );

        verify(
                harness.searchPort
        ).search(
                anyList(),
                any(
                        JobVectorSearchCriteria.class
                )
        );
    }

    @Test
    void accountingSalesAndLogisticsKeepRewritesGroundedInExistingEvidence() {
        List<Scenario> scenarios =
                List.of(
                        new Scenario(
                                "Accountant",
                                "Prepared monthly reports.",
                                List.of(
                                        "Excel",
                                        "Financial Reporting"
                                ),
                                List.of(
                                        "Excel",
                                        "Financial Reporting"
                                ),
                                List.of(
                                        "Excel",
                                        "Financial Reporting"
                                ),
                                List.of(),
                                "Prepared monthly financial reports using Excel.",
                                List.of(
                                        "Excel",
                                        "Financial Reporting"
                                )
                        ),
                        new Scenario(
                                "B2B Account Executive",
                                "Managed business clients.",
                                List.of(
                                        "B2B Sales"
                                ),
                                List.of(
                                        "B2B Sales",
                                        "Account Management"
                                ),
                                List.of(
                                        "B2B Sales"
                                ),
                                List.of(
                                        "Account Management"
                                ),
                                "Managed B2B client accounts.",
                                List.of(
                                        "B2B Sales"
                                )
                        ),
                        new Scenario(
                                "Warehouse Coordinator",
                                "Handled inventory.",
                                List.of(
                                        "Inventory Control"
                                ),
                                List.of(
                                        "Inventory Control",
                                        "WMS"
                                ),
                                List.of(
                                        "Inventory Control"
                                ),
                                List.of(
                                        "WMS"
                                ),
                                "Handled inventory control activities.",
                                List.of(
                                        "Inventory Control"
                                )
                        )
                );

        for (Scenario scenario : scenarios) {
            Harness harness =
                    harness(
                            scenario,
                            truthfulRewrite(
                                    scenario
                            )
                    );

            CvTailoringAnalyzeResponse analysis =
                    harness.analysisService.analyze(
                            CANDIDATE_ID,
                            JOB_ID,
                            USER_ID
                    );

            assertThat(
                    rewrites(
                            analysis
                    )
            )
                    .extracting(
                            SuggestionItem::suggested
                    )
                    .contains(
                            scenario.suggested()
                    );

            List<String> gaps =
                    analysis
                            .gaps()
                            .stream()
                            .map(
                                    CvTailoringAnalyzeResponse
                                            .GapItem::skill
                            )
                            .toList();

            assertThat(
                    gaps
            ).containsAll(
                    scenario.missingSkills()
            );

            List<String> generatedText =
                    analysis
                            .suggestions()
                            .stream()
                            .map(
                                    SuggestionItem::suggested
                            )
                            .filter(
                                    value ->
                                            value != null
                            )
                            .map(
                                    String::toLowerCase
                            )
                            .toList();

            assertThat(
                    generatedText
            ).noneMatch(
                    value ->
                            value.contains(
                                    "crm"
                            )
                                    || value.contains(
                                    "revenue"
                            )
                                    || value.contains(
                                    "quota"
                            )
                                    || value.contains(
                                    "wms"
                            )
            );
        }
    }

    @Test
    void rejectsFabricatedCertificationWrongScopeAndSeniorityInflation() {
        Scenario certification =
                new Scenario(
                        "Clinical Assistant",
                        "Provided patient care.",
                        List.of(
                                "Patient Care"
                        ),
                        List.of(
                                "Patient Care",
                                "Registered Nurse License"
                        ),
                        List.of(
                                "Patient Care"
                        ),
                        List.of(
                                "Registered Nurse License"
                        ),
                        "Provided patient care as a licensed registered nurse.",
                        List.of(
                                "Patient Care"
                        )
                );

        CvTailoringAnalyzeResponse certificationAnalysis =
                harness(
                        certification,
                        truthfulRewrite(
                                certification
                        )
                )
                        .analysisService
                        .analyze(
                                CANDIDATE_ID,
                                JOB_ID,
                                USER_ID
                        );

        assertThat(
                rewrites(
                        certificationAnalysis
                )
        ).isEmpty();

        assertThat(
                certificationAnalysis.gaps()
        )
                .extracting(
                        CvTailoringAnalyzeResponse
                                .GapItem::skill
                )
                .contains(
                        "Registered Nurse License"
                );

        Scenario wrongScope =
                new Scenario(
                        "Cloud Engineer",
                        "Maintained internal services.",
                        List.of(
                                "Operations"
                        ),
                        List.of(
                                "Operations",
                                "AWS"
                        ),
                        List.of(
                                "Operations",
                                "AWS"
                        ),
                        List.of(),
                        "Maintained AWS internal services.",
                        List.of(
                                "AWS"
                        )
                );

        Harness wrongScopeHarness =
                harnessWithProjectEvidence(
                        wrongScope,
                        request ->
                                new RewriteResponse(
                                        List.of(
                                                new RewriteSuggestion(
                                                        SOURCE_ID,
                                                        wrongScope.suggested(),
                                                        wrongScope.targetSkills(),
                                                        List.of(
                                                                SOURCE_ID,
                                                                "project:0:skill:0"
                                                        )
                                                )
                                        )
                                )
                );

        CvTailoringAnalyzeResponse wrongScopeAnalysis =
                wrongScopeHarness
                        .analysisService
                        .analyze(
                                CANDIDATE_ID,
                                JOB_ID,
                                USER_ID
                        );

        assertThat(
                rewrites(
                        wrongScopeAnalysis
                )
        ).isEmpty();

        Scenario seniority =
                new Scenario(
                        "Operations Specialist",
                        "Worked in operations for 2 years.",
                        List.of(
                                "Operations"
                        ),
                        List.of(
                                "Operations"
                        ),
                        List.of(
                                "Operations"
                        ),
                        List.of(),
                        "Worked in operations for 5 years.",
                        List.of(
                                "Operations"
                        )
                );

        CvTailoringAnalyzeResponse seniorityAnalysis =
                harness(
                        seniority,
                        truthfulRewrite(
                                seniority
                        )
                )
                        .analysisService
                        .analyze(
                                CANDIDATE_ID,
                                JOB_ID,
                                USER_ID
                        );

        assertThat(
                rewrites(
                        seniorityAnalysis
                )
        ).isEmpty();
    }

    @Test
    void previewReturnsNullAfterScoreWhenTargetIsNotRetrievedOrNotMatched() {
        Scenario scenario =
                new Scenario(
                        "Warehouse Coordinator",
                        "Handled inventory.",
                        List.of(
                                "Inventory Control"
                        ),
                        List.of(
                                "Inventory Control"
                        ),
                        List.of(
                                "Inventory Control"
                        ),
                        List.of(),
                        null,
                        List.of()
                );

        Function<RewriteRequest, RewriteResponse> noRewrite =
                request ->
                        new RewriteResponse(
                                List.of()
                        );

        Harness notRetrievedHarness =
                harness(
                        scenario,
                        noRewrite
                );

        when(
                notRetrievedHarness
                        .searchPort
                        .search(
                                anyList(),
                                any(
                                        JobVectorSearchCriteria.class
                                )
                        )
        ).thenReturn(
                List.of()
        );

        CvTailoringAnalyzeResponse notRetrievedAnalysis =
                notRetrievedHarness
                        .analysisService
                        .analyze(
                                CANDIDATE_ID,
                                JOB_ID,
                                USER_ID
                        );

        CvTailoringPreviewResponse notRetrieved =
                notRetrievedHarness
                        .previewService
                        .preview(
                                CANDIDATE_ID,
                                JOB_ID,
                                USER_ID,
                                new CvTailoringPreviewRequest(
                                        notRetrievedAnalysis
                                                .analysisId(),
                                        List.of()
                                )
                        );

        assertThat(
                notRetrieved.after().status()
        ).isEqualTo(
                PreviewStatus.NOT_RETRIEVED
        );

        assertThat(
                notRetrieved.after().finalScore()
        ).isNull();

        Harness notMatchedHarness =
                harness(
                        scenario,
                        noRewrite
                );

        CvTailoringAnalyzeResponse notMatchedAnalysis =
                notMatchedHarness
                        .analysisService
                        .analyze(
                                CANDIDATE_ID,
                                JOB_ID,
                                USER_ID
                        );

        CvTailoringPreviewResponse notMatched =
                notMatchedHarness
                        .previewService
                        .preview(
                                CANDIDATE_ID,
                                JOB_ID,
                                USER_ID,
                                new CvTailoringPreviewRequest(
                                        notMatchedAnalysis
                                                .analysisId(),
                                        List.of()
                                )
                        );

        assertThat(
                notMatched.after().status()
        ).isEqualTo(
                PreviewStatus.NOT_MATCHED
        );

        assertThat(
                notMatched.after().finalScore()
        ).isNull();
    }

    @Test
    void llmFailureKeepsRuleBasedEmphasizeAndGapWarnings() {
        Scenario scenario =
                new Scenario(
                        "Warehouse Coordinator",
                        "Handled inventory.",
                        List.of(
                                "Inventory Control"
                        ),
                        List.of(
                                "Inventory Control",
                                "WMS"
                        ),
                        List.of(
                                "Inventory Control"
                        ),
                        List.of(
                                "WMS"
                        ),
                        null,
                        List.of()
                );

        Harness harness =
                harness(
                        scenario,
                        request -> {
                            throw new ProviderException(
                                    FailureReason.SERVER_ERROR,
                                    503,
                                    "provider unavailable"
                            );
                        }
                );

        CvTailoringAnalyzeResponse analysis =
                harness.analysisService.analyze(
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID
                );

        assertThat(
                rewrites(
                        analysis
                )
        ).isEmpty();

        assertThat(
                analysis.suggestions()
        )
                .extracting(
                        SuggestionItem::type
                )
                .contains(
                        SuggestionType.EMPHASIZE
                );

        assertThat(
                analysis.gaps()
        )
                .extracting(
                        CvTailoringAnalyzeResponse
                                .GapItem::skill
                )
                .contains(
                        "WMS"
                );
    }

    @Test
    void previewRejectsChangedProfileAndWrongOwnerFromStoredAnalysisContext() {
        Scenario scenario =
                new Scenario(
                        "Accountant",
                        "Prepared reports.",
                        List.of(
                                "Excel"
                        ),
                        List.of(
                                "Excel"
                        ),
                        List.of(
                                "Excel"
                        ),
                        List.of(),
                        "Prepared reports using Excel.",
                        List.of(
                                "Excel"
                        )
                );

        Harness changedProfileHarness =
                harness(
                        scenario,
                        truthfulRewrite(
                                scenario
                        )
                );

        CvTailoringAnalyzeResponse changedProfileAnalysis =
                changedProfileHarness
                        .analysisService
                        .analyze(
                                CANDIDATE_ID,
                                JOB_ID,
                                USER_ID
                        );

        changedProfileHarness
                .profile
                .setUpdatedAt(
                        Instant.parse(
                                "2026-09-10T09:00:00Z"
                        )
                );

        assertThatThrownBy(
                () ->
                        changedProfileHarness
                                .previewService
                                .preview(
                                        CANDIDATE_ID,
                                        JOB_ID,
                                        USER_ID,
                                        new CvTailoringPreviewRequest(
                                                changedProfileAnalysis
                                                        .analysisId(),
                                                List.of()
                                        )
                                )
        )
                .isInstanceOf(
                        ResponseStatusException.class
                )
                .hasMessageContaining(
                        "Candidate profile changed"
                );

        Harness wrongOwnerHarness =
                harness(
                        scenario,
                        truthfulRewrite(
                                scenario
                        )
                );

        CvTailoringAnalyzeResponse wrongOwnerAnalysis =
                wrongOwnerHarness
                        .analysisService
                        .analyze(
                                CANDIDATE_ID,
                                JOB_ID,
                                USER_ID
                        );

        assertThatThrownBy(
                () ->
                        wrongOwnerHarness
                                .previewService
                                .preview(
                                        CANDIDATE_ID,
                                        JOB_ID,
                                        "user-2",
                                        new CvTailoringPreviewRequest(
                                                wrongOwnerAnalysis
                                                        .analysisId(),
                                                List.of()
                                        )
                                )
        )
                .isInstanceOf(
                        ResponseStatusException.class
                )
                .hasMessageContaining(
                        "was not found"
                );
    }

    private Function<RewriteRequest, RewriteResponse>
    truthfulRewrite(
            Scenario scenario
    ) {
        return request -> {
            List<String> evidenceIds =
                    new ArrayList<>();

            evidenceIds.add(
                    SOURCE_ID
            );

            for (String targetSkill :
                    scenario.targetSkills()) {

                for (
                        int index = 0;
                        index
                                < scenario
                                .evidenceSkills()
                                .size();
                        index++
                ) {
                    if (
                            targetSkill.equalsIgnoreCase(
                                    scenario
                                            .evidenceSkills()
                                            .get(index)
                            )
                    ) {
                        evidenceIds.add(
                                "work:0:skill:"
                                        + index
                        );
                    }
                }
            }

            return new RewriteResponse(
                    List.of(
                            new RewriteSuggestion(
                                    SOURCE_ID,
                                    scenario.suggested(),
                                    scenario.targetSkills(),
                                    List.copyOf(
                                            evidenceIds
                                    )
                            )
                    )
            );
        };
    }

    private Harness harness(
            Scenario scenario,
            Function<RewriteRequest, RewriteResponse>
                    providerBehavior
    ) {
        return harness(
                scenario,
                providerBehavior,
                false
        );
    }

    private Harness harnessWithProjectEvidence(
            Scenario scenario,
            Function<RewriteRequest, RewriteResponse>
                    providerBehavior
    ) {
        return harness(
                scenario,
                providerBehavior,
                true
        );
    }

    private Harness harness(
            Scenario scenario,
            Function<RewriteRequest, RewriteResponse>
                    providerBehavior,
            boolean addProjectEvidence
    ) {
        SkillNormalizer skillNormalizer =
                mock(
                        SkillNormalizer.class
                );

        when(
                skillNormalizer.normalize(
                        anyList()
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(
                                0
                        )
        );

        CvEvidenceService evidenceService =
                new CvEvidenceService(
                        skillNormalizer
                );

        CvSourceIdResolver sourceIdResolver =
                new CvSourceIdResolver();

        CvSuggestionValidator suggestionValidator =
                new CvSuggestionValidator(
                        evidenceService,
                        sourceIdResolver
                );

        CvSuggestionApplier suggestionApplier =
                new CvSuggestionApplier(
                        evidenceService,
                        sourceIdResolver
                );

        CvTailoringAiProperties aiProperties =
                new CvTailoringAiProperties();

        aiProperties.setEnabled(
                true
        );

        CvRewriteCandidateSelector candidateSelector =
                new CvRewriteCandidateSelector(
                        aiProperties,
                        evidenceService,
                        sourceIdResolver
                );

        CvRewriteSafetyTaxonomyProperties safetyTaxonomy =
                testSafetyTaxonomy();

        CvRewriteSafetyGuard safetyGuard =
                new CvRewriteSafetyGuard(
                        evidenceService,
                        safetyTaxonomy
                );

        CvRewriteProviderRouter providerRouter =
                new CvRewriteProviderRouter(
                        List.of(
                                new TestProvider(
                                        providerBehavior
                                )
                        ),
                        aiProperties,
                        CLOCK
                );

        CvSuggestionService suggestionService =
                new CvSuggestionService(
                        aiProperties,
                        candidateSelector,
                        providerRouter,
                        safetyGuard,
                        suggestionValidator,
                        evidenceService,
                        sourceIdResolver,
                        CLOCK
                );

        CvTailoringAnalysisStore analysisStore =
                new CvTailoringAnalysisStore(
                        CLOCK
                );

        CandidateProfileRepository profileRepository =
                mock(
                        CandidateProfileRepository.class
                );

        NormalizedJobRepository jobRepository =
                mock(
                        NormalizedJobRepository.class
                );

        HybridMatchingService hybridMatchingService =
                mock(
                        HybridMatchingService.class
                );

        CandidateProfile profile =
                profile(
                        scenario,
                        addProjectEvidence
                );

        NormalizedJob job =
                job(
                        scenario
                );

        MatchResult beforeMatch =
                beforeMatch(
                        scenario
                );

        MatchingRunResult beforeRun =
                new MatchingRunResult(
                        CANDIDATE_ID,
                        EMBEDDING_ID,
                        RANKING_VERSION,
                        0,
                        0,
                        1,
                        true,
                        List.of(
                                beforeMatch
                        )
                );

        when(
                profileRepository.findById(
                        CANDIDATE_ID
                )
        ).thenReturn(
                Optional.of(
                        profile
                )
        );

        when(
                jobRepository.findById(
                        JOB_ID
                )
        ).thenReturn(
                Optional.of(
                        job
                )
        );

        when(
                hybridMatchingService.getCurrent(
                        CANDIDATE_ID,
                        USER_ID
                )
        ).thenReturn(
                beforeRun
        );

        CvTailoringAnalysisService analysisService =
                new CvTailoringAnalysisService(
                        hybridMatchingService,
                        profileRepository,
                        evidenceService,
                        suggestionValidator,
                        analysisStore,
                        jobRepository,
                        suggestionService
                );

        CvTailoringDraftService draftService =
                new CvTailoringDraftService(
                        analysisStore,
                        profileRepository,
                        evidenceService,
                        suggestionValidator,
                        suggestionApplier
                );

        CandidateEmbeddingProperties candidateProperties =
                new CandidateEmbeddingProperties();

        candidateProperties.setTextVersion(
                TEXT_VERSION
        );

        CandidateEmbeddingTextBuilder textBuilder =
                new CandidateEmbeddingTextBuilder(
                        candidateProperties
                );

        EmbeddingProperties embeddingProperties =
                new EmbeddingProperties();

        embeddingProperties.setExpectedDimension(
                3
        );

        embeddingProperties.setExpectedVersion(
                EMBEDDING_VERSION
        );

        EmbeddingTextHashCalculator hashCalculator =
                new EmbeddingTextHashCalculator();

        EmbeddingClient embeddingClient =
                mock(
                        EmbeddingClient.class
                );

        when(
                embeddingClient.embed(
                        any(String.class)
                )
        ).thenAnswer(
                invocation -> {
                    String text =
                            invocation.getArgument(
                                    0,
                                    String.class
                            );

                    return new EmbeddingResponse(
                            List.of(
                                    1.0d,
                                    0.0d,
                                    0.0d
                            ),
                            3,
                            "test-model",
                            "rev",
                            EMBEDDING_VERSION,
                            hashCalculator.calculate(
                                    text
                            ),
                            true
                    );
                }
        );

        CandidateEmbeddingGenerator embeddingGenerator =
                new CandidateEmbeddingGenerator(
                        textBuilder,
                        embeddingClient,
                        hashCalculator,
                        embeddingProperties,
                        candidateProperties
                );

        MatchingProperties matchingProperties =
                new MatchingProperties();

        matchingProperties
                .getCompatibility()
                .setCandidateTextVersion(
                        TEXT_VERSION
                );

        matchingProperties
                .getCompatibility()
                .setNormalizationVersion(
                        "rule-v4"
                );

        matchingProperties
                .getCompatibility()
                .setJobTextVersion(
                        "job-text-v2"
                );

        JobVectorSearchPort searchPort =
                mock(
                        JobVectorSearchPort.class
                );

        HybridRankingService rankingService =
                mock(
                        HybridRankingService.class
                );

        when(
                searchPort.search(
                        anyList(),
                        any(
                                JobVectorSearchCriteria.class
                        )
                )
        ).thenReturn(
                List.of(
                        new JobVectorHit(
                                JOB_ID,
                                "point-after",
                                0.90d
                        )
                )
        );

        when(
                jobRepository.findAllById(
                        any()
                )
        ).thenReturn(
                List.of(
                        job
                )
        );

        when(
                rankingService.rank(
                        any(CandidateProfile.class),
                        anyList(),
                        anyInt()
                )
        ).thenReturn(
                List.of()
        );

        MatchingEvaluationService matchingEvaluationService =
                new MatchingEvaluationService(
                        searchPort,
                        jobRepository,
                        rankingService,
                        matchingProperties
                );

        CvTailoringPreviewService previewService =
                new CvTailoringPreviewService(
                        draftService,
                        hybridMatchingService,
                        embeddingGenerator,
                        matchingEvaluationService,
                        matchingProperties
                );

        return new Harness(
                profile,
                job,
                analysisService,
                previewService,
                embeddingClient,
                searchPort,
                rankingService
        );
    }

    private CvRewriteSafetyTaxonomyProperties testSafetyTaxonomy() {
        CvRewriteSafetyTaxonomyProperties taxonomy =
                new CvRewriteSafetyTaxonomyProperties();

        taxonomy.setHighRiskClaimGroups(
                Map.ofEntries(
                        Map.entry(
                                "leadership",
                                List.of(
                                        "led",
                                        "managed",
                                        "supervised",
                                        "directed",
                                        "oversaw",
                                        "mentored",
                                        "leadership",
                                        "owned"
                                )
                        ),
                        Map.entry(
                                "impact",
                                List.of(
                                        "optimized",
                                        "increased",
                                        "reduced",
                                        "improved",
                                        "scaled",
                                        "scalable",
                                        "highly scalable"
                                )
                        ),
                        Map.entry(
                                "seniority-and-proficiency",
                                List.of(
                                        "expert",
                                        "expertise",
                                        "advanced proficiency",
                                        "proficient",
                                        "senior"
                                )
                        ),
                        Map.entry(
                                "certification-and-license",
                                List.of(
                                        "certified",
                                        "certification",
                                        "licensed",
                                        "license",
                                        "professional license",
                                        "medical license",
                                        "driving license",
                                        "safety certification"
                                )
                        ),
                        Map.entry(
                                "education",
                                List.of(
                                        "bachelor's degree",
                                        "bachelor degree",
                                        "master's degree",
                                        "master degree",
                                        "doctorate",
                                        "phd"
                                )
                        ),
                        Map.entry(
                                "language-proficiency",
                                List.of(
                                        "fluent",
                                        "native proficiency",
                                        "professional proficiency"
                                )
                        ),
                        Map.entry(
                                "financial-responsibility",
                                List.of(
                                        "budget responsibility",
                                        "budget ownership",
                                        "budget"
                                )
                        ),
                        Map.entry(
                                "team-responsibility",
                                List.of(
                                        "team size",
                                        "team of"
                                )
                        ),
                        Map.entry(
                                "experience-duration",
                                List.of(
                                        "years of experience",
                                        "years experience"
                                )
                        ),
                        Map.entry(
                                "regulated-operation",
                                List.of(
                                        "qualified to operate",
                                        "authorized to operate",
                                        "regulated procedure"
                                )
                        ),
                        Map.entry(
                                "architecture-and-systems",
                                List.of(
                                        "architected",
                                        "architecture",
                                        "microservices",
                                        "distributed systems"
                                )
                        )
                )
        );

        return taxonomy;
    }

    private CandidateProfile profile(
            Scenario scenario,
            boolean addProjectEvidence
    ) {
        List<CandidateProfile.ProjectExperience> projects =
                addProjectEvidence
                        ? List.of(
                        new CandidateProfile.ProjectExperience(
                                "Cloud Migration",
                                "Contributor",
                                "Infrastructure",
                                null,
                                null,
                                false,
                                "Supported a cloud migration project.",
                                List.of(),
                                List.of(),
                                List.of(
                                        "AWS"
                                ),
                                List.of(),
                                List.of(),
                                null,
                                null,
                                null
                        )
                )
                        : List.of();

        return CandidateProfile
                .builder()
                .id(
                        CANDIDATE_ID
                )
                .ownerUserId(
                        USER_ID
                )
                .workExperiences(
                        List.of(
                                new CandidateProfile.WorkExperience(
                                        "Example Co",
                                        null,
                                        scenario.title(),
                                        null,
                                        CandidateProfile
                                                .EmploymentType
                                                .FULL_TIME,
                                        null,
                                        CandidateProfile
                                                .WorkMode
                                                .ONSITE,
                                        null,
                                        null,
                                        false,
                                        null,
                                        null,
                                        List.of(
                                                scenario.original()
                                        ),
                                        List.of(),
                                        scenario.evidenceSkills(),
                                        List.of(),
                                        List.of()
                                )
                        )
                )
                .projects(
                        projects
                )
                .updatedAt(
                        Instant.parse(
                                "2026-09-10T07:00:00Z"
                        )
                )
                .parserVersion(
                        "parser-v1"
                )
                .sourceSha256(
                        "profile-sha"
                )
                .build();
    }

    private NormalizedJob job(
            Scenario scenario
    ) {
        return NormalizedJob
                .builder()
                .id(
                        JOB_ID
                )
                .title(
                        scenario.title()
                )
                .companyName(
                        "Example Co"
                )
                .skills(
                        scenario.jobSkills()
                )
                .requirementsText(
                        String.join(
                                ", ",
                                scenario.jobSkills()
                        )
                )
                .descriptionText(
                        "Role requirements for "
                                + scenario.title()
                )
                .normalizationVersion(
                        "rule-v4"
                )
                .rawContentHash(
                        "job-sha"
                )
                .normalizedAt(
                        Instant.parse(
                                "2026-09-10T06:00:00Z"
                        )
                )
                .build();
    }

    private MatchResult beforeMatch(
            Scenario scenario
    ) {
        return MatchResult
                .builder()
                .candidateProfileId(
                        CANDIDATE_ID
                )
                .candidateEmbeddingId(
                        EMBEDDING_ID
                )
                .normalizedJobId(
                        JOB_ID
                )
                .jobTitle(
                        scenario.title()
                )
                .companyName(
                        "Example Co"
                )
                .rank(
                        1
                )
                .finalScore(
                        0.67d
                )
                .semanticScore(
                        0.70d
                )
                .skillScore(
                        0.55d
                )
                .seniorityScore(
                        0.80d
                )
                .locationScore(
                        1.00d
                )
                .freshnessScore(
                        0.90d
                )
                .skillKnown(
                        true
                )
                .seniorityKnown(
                        true
                )
                .locationKnown(
                        true
                )
                .freshnessKnown(
                        true
                )
                .candidateTextVersion(
                        TEXT_VERSION
                )
                .embeddingVersion(
                        EMBEDDING_VERSION
                )
                .rankingVersion(
                        RANKING_VERSION
                )
                .matchedSkills(
                        scenario.matchedSkills()
                )
                .missingSkills(
                        scenario.missingSkills()
                )
                .build();
    }

    private List<SuggestionItem> rewrites(
            CvTailoringAnalyzeResponse analysis
    ) {
        return analysis
                .suggestions()
                .stream()
                .filter(
                        suggestion ->
                                suggestion.type()
                                        == SuggestionType.REWRITE
                )
                .toList();
    }

    private String responsibility(
            CandidateProfile profile
    ) {
        return profile
                .getWorkExperiences()
                .getFirst()
                .responsibilities()
                .getFirst();
    }

    private record Scenario(
            String title,
            String original,
            List<String> evidenceSkills,
            List<String> jobSkills,
            List<String> matchedSkills,
            List<String> missingSkills,
            String suggested,
            List<String> targetSkills
    ) {
    }

    private record Harness(
            CandidateProfile profile,
            NormalizedJob job,
            CvTailoringAnalysisService analysisService,
            CvTailoringPreviewService previewService,
            EmbeddingClient embeddingClient,
            JobVectorSearchPort searchPort,
            HybridRankingService rankingService
    ) {
    }

    private static final class TestProvider
            implements CvRewriteProvider {

        private final Function<
                RewriteRequest,
                RewriteResponse
                > behavior;

        private TestProvider(
                Function<
                        RewriteRequest,
                        RewriteResponse
                        > behavior
        ) {
            this.behavior =
                    behavior;
        }

        @Override
        public String name() {
            return "test-primary";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public RewriteResponse generate(
                RewriteRequest request
        ) {
            return behavior.apply(
                    request
            );
        }
    }
}