package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EditableNode;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EvidenceValue;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.JobContext;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteRequest;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteResponse;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteSuggestion;
import com.autojob.modules.cvtailoring.ai.CvRewriteProviderRouter;
import com.autojob.modules.cvtailoring.config.CvTailoringAiProperties;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.domain.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvSuggestionServiceTest {

    private CvTailoringAiProperties properties;
    private CvRewriteCandidateSelector candidateSelector;
    private CvRewriteProviderRouter providerRouter;
    private CvRewriteSafetyGuard safetyGuard;
    private CvSuggestionValidator suggestionValidator;
    private CvEvidenceService evidenceService;
    private CvSuggestionService service;

    @BeforeEach
    void setUp() {
        properties =
                new CvTailoringAiProperties();

        properties.setEnabled(
                true
        );

        candidateSelector =
                mock(
                        CvRewriteCandidateSelector.class
                );

        providerRouter =
                mock(
                        CvRewriteProviderRouter.class
                );

        safetyGuard =
                mock(
                        CvRewriteSafetyGuard.class
                );

        suggestionValidator =
                mock(
                        CvSuggestionValidator.class
                );

        evidenceService =
                mock(
                        CvEvidenceService.class
                );

        when(
                evidenceService
                        .canonicalSkillKey(
                                anyString()
                        )
        ).thenAnswer(
                invocation -> {

                    String value =
                            invocation.getArgument(
                                    0,
                                    String.class
                            );

                    return value
                            .trim()
                            .toLowerCase(
                                    Locale.ROOT
                            );
                }
        );

        service =
                new CvSuggestionService(
                        properties,
                        candidateSelector,
                        providerRouter,
                        safetyGuard,
                        suggestionValidator,
                        evidenceService,
                        new CvSourceIdResolver(),
                        Clock.fixed(
                                Instant.parse(
                                        "2026-09-09T00:00:00Z"
                                ),
                                ZoneOffset.UTC
                        )
                );
    }

    @Test
    void usesFullCandidateProfileOriginalAndCachesValidatedRewrite() {

        String original =
                "Developed backend services. "
                        + "This original responsibility intentionally "
                        + "contains considerably more text than the "
                        + "truncated copy sent to the language model.";

        CandidateProfile profile =
                CandidateProfile
                        .builder()
                        .id(
                                "candidate-1"
                        )
                        .ownerUserId(
                                "user-1"
                        )
                        .updatedAt(
                                Instant.parse(
                                        "2026-09-09T00:00:00Z"
                                )
                        )
                        .sourceSha256(
                                "profile-sha"
                        )
                        .workExperiences(
                                List.of(
                                        new CandidateProfile.WorkExperience(
                                                "Example Co",
                                                null,
                                                "Backend Developer",
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
                                                        original
                                                ),
                                                List.of(),
                                                List.of(
                                                        "Java"
                                                ),
                                                List.of(),
                                                List.of()
                                        )
                                )
                        )
                        .build();

        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .id(
                                "job-1"
                        )
                        .rawContentHash(
                                "job-sha"
                        )
                        .normalizedAt(
                                Instant.parse(
                                        "2026-09-08T00:00:00Z"
                                )
                        )
                        .title(
                                "Backend Developer"
                        )
                        .skills(
                                List.of(
                                        "Java"
                                )
                        )
                        .requirementsText(
                                "Java backend development"
                        )
                        .build();

        MatchResult targetMatch =
                MatchResult
                        .builder()
                        .candidateEmbeddingId(
                                "embedding-1"
                        )
                        .rankingVersion(
                                "hybrid-v1"
                        )
                        .matchedSkills(
                                List.of(
                                        "Java"
                                )
                        )
                        .missingSkills(
                                List.of()
                        )
                        .build();

        CvEvidenceService.EvidenceMap evidenceMap =
                new CvEvidenceService.EvidenceMap(
                        List.of(
                                new EvidenceItem(
                                        "work:0:responsibility:0",
                                        Section.WORK_EXPERIENCE,
                                        "work:0",
                                        EvidenceKind.TEXT,
                                        original,
                                        null
                                ),

                                new EvidenceItem(
                                        "work:0:skill:0",
                                        Section.WORK_EXPERIENCE,
                                        "work:0",
                                        EvidenceKind.SKILL,
                                        "Java",
                                        "java"
                                )
                        )
                );

        /*
         * The model sees this shortened text.
         *
         * Backend must NOT use this truncated copy as
         * suggestion.original.
         */
        RewriteRequest request =
                new RewriteRequest(
                        new JobContext(
                                "Backend Developer",
                                List.of(
                                        "Java"
                                ),
                                "Java backend development",
                                ""
                        ),
                        List.of(
                                new EditableNode(
                                        "work:0:responsibility:0",
                                        "WORK_EXPERIENCE",
                                        "Work role: Backend Developer",
                                        "Developed backend services.",
                                        List.of(
                                                "work:0:responsibility:0",
                                                "work:0:skill:0"
                                        )
                                )
                        ),
                        List.of(
                                new EvidenceValue(
                                        "work:0:responsibility:0",
                                        "TEXT",
                                        "Developed backend services.",
                                        ""
                                ),

                                new EvidenceValue(
                                        "work:0:skill:0",
                                        "SKILL",
                                        "Java",
                                        "java"
                                )
                        )
                );

        RewriteResponse providerResponse =
                new RewriteResponse(
                        List.of(
                                new RewriteSuggestion(
                                        "work:0:responsibility:0",
                                        "Developed Java backend services.",
                                        List.of(
                                                "Java"
                                        ),
                                        List.of(
                                                "work:0:skill:0"
                                        )
                                )
                        )
                );

        when(
                candidateSelector.select(
                        profile,
                        job,
                        targetMatch,
                        evidenceMap
                )
        ).thenReturn(
                request
        );

        when(
                providerRouter.generate(
                        eq(
                                request
                        ),
                        org.mockito.ArgumentMatchers
                                .<Predicate<RewriteResponse>>any()
                )
        ).thenReturn(
                Optional.of(
                        providerResponse
                )
        );

        when(
                safetyGuard.isSafe(
                        any(
                                SuggestionItem.class
                        ),
                        eq(
                                evidenceMap
                        ),
                        eq(
                                job
                        )
                )
        ).thenReturn(
                true
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

        List<SuggestionItem> first =
                service.generateRewrites(
                        profile,
                        job,
                        targetMatch,
                        evidenceMap
                );

        List<SuggestionItem> second =
                service.generateRewrites(
                        profile,
                        job,
                        targetMatch,
                        evidenceMap
                );

        assertThat(first)
                .hasSize(
                        1
                );

        SuggestionItem suggestion =
                first.getFirst();

        assertThat(
                suggestion.original()
        ).isEqualTo(
                original
        );

        assertThat(
                suggestion.suggested()
        ).isEqualTo(
                "Developed Java backend services."
        );

        assertThat(
                suggestion.targetSkills()
        ).containsExactly(
                "Java"
        );

        /*
         * Backend automatically retains the original source
         * provenance in addition to model-selected skill evidence.
         */
        assertThat(
                suggestion.evidenceIds()
        ).containsExactly(
                "work:0:skill:0",
                "work:0:responsibility:0"
        );

        assertThat(second)
                .isEqualTo(
                        first
                );

        /*
         * Same profile + JD + matching run:
         * cache prevents another free-tier API call.
         */
        verify(
                providerRouter,
                times(
                        1
                )
        ).generate(
                eq(
                        request
                ),
                org.mockito.ArgumentMatchers
                        .<Predicate<RewriteResponse>>any()
        );
    }
}