package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CvTailoringDraftServiceHardeningTest {

    private static final String USER_ID = "user-1";
    private static final String CANDIDATE_ID = "candidate-1";
    private static final String JOB_ID = "job-1";
    private static final String EMBEDDING_ID = "embedding-1";
    private static final String RANKING_VERSION = "hybrid-v1";

    private CandidateProfileRepository candidateProfileRepository;
    private CvEvidenceService evidenceService;
    private CvSuggestionApplier suggestionApplier;
    private CvTailoringAnalysisStore analysisStore;
    private CvTailoringDraftService draftService;
    private CandidateProfile profile;
    private CvEvidenceService.EvidenceMap freshEvidence;

    @BeforeEach
    void setUp() {
        candidateProfileRepository =
                mock(CandidateProfileRepository.class);

        evidenceService =
                mock(CvEvidenceService.class);

        suggestionApplier =
                mock(CvSuggestionApplier.class);

        analysisStore =
                new CvTailoringAnalysisStore(
                        Clock.fixed(
                                Instant.parse("2026-09-09T00:00:00Z"),
                                ZoneOffset.UTC
                        )
                );

        CvSourceIdResolver sourceIdResolver =
                new CvSourceIdResolver();

        CvSuggestionValidator validator =
                new CvSuggestionValidator(
                        evidenceService,
                        sourceIdResolver
                );

        draftService =
                new CvTailoringDraftService(
                        analysisStore,
                        candidateProfileRepository,
                        evidenceService,
                        validator,
                        suggestionApplier
                );

        profile = profile();
        freshEvidence = evidenceMap();

        when(
                candidateProfileRepository.findById(CANDIDATE_ID)
        ).thenReturn(
                java.util.Optional.of(profile)
        );

        when(
                evidenceService.build(profile)
        ).thenReturn(
                freshEvidence
        );

        when(
                evidenceService.canonicalSkillKey(anyString())
        ).thenAnswer(invocation -> {
            String value = invocation.getArgument(0);
            return value == null
                    ? ""
                    : value.trim().toLowerCase(Locale.ROOT);
        });
    }

    @Test
    void rejectsDuplicateAcceptedSuggestionIds() {
        SuggestionItem suggestion = validRewrite();
        String analysisId = saveContext(List.of(suggestion));

        assertBadRequest(
                () -> draftService.createTemporaryDraft(
                        analysisId,
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID,
                        List.of(
                                suggestion.id(),
                                suggestion.id()
                        )
                ),
                "must not contain duplicates"
        );

        verifyNoInteractions(suggestionApplier);
    }

    @Test
    void rejectsUnknownSuggestionId() {
        String analysisId = saveContext(
                List.of(validRewrite())
        );

        assertBadRequest(
                () -> draftService.createTemporaryDraft(
                        analysisId,
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID,
                        List.of("missing-suggestion")
                ),
                "Unknown or unvalidated suggestion id"
        );
    }

    @Test
    void rejectsGapWarningWhenItReachesApplyPath() {
        SuggestionItem gapWarning =
                new SuggestionItem(
                        "gap-aws",
                        SuggestionType.GAP_WARNING,
                        Section.SKILLS,
                        "skills",
                        null,
                        null,
                        "AWS has no supporting evidence.",
                        List.of("AWS"),
                        List.of()
                );

        String analysisId = saveContext(
                List.of(gapWarning)
        );

        assertThatThrownBy(
                () -> draftService.createTemporaryDraft(
                        analysisId,
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID,
                        List.of(gapWarning.id())
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "GAP_WARNING is informational"
                );
    }

    @Test
    void rejectsWrongSourceIdDuringServerSideRevalidation() {
        SuggestionItem suggestion =
                rewrite(
                        "rewrite-wrong-source",
                        "work:9:responsibility:0",
                        "Developed backend services.",
                        List.of("Java"),
                        List.of("work:0:skill:0")
                );

        String analysisId = saveContext(
                List.of(suggestion)
        );

        assertThatThrownBy(
                () -> createDraft(
                        analysisId,
                        suggestion.id()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Invalid work sourceId"
                );
    }

    @Test
    void rejectsWrongOriginalDuringServerSideRevalidation() {
        SuggestionItem suggestion =
                rewrite(
                        "rewrite-wrong-original",
                        "work:0:responsibility:0",
                        "Different original text.",
                        List.of("Java"),
                        List.of("work:0:skill:0")
                );

        String analysisId = saveContext(
                List.of(suggestion)
        );

        assertThatThrownBy(
                () -> createDraft(
                        analysisId,
                        suggestion.id()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "original text does not match CandidateProfile"
                );
    }

    @Test
    void rejectsUnknownEvidenceIdDuringServerSideRevalidation() {
        SuggestionItem suggestion =
                rewrite(
                        "rewrite-wrong-evidence",
                        "work:0:responsibility:0",
                        "Developed backend services.",
                        List.of("Java"),
                        List.of("work:0:skill:99")
                );

        String analysisId = saveContext(
                List.of(suggestion)
        );

        assertThatThrownBy(
                () -> createDraft(
                        analysisId,
                        suggestion.id()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Unknown evidence id"
                );
    }

    @Test
    void rejectsEvidenceFromAnotherWorkScopeDuringServerSideRevalidation() {
        SuggestionItem suggestion =
                rewrite(
                        "rewrite-wrong-scope",
                        "work:0:responsibility:0",
                        "Developed backend services.",
                        List.of("Spring Boot"),
                        List.of("work:1:skill:0")
                );

        String analysisId = saveContext(
                List.of(suggestion)
        );

        assertThatThrownBy(
                () -> createDraft(
                        analysisId,
                        suggestion.id()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "outside source scope"
                );
    }

    @Test
    void rejectsTargetSkillWithoutSupportingEvidence() {
        SuggestionItem suggestion =
                rewrite(
                        "rewrite-no-evidence",
                        "work:0:responsibility:0",
                        "Developed backend services.",
                        List.of("AWS"),
                        List.of("work:0:responsibility:0")
                );

        String analysisId = saveContext(
                List.of(suggestion)
        );

        assertThatThrownBy(
                () -> createDraft(
                        analysisId,
                        suggestion.id()
                )
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "target skill has no supporting evidence: AWS"
                );
    }

    @Test
    void successfulDraftKeepsOriginalCandidateProfileUnchanged() {
        SuggestionItem suggestion = validRewrite();
        String analysisId = saveContext(List.of(suggestion));

        CvSuggestionApplier realApplier =
                new CvSuggestionApplier(
                        evidenceService,
                        new CvSourceIdResolver()
                );

        CvTailoringDraftService realDraftService =
                new CvTailoringDraftService(
                        analysisStore,
                        candidateProfileRepository,
                        evidenceService,
                        new CvSuggestionValidator(
                                evidenceService,
                                new CvSourceIdResolver()
                        ),
                        realApplier
                );

        CvTailoringDraftService.TemporaryDraft draft =
                realDraftService.createTemporaryDraft(
                        analysisId,
                        CANDIDATE_ID,
                        JOB_ID,
                        USER_ID,
                        List.of(suggestion.id())
                );

        assertThat(
                profile.getWorkExperiences()
                        .getFirst()
                        .responsibilities()
                        .getFirst()
        ).isEqualTo(
                "Developed backend services."
        );

        assertThat(draft.temporaryProfile())
                .isNotSameAs(profile);

        assertThat(
                draft.temporaryProfile()
                        .getWorkExperiences()
                        .getFirst()
                        .responsibilities()
                        .getFirst()
        ).isEqualTo(
                "Developed Java backend services."
        );
    }

    private CvTailoringDraftService.TemporaryDraft createDraft(
            String analysisId,
            String suggestionId
    ) {
        return draftService.createTemporaryDraft(
                analysisId,
                CANDIDATE_ID,
                JOB_ID,
                USER_ID,
                List.of(suggestionId)
        );
    }

    private String saveContext(List<SuggestionItem> suggestions) {
        return analysisStore.save(
                USER_ID,
                profile,
                JOB_ID,
                EMBEDDING_ID,
                RANKING_VERSION,
                freshEvidence.items(),
                suggestions
        ).analysisId();
    }

    private SuggestionItem validRewrite() {
        return rewrite(
                "rewrite-java",
                "work:0:responsibility:0",
                "Developed backend services.",
                List.of("Java"),
                List.of("work:0:skill:0")
        );
    }

    private SuggestionItem rewrite(
            String id,
            String sourceId,
            String original,
            List<String> targetSkills,
            List<String> evidenceIds
    ) {
        return new SuggestionItem(
                id,
                SuggestionType.REWRITE,
                Section.WORK_EXPERIENCE,
                sourceId,
                original,
                "Developed Java backend services.",
                "Clarifies existing evidence.",
                targetSkills,
                evidenceIds
        );
    }

    private CvEvidenceService.EvidenceMap evidenceMap() {
        return new CvEvidenceService.EvidenceMap(
                List.of(
                        new EvidenceItem(
                                "work:0:responsibility:0",
                                Section.WORK_EXPERIENCE,
                                "work:0",
                                EvidenceKind.TEXT,
                                "Developed backend services.",
                                null
                        ),
                        new EvidenceItem(
                                "work:0:skill:0",
                                Section.WORK_EXPERIENCE,
                                "work:0",
                                EvidenceKind.SKILL,
                                "Java",
                                "java"
                        ),
                        new EvidenceItem(
                                "work:1:skill:0",
                                Section.WORK_EXPERIENCE,
                                "work:1",
                                EvidenceKind.SKILL,
                                "Spring Boot",
                                "spring boot"
                        )
                )
        );
    }

    private CandidateProfile profile() {
        return CandidateProfile
                .builder()
                .id(CANDIDATE_ID)
                .ownerUserId(USER_ID)
                .updatedAt(
                        Instant.parse("2026-09-09T00:00:00Z")
                )
                .parserVersion("parser-v1")
                .sourceSha256("sha-1")
                .workExperiences(
                        List.of(
                                work(
                                        "Developed backend services.",
                                        List.of("Java")
                                ),
                                work(
                                        "Maintained internal systems.",
                                        List.of("Spring Boot")
                                )
                        )
                )
                .build();
    }

    private CandidateProfile.WorkExperience work(
            String responsibility,
            List<String> skills
    ) {
        return new CandidateProfile.WorkExperience(
                "Example Co",
                null,
                "Backend Developer",
                null,
                CandidateProfile.EmploymentType.FULL_TIME,
                null,
                CandidateProfile.WorkMode.ONSITE,
                null,
                null,
                false,
                null,
                null,
                List.of(responsibility),
                List.of(),
                skills,
                List.of(),
                List.of()
        );
    }

    private void assertBadRequest(
            Runnable action,
            String messageFragment
    ) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(throwable -> {
                    ResponseStatusException exception =
                            (ResponseStatusException) throwable;

                    assertThat(exception.getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST);
                })
                .hasMessageContaining(messageFragment);
    }
}