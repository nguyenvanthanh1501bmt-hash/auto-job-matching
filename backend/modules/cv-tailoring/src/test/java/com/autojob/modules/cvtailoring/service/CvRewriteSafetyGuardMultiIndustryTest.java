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

class CvRewriteSafetyGuardMultiIndustryTest {

    private CvRewriteSafetyGuard guard;
    private CvEvidenceService.EvidenceMap evidenceMap;
    private NormalizedJob job;

    @BeforeEach
    void setUp() {
        CvEvidenceService evidenceService =
                mock(
                        CvEvidenceService.class
                );

        when(
                evidenceService.canonicalSkillKey(
                        anyString()
                )
        ).thenAnswer(
                invocation ->
                        invocation
                                .getArgument(
                                        0,
                                        String.class
                                )
                                .trim()
                                .toLowerCase(
                                        Locale.ROOT
                                )
        );

        CvRewriteSafetyTaxonomyProperties taxonomy =
                new CvRewriteSafetyTaxonomyProperties();

        taxonomy.setHighRiskClaimGroups(
                Map.of(
                        "test-claims",
                        List.of(
                                "licensed",
                                "certification",
                                "bachelor's degree",
                                "fluent",
                                "supervised",
                                "budget responsibility"
                        )
                )
        );

        guard =
                new CvRewriteSafetyGuard(
                        evidenceService,
                        taxonomy
                );

        evidenceMap =
                new CvEvidenceService.EvidenceMap(
                        List.of(
                                new EvidenceItem(
                                        "work:0:responsibility:0",
                                        Section.WORK_EXPERIENCE,
                                        "work:0",
                                        EvidenceKind.TEXT,
                                        "Provided patient care and supported clinical procedures.",
                                        null
                                ),
                                new EvidenceItem(
                                        "work:0:skill:0",
                                        Section.WORK_EXPERIENCE,
                                        "work:0",
                                        EvidenceKind.SKILL,
                                        "Patient Care",
                                        "patient care"
                                )
                        )
                );

        job =
                NormalizedJob
                        .builder()
                        .id(
                                "job-healthcare-1"
                        )
                        .skills(
                                List.of(
                                        "Patient Care"
                                )
                        )
                        .build();
    }

    @Test
    void rejectsFabricatedProfessionalLicense() {
        assertThat(
                guard.isSafe(
                        suggestion(
                                "Provided patient care as a licensed registered nurse."
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void rejectsFabricatedCertification() {
        assertThat(
                guard.isSafe(
                        suggestion(
                                "Provided certification-based patient care and supported clinical procedures."
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void rejectsFabricatedDegree() {
        assertThat(
                guard.isSafe(
                        suggestion(
                                "Provided patient care with a bachelor's degree in nursing."
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void rejectsFabricatedLanguageProficiency() {
        assertThat(
                guard.isSafe(
                        suggestion(
                                "Provided fluent English support while delivering patient care."
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void rejectsFabricatedManagementResponsibility() {
        assertThat(
                guard.isSafe(
                        suggestion(
                                "Supervised clinical staff while providing patient care."
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void rejectsFabricatedBudgetResponsibility() {
        assertThat(
                guard.isSafe(
                        suggestion(
                                "Held budget responsibility while providing patient care."
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void stillAllowsOrdinaryEvidenceGroundedHealthcareRewrite() {
        SuggestionItem suggestion =
                new SuggestionItem(
                        "rewrite-ai-0",
                        SuggestionType.REWRITE,
                        Section.WORK_EXPERIENCE,
                        "work:0:responsibility:0",
                        "Provided patient care and supported clinical procedures.",
                        "Supported clinical procedures while providing patient care.",
                        "Makes existing evidence clearer.",
                        List.of(
                                "Patient Care"
                        ),
                        List.of(
                                "work:0:responsibility:0",
                                "work:0:skill:0"
                        )
                );

        assertThat(
                guard.isSafe(
                        suggestion,
                        evidenceMap,
                        job
                )
        ).isTrue();
    }

    private SuggestionItem suggestion(
            String suggested
    ) {
        return new SuggestionItem(
                "rewrite-ai-0",
                SuggestionType.REWRITE,
                Section.WORK_EXPERIENCE,
                "work:0:responsibility:0",
                "Provided patient care and supported clinical procedures.",
                suggested,
                "Makes existing evidence clearer.",
                List.of(
                        "Patient Care"
                ),
                List.of(
                        "work:0:responsibility:0",
                        "work:0:skill:0"
                )
        );
    }
}