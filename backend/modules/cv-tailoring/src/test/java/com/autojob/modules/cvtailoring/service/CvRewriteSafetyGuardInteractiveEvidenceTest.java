package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cvtailoring.config.CvRewriteSafetyTaxonomyProperties;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionType;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CvRewriteSafetyGuardInteractiveEvidenceTest {

    private CvRewriteSafetyGuard guard;
    private NormalizedJob job;

    @BeforeEach
    void setUp() {
        CvEvidenceService evidenceService =
                mock(CvEvidenceService.class);

        when(
                evidenceService.canonicalSkillKey(
                        anyString()
                )
        ).thenAnswer(invocation -> {
            String value = invocation.getArgument(
                    0,
                    String.class
            );

            return value
                    .trim()
                    .toLowerCase(Locale.ROOT);
        });

        CvRewriteSafetyTaxonomyProperties taxonomy =
                new CvRewriteSafetyTaxonomyProperties();

        taxonomy.setHighRiskClaimGroups(
                Map.of(
                        "leadership",
                        List.of("led")
                )
        );

        guard = new CvRewriteSafetyGuard(
                evidenceService,
                taxonomy
        );

        job = NormalizedJob
                .builder()
                .id("job-1")
                .skills(
                        List.of(
                                "Java",
                                "AWS"
                        )
                )
                .build();
    }

    @Test
    void allowsNewJobSkillWhenCandidateExplicitlyConfirmedIt() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        "I used AWS to deploy this backend service."
                );

        SuggestionItem suggestion = suggestion(
                "Developed backend services using Java and AWS."
        );

        CvRewriteSafetyGuard.SafetyAssessment assessment =
                guard.assess(
                        suggestion,
                        evidenceMap,
                        job
                );

        assertThat(assessment.safe()).isTrue();
        assertThat(assessment.reason()).isEqualTo(
                CvRewriteSafetyGuard
                        .SafetyFailureReason
                        .NONE
        );
    }

    @Test
    void stillRejectsNewJobSkillWhenUserDidNotConfirmIt() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        "I deployed this backend service."
                );

        SuggestionItem suggestion = suggestion(
                "Developed backend services using Java and AWS."
        );

        CvRewriteSafetyGuard.SafetyAssessment assessment =
                guard.assess(
                        suggestion,
                        evidenceMap,
                        job
                );

        assertThat(assessment.safe()).isFalse();
        assertThat(assessment.reason()).isEqualTo(
                CvRewriteSafetyGuard
                        .SafetyFailureReason
                        .JOB_ONLY_TERM
        );
    }

    @Test
    void exposesUnsupportedNumberReasonWithoutLoggingCandidateText() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        "I do not have a reliable production request count."
                );

        SuggestionItem suggestion = suggestion(
                "Developed 500 backend services using Java."
        );

        CvRewriteSafetyGuard.SafetyAssessment assessment =
                guard.assess(
                        suggestion,
                        evidenceMap,
                        job
                );

        assertThat(assessment.safe()).isFalse();
        assertThat(assessment.reason()).isEqualTo(
                CvRewriteSafetyGuard
                        .SafetyFailureReason
                        .UNSUPPORTED_NUMBER
        );
    }

    private CvEvidenceService.EvidenceMap evidenceMap(
            String confirmedText
    ) {
        return new CvEvidenceService.EvidenceMap(
                List.of(
                        new EvidenceItem(
                                "work:0:responsibility:0",
                                Section.WORK_EXPERIENCE,
                                "work:0",
                                EvidenceKind.TEXT,
                                "Developed backend services using Java.",
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
                                "user-confirmed-test",
                                Section.WORK_EXPERIENCE,
                                "work:0",
                                EvidenceKind.TEXT,
                                confirmedText,
                                null
                        )
                )
        );
    }

    private SuggestionItem suggestion(
            String suggested
    ) {
        return new SuggestionItem(
                "coaching-rewrite-test",
                SuggestionType.REWRITE,
                Section.WORK_EXPERIENCE,
                "work:0:responsibility:0",
                "Developed backend services using Java.",
                suggested,
                "Use confirmed evidence.",
                List.of(),
                List.of(
                        "work:0:responsibility:0",
                        "user-confirmed-test"
                )
        );
    }
}
