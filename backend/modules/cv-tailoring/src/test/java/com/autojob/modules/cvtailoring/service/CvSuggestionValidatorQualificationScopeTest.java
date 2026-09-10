package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CvSuggestionValidatorQualificationScopeTest {

    @Test
    void professionalSummaryMayUseConfirmedQualificationEvidence() {
        CvEvidenceService evidenceService =
                mock(
                        CvEvidenceService.class
                );

        CvSuggestionValidator validator =
                new CvSuggestionValidator(
                        evidenceService,
                        new CvSourceIdResolver()
                );

        CandidateProfile profile =
                profile();

        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap();

        SuggestionItem suggestion =
                new SuggestionItem(
                        "rewrite-summary",
                        SuggestionType.REWRITE,
                        Section.PROFESSIONAL_SUMMARY,
                        "professionalSummary",
                        "Healthcare professional.",
                        "Healthcare professional with a Registered Nurse License.",
                        "Surfaces confirmed profile qualification.",
                        List.of(),
                        List.of(
                                "professionalSummary",
                                "license:0:name"
                        )
                );

        assertThat(
                validator.validateAll(
                        profile,
                        evidenceMap,
                        List.of(
                                suggestion
                        )
                )
        ).containsExactly(
                suggestion
        );
    }

    @Test
    void workRewriteCannotBorrowProfileLevelLicenseEvidence() {
        CvEvidenceService evidenceService =
                mock(
                        CvEvidenceService.class
                );

        CvSuggestionValidator validator =
                new CvSuggestionValidator(
                        evidenceService,
                        new CvSourceIdResolver()
                );

        CandidateProfile profile =
                profile();

        CvEvidenceService.EvidenceMap evidenceMap =
                evidenceMap();

        SuggestionItem suggestion =
                new SuggestionItem(
                        "rewrite-work",
                        SuggestionType.REWRITE,
                        Section.WORK_EXPERIENCE,
                        "work:0:responsibility:0",
                        "Supported patient care.",
                        "Supported patient care as a licensed nurse.",
                        "Attempts to borrow global qualification evidence.",
                        List.of(),
                        List.of(
                                "work:0:responsibility:0",
                                "license:0:name"
                        )
                );

        assertThatThrownBy(
                () ->
                        validator.validateAll(
                                profile,
                                evidenceMap,
                                List.of(
                                        suggestion
                                )
                        )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "outside source scope"
                );
    }

    private CandidateProfile profile() {
        return CandidateProfile
                .builder()
                .professionalSummary(
                        "Healthcare professional."
                )
                .workExperiences(
                        List.of(
                                new CandidateProfile.WorkExperience(
                                        "Example Clinic",
                                        "Healthcare",
                                        "Clinical Assistant",
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
                                        true,
                                        null,
                                        null,
                                        List.of(
                                                "Supported patient care."
                                        ),
                                        List.of(),
                                        List.of(
                                                "Patient Care"
                                        ),
                                        List.of(),
                                        List.of()
                                )
                        )
                )
                .licenses(
                        List.of(
                                new CandidateProfile.LicenseEntry(
                                        "Registered Nurse License",
                                        "Example Nursing Board",
                                        "PRIVATE-LICENSE-NUMBER",
                                        "2025",
                                        null,
                                        false,
                                        "Vietnam"
                                )
                        )
                )
                .build();
    }

    private CvEvidenceService.EvidenceMap evidenceMap() {
        return new CvEvidenceService.EvidenceMap(
                List.of(
                        new EvidenceItem(
                                "professionalSummary",
                                Section.PROFESSIONAL_SUMMARY,
                                "professionalSummary",
                                EvidenceKind.TEXT,
                                "Healthcare professional.",
                                null
                        ),
                        new EvidenceItem(
                                "work:0:responsibility:0",
                                Section.WORK_EXPERIENCE,
                                "work:0",
                                EvidenceKind.TEXT,
                                "Supported patient care.",
                                null
                        ),
                        new EvidenceItem(
                                "license:0:name",
                                Section.LICENSE,
                                "license:0",
                                EvidenceKind.LICENSE,
                                "Registered Nurse License",
                                null
                        )
                )
        );
    }
}