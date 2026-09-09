package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionType;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import com.autojob.modules.matching.contract.MatchingRunResult;
import com.autojob.modules.matching.domain.MatchResult;
import com.autojob.modules.matching.service.HybridMatchingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CvTailoringAnalysisServiceTest {

    @Mock
    private HybridMatchingService
            hybridMatchingService;

    @Mock
    private CandidateProfileRepository
            candidateProfileRepository;

    @Mock
    private CvEvidenceService
            cvEvidenceService;

    @Mock
    private CvSuggestionValidator
            suggestionValidator;

    @Mock
    private CvTailoringAnalysisStore
            analysisStore;

    @Mock
    private NormalizedJobRepository
            normalizedJobRepository;

    @Mock
    private CvSuggestionService
            cvSuggestionService;

    private CvTailoringAnalysisService
            service;

    @BeforeEach
    void setUp() {
        service =
                new CvTailoringAnalysisService(
                        hybridMatchingService,
                        candidateProfileRepository,
                        cvEvidenceService,
                        suggestionValidator,
                        analysisStore,
                        normalizedJobRepository,
                        cvSuggestionService
                );
    }

    @Test
    void analyzeKeepsRuleBasedSuggestionsWhenAiReturnsNoRewrite() {

        String candidateId =
                "candidate-1";

        String jobId =
                "job-1";

        String ownerId =
                "user-1";

        CandidateProfile profile =
                CandidateProfile
                        .builder()
                        .id(
                                candidateId
                        )
                        .ownerUserId(
                                ownerId
                        )
                        .updatedAt(
                                Instant.parse(
                                        "2026-09-08T00:00:00Z"
                                )
                        )
                        .parserVersion(
                                "parser-v1"
                        )
                        .sourceSha256(
                                "sha-1"
                        )
                        .build();

        MatchResult match =
                MatchResult
                        .builder()
                        .candidateProfileId(
                                candidateId
                        )
                        .candidateEmbeddingId(
                                "embedding-1"
                        )
                        .normalizedJobId(
                                jobId
                        )
                        .rank(
                                1
                        )
                        .jobTitle(
                                "Backend Developer"
                        )
                        .companyName(
                                "Example Co"
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
                        .matchedSkills(
                                List.of(
                                        "Java",
                                        "PostgreSQL"
                                )
                        )
                        .missingSkills(
                                List.of(
                                        "Docker"
                                )
                        )
                        .build();

        MatchingRunResult run =
                new MatchingRunResult(
                        candidateId,
                        "embedding-1",
                        "hybrid-v1",
                        0,
                        0,
                        1,
                        true,
                        List.of(
                                match
                        )
                );

        CvEvidenceService.EvidenceMap evidenceMap =
                new CvEvidenceService.EvidenceMap(
                        List.of(
                                new EvidenceItem(
                                        "skill:0",
                                        Section.SKILLS,
                                        "skills",
                                        EvidenceKind.SKILL,
                                        "Java",
                                        "java"
                                ),

                                new EvidenceItem(
                                        "project:0:skill:0",
                                        Section.PROJECT,
                                        "project:0",
                                        EvidenceKind.SKILL,
                                        "PostgreSQL",
                                        "postgresql"
                                )
                        )
                );

        NormalizedJob normalizedJob =
                NormalizedJob
                        .builder()
                        .id(
                                jobId
                        )
                        .title(
                                "Backend Developer"
                        )
                        .skills(
                                List.of(
                                        "Java",
                                        "PostgreSQL",
                                        "Docker"
                                )
                        )
                        .requirementsText(
                                "Java, PostgreSQL and Docker"
                        )
                        .descriptionText(
                                "Develop backend applications."
                        )
                        .build();

        CvTailoringAnalysisStore.AnalysisContext storedContext =
                new CvTailoringAnalysisStore.AnalysisContext(
                        "analysis-1",
                        ownerId,
                        candidateId,
                        jobId,
                        "embedding-1",
                        "hybrid-v1",
                        profile.getUpdatedAt(),
                        profile.getParserVersion(),
                        profile.getSourceSha256(),
                        Instant.parse(
                                "2026-09-08T00:00:00Z"
                        ),
                        Instant.parse(
                                "2026-09-08T00:30:00Z"
                        ),
                        evidenceMap.items(),
                        List.of()
                );

        when(
                hybridMatchingService
                        .getCurrent(
                                candidateId,
                                ownerId
                        )
        ).thenReturn(
                run
        );

        when(
                candidateProfileRepository
                        .findById(
                                candidateId
                        )
        ).thenReturn(
                Optional.of(
                        profile
                )
        );

        when(
                cvEvidenceService.build(
                        profile
                )
        ).thenReturn(
                evidenceMap
        );

        when(
                normalizedJobRepository
                        .findById(
                                jobId
                        )
        ).thenReturn(
                Optional.of(
                        normalizedJob
                )
        );

        when(
                cvEvidenceService
                        .canonicalSkillKey(
                                "Java"
                        )
        ).thenReturn(
                "java"
        );

        when(
                cvEvidenceService
                        .canonicalSkillKey(
                                "PostgreSQL"
                        )
        ).thenReturn(
                "postgresql"
        );

        when(
                cvEvidenceService
                        .canonicalSkillKey(
                                "Docker"
                        )
        ).thenReturn(
                "docker"
        );

        /*
         * Represents:
         * - both providers disabled
         * - both providers unavailable
         * - both providers rate-limited
         * - or no truthful rewrite needed
         *
         * Analyze must still work.
         */
        when(
                cvSuggestionService
                        .generateRewrites(
                                profile,
                                normalizedJob,
                                match,
                                evidenceMap
                        )
        ).thenReturn(
                List.of()
        );

        when(
                suggestionValidator.validateAll(
                        eq(
                                profile
                        ),
                        eq(
                                evidenceMap
                        ),
                        any()
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(
                                2
                        )
        );

        when(
                analysisStore.save(
                        eq(
                                ownerId
                        ),
                        eq(
                                profile
                        ),
                        eq(
                                jobId
                        ),
                        eq(
                                "embedding-1"
                        ),
                        eq(
                                "hybrid-v1"
                        ),
                        eq(
                                evidenceMap.items()
                        ),
                        any()
                )
        ).thenAnswer(
                invocation -> {

                    @SuppressWarnings(
                            "unchecked"
                    )
                    List<SuggestionItem> suggestions =
                            invocation.getArgument(
                                    6
                            );

                    return new CvTailoringAnalysisStore.AnalysisContext(
                            storedContext.analysisId(),
                            storedContext.ownerUserId(),
                            storedContext.candidateProfileId(),
                            storedContext.normalizedJobId(),
                            storedContext.candidateEmbeddingId(),
                            storedContext.rankingVersion(),
                            storedContext.profileUpdatedAt(),
                            storedContext.parserVersion(),
                            storedContext.sourceSha256(),
                            storedContext.createdAt(),
                            storedContext.expiresAt(),
                            storedContext.evidence(),
                            suggestions
                    );
                }
        );

        CvTailoringAnalyzeResponse response =
                service.analyze(
                        candidateId,
                        jobId,
                        ownerId
                );

        assertThat(
                response.analysisId()
        ).isEqualTo(
                "analysis-1"
        );

        assertThat(
                response
                        .currentMatch()
                        .finalScore()
        ).isEqualTo(
                0.67d
        );

        /*
         * PostgreSQL exists in Project evidence but not
         * top-level Skills -> EMPHASIZE still works.
         */
        assertThat(
                response.suggestions()
        ).hasSize(
                1
        );

        assertThat(
                response
                        .suggestions()
                        .getFirst()
                        .type()
        ).isEqualTo(
                SuggestionType.EMPHASIZE
        );

        assertThat(
                response
                        .suggestions()
                        .getFirst()
                        .suggested()
        ).isEqualTo(
                "PostgreSQL"
        );

        assertThat(
                response
                        .suggestions()
                        .getFirst()
                        .evidenceIds()
        ).containsExactly(
                "project:0:skill:0"
        );

        /*
         * Docker is missing -> GAP_WARNING still works
         * independently of LLM availability.
         */
        assertThat(
                response.gaps()
        ).hasSize(
                1
        );

        assertThat(
                response
                        .gaps()
                        .getFirst()
                        .skill()
        ).isEqualTo(
                "Docker"
        );

        assertThat(
                evidenceMap
                        .topLevelSkillKeys()
        ).isEqualTo(
                Set.of(
                        "java"
                )
        );

        /*
         * Most important Phase 5 integration invariant:
         * AI received the actual NormalizedJob/JD.
         */
        verify(
                cvSuggestionService
        ).generateRewrites(
                profile,
                normalizedJob,
                match,
                evidenceMap
        );
    }
}