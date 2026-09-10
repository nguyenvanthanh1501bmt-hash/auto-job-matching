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

class CvRewriteSafetyGuardQualificationTest {

    private CvRewriteSafetyGuard guard;

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
                        "certification",
                        List.of(
                                "certified",
                                "certification",
                                "certificate"
                        ),

                        "license",
                        List.of(
                                "licensed",
                                "license",
                                "professional license",
                                "medical license",
                                "driving license"
                        ),

                        "education",
                        List.of(
                                "bachelor's degree",
                                "bachelor degree",
                                "bachelor of",
                                "master's degree",
                                "master degree",
                                "master of",
                                "doctorate",
                                "phd"
                        ),

                        "language-proficiency",
                        List.of(
                                "fluent",
                                "native proficiency",
                                "professional proficiency",
                                "language proficiency"
                        )
                )
        );

        taxonomy.setRequiredEvidenceKindsByGroup(
                Map.of(
                        "certification",
                        List.of(
                                EvidenceKind.CERTIFICATION
                        ),

                        "license",
                        List.of(
                                EvidenceKind.LICENSE
                        ),

                        "education",
                        List.of(
                                EvidenceKind.EDUCATION
                        ),

                        "language-proficiency",
                        List.of(
                                EvidenceKind.LANGUAGE
                        )
                )
        );

        taxonomy.setGenericTokensByGroup(
                Map.of(
                        "certification",
                        List.of(
                                "certified",
                                "certification",
                                "certificate"
                        ),

                        "license",
                        List.of(
                                "licensed",
                                "license"
                        ),

                        "education",
                        List.of(
                                "degree",
                                "bachelor",
                                "bachelors",
                                "master",
                                "masters",
                                "doctorate",
                                "phd",
                                "of"
                        ),

                        "language-proficiency",
                        List.of(
                                "fluent",
                                "proficiency",
                                "native",
                                "professional",
                                "language"
                        )
                )
        );

        taxonomy.setCompanionIdentityRequiredGroups(
                List.of(
                        "language-proficiency"
                )
        );

        guard =
                new CvRewriteSafetyGuard(
                        evidenceService,
                        taxonomy
                );

        job =
                NormalizedJob
                        .builder()
                        .id(
                                "job-1"
                        )
                        .skills(
                                List.of()
                        )
                        .build();
    }

    @Test
    void allowsLicenseClaimWhenCitedLicenseIdentityMatches() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "license:0:name",
                                Section.LICENSE,
                                "license:0",
                                EvidenceKind.LICENSE,
                                "Registered Nurse License",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional working as a licensed registered nurse.",
                                List.of(
                                        "professionalSummary",
                                        "license:0:name"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isTrue();
    }

    @Test
    void rejectsLicenseClaimWhenCitedLicenseHasDifferentIdentity() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "license:0:name",
                                Section.LICENSE,
                                "license:0",
                                EvidenceKind.LICENSE,
                                "Driving License",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional working as a licensed registered nurse.",
                                List.of(
                                        "professionalSummary",
                                        "license:0:name"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void rejectsLicenseClaimWhenEvidenceKindIsWrong() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "certification:0:name",
                                Section.CERTIFICATION,
                                "certification:0",
                                EvidenceKind.CERTIFICATION,
                                "Registered Nurse License",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional working as a licensed registered nurse.",
                                List.of(
                                        "professionalSummary",
                                        "certification:0:name"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void allowsCertificationWhenQualificationIdentityMatches() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "certification:0:name",
                                Section.CERTIFICATION,
                                "certification:0",
                                EvidenceKind.CERTIFICATION,
                                "Basic Life Support",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional with Basic Life Support certification.",
                                List.of(
                                        "professionalSummary",
                                        "certification:0:name"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isTrue();
    }

    @Test
    void rejectsCertificationWhenQualificationIdentityDoesNotMatch() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "certification:0:name",
                                Section.CERTIFICATION,
                                "certification:0",
                                EvidenceKind.CERTIFICATION,
                                "Forklift Safety",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional with Basic Life Support certification.",
                                List.of(
                                        "professionalSummary",
                                        "certification:0:name"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void allowsEducationClaimWhenDegreeIdentityMatches() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "education:0:degree",
                                Section.EDUCATION,
                                "education:0",
                                EvidenceKind.EDUCATION,
                                "Bachelor of Nursing",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional with a bachelor's degree in nursing.",
                                List.of(
                                        "professionalSummary",
                                        "education:0:degree"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isTrue();
    }

    @Test
    void rejectsDifferentEducationIdentity() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "education:0:degree",
                                Section.EDUCATION,
                                "education:0",
                                EvidenceKind.EDUCATION,
                                "Bachelor of Mechanical Engineering",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional with a bachelor's degree in nursing.",
                                List.of(
                                        "professionalSummary",
                                        "education:0:degree"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void languageProficiencyRequiresLanguageIdentityFromSameScope() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "language:0:name",
                                Section.LANGUAGE,
                                "language:0",
                                EvidenceKind.LANGUAGE,
                                "English",
                                null
                        ),
                        new EvidenceItem(
                                "language:0:proficiency",
                                Section.LANGUAGE,
                                "language:0",
                                EvidenceKind.LANGUAGE,
                                "Fluent",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional fluent in English.",
                                List.of(
                                        "professionalSummary",
                                        "language:0:name",
                                        "language:0:proficiency"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isTrue();
    }

    @Test
    void languageNameAloneDoesNotProveFluency() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "language:0:name",
                                Section.LANGUAGE,
                                "language:0",
                                EvidenceKind.LANGUAGE,
                                "English",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional fluent in English.",
                                List.of(
                                        "professionalSummary",
                                        "language:0:name"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void languageProficiencyRejectsMissingLanguageIdentityEvidence() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "language:0:proficiency",
                                Section.LANGUAGE,
                                "language:0",
                                EvidenceKind.LANGUAGE,
                                "Fluent",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional fluent in English.",
                                List.of(
                                        "professionalSummary",
                                        "language:0:proficiency"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void languageProficiencyRejectsDifferentLanguageIdentity() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "language:0:name",
                                Section.LANGUAGE,
                                "language:0",
                                EvidenceKind.LANGUAGE,
                                "English",
                                null
                        ),
                        new EvidenceItem(
                                "language:0:proficiency",
                                Section.LANGUAGE,
                                "language:0",
                                EvidenceKind.LANGUAGE,
                                "Fluent",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional fluent in German.",
                                List.of(
                                        "professionalSummary",
                                        "language:0:name",
                                        "language:0:proficiency"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void languageEvidenceCannotBeMixedAcrossScopes() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "language:0:name",
                                Section.LANGUAGE,
                                "language:0",
                                EvidenceKind.LANGUAGE,
                                "English",
                                null
                        ),
                        new EvidenceItem(
                                "language:1:proficiency",
                                Section.LANGUAGE,
                                "language:1",
                                EvidenceKind.LANGUAGE,
                                "Fluent",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional fluent in English.",
                                List.of(
                                        "professionalSummary",
                                        "language:0:name",
                                        "language:1:proficiency"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void unsupportedNumberIsStillRejectedForTypedQualificationClaim() {
        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap(
                        new EvidenceItem(
                                "language:0:name",
                                Section.LANGUAGE,
                                "language:0",
                                EvidenceKind.LANGUAGE,
                                "English",
                                null
                        ),
                        new EvidenceItem(
                                "language:0:proficiency",
                                Section.LANGUAGE,
                                "language:0",
                                EvidenceKind.LANGUAGE,
                                "Fluent",
                                null
                        )
                );

        assertThat(
                guard.isSafe(
                        summarySuggestion(
                                "Healthcare professional fluent in English with IELTS 8.5.",
                                List.of(
                                        "professionalSummary",
                                        "language:0:name",
                                        "language:0:proficiency"
                                )
                        ),
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    private CvEvidenceService.EvidenceMap evidenceMap(
            EvidenceItem... additionalEvidence
    ) {
        List<EvidenceItem> items =
                new java.util.ArrayList<>();

        items.add(
                new EvidenceItem(
                        "professionalSummary",
                        Section.PROFESSIONAL_SUMMARY,
                        "professionalSummary",
                        EvidenceKind.TEXT,
                        "Healthcare professional.",
                        null
                )
        );

        items.addAll(
                List.of(
                        additionalEvidence
                )
        );

        return new CvEvidenceService.EvidenceMap(
                items
        );
    }

    private SuggestionItem summarySuggestion(
            String suggested,
            List<String> evidenceIds
    ) {
        return new SuggestionItem(
                "rewrite-summary",
                SuggestionType.REWRITE,
                Section.PROFESSIONAL_SUMMARY,
                "professionalSummary",
                "Healthcare professional.",
                suggested,
                "Surfaces confirmed qualification evidence.",
                List.of(),
                evidenceIds
        );
    }
}