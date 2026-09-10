package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.jobnormalizer.normalization.SkillNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CvEvidenceServiceMultiIndustryTest {

    @Test
    void buildsGenericQualificationEvidenceWithoutCredentialSecrets() {
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

        CvEvidenceService service =
                new CvEvidenceService(
                        skillNormalizer
                );

        CandidateProfile profile =
                CandidateProfile
                        .builder()
                        .professionalSummary(
                                "Healthcare professional."
                        )
                        .educations(
                                List.of(
                                        new CandidateProfile.Education(
                                                "Example University",
                                                "Bachelor of Nursing",
                                                CandidateProfile
                                                        .EducationLevel
                                                        .BACHELOR,
                                                "Nursing",
                                                "Clinical Nursing",
                                                "2018",
                                                "2022",
                                                false,
                                                "3.8",
                                                List.of(),
                                                null
                                        )
                                )
                        )
                        .certifications(
                                List.of(
                                        new CandidateProfile.Certification(
                                                "Basic Life Support",
                                                "Example Medical Association",
                                                "2025",
                                                null,
                                                false,
                                                "SECRET-CREDENTIAL-ID",
                                                "https://private.example/cert",
                                                List.of(
                                                        "Patient Care"
                                                )
                                        ),
                                        new CandidateProfile.Certification(
                                                "Expired Safety Certificate",
                                                "Old Authority",
                                                "2019",
                                                "2020",
                                                true,
                                                "EXPIRED-ID",
                                                null,
                                                List.of()
                                        )
                                )
                        )
                        .licenses(
                                List.of(
                                        new CandidateProfile.LicenseEntry(
                                                "Registered Nurse License",
                                                "Example Nursing Board",
                                                "SECRET-LICENSE-NUMBER",
                                                "2025",
                                                null,
                                                false,
                                                "Vietnam"
                                        ),
                                        new CandidateProfile.LicenseEntry(
                                                "Expired Practice License",
                                                "Old Board",
                                                "OLD-LICENSE",
                                                "2018",
                                                "2020",
                                                true,
                                                "Vietnam"
                                        )
                                )
                        )
                        .languages(
                                List.of(
                                        new CandidateProfile.LanguageSkill(
                                                "English",
                                                "Fluent",
                                                CandidateProfile
                                                        .ProficiencyLevel
                                                        .FLUENT,
                                                "IELTS",
                                                "7.5"
                                        )
                                )
                        )
                        .build();

        CvEvidenceService.EvidenceMap evidenceMap =
                service.build(
                        profile
                );

        assertThat(
                evidenceMap.items()
        )
                .extracting(
                        EvidenceItem::id
                )
                .contains(
                        "education:0:degree",
                        "education:0:fieldOfStudy",
                        "education:0:specialization",
                        "education:0:institution",
                        "certification:0:name",
                        "certification:0:issuer",
                        "license:0:name",
                        "license:0:authority",
                        "license:0:jurisdiction",
                        "language:0:name",
                        "language:0:proficiency",
                        "language:0:framework",
                        "language:0:score"
                )
                .doesNotContain(
                        "certification:1:name",
                        "license:1:name"
                );

        assertEvidence(
                evidenceMap,
                "education:0:degree",
                Section.EDUCATION,
                EvidenceKind.EDUCATION,
                "Bachelor of Nursing"
        );

        assertEvidence(
                evidenceMap,
                "certification:0:name",
                Section.CERTIFICATION,
                EvidenceKind.CERTIFICATION,
                "Basic Life Support"
        );

        assertEvidence(
                evidenceMap,
                "license:0:name",
                Section.LICENSE,
                EvidenceKind.LICENSE,
                "Registered Nurse License"
        );

        assertEvidence(
                evidenceMap,
                "language:0:name",
                Section.LANGUAGE,
                EvidenceKind.LANGUAGE,
                "English"
        );

        assertEvidence(
                evidenceMap,
                "language:0:proficiency",
                Section.LANGUAGE,
                EvidenceKind.LANGUAGE,
                "Fluent"
        );

        assertThat(
                evidenceMap.items()
        )
                .extracting(
                        EvidenceItem::text
                )
                .doesNotContain(
                        "SECRET-CREDENTIAL-ID",
                        "https://private.example/cert",
                        "SECRET-LICENSE-NUMBER",
                        "3.8"
                );
    }

    private void assertEvidence(
            CvEvidenceService.EvidenceMap evidenceMap,
            String id,
            Section section,
            EvidenceKind kind,
            String text
    ) {
        EvidenceItem item =
                evidenceMap
                        .items()
                        .stream()
                        .filter(
                                evidence ->
                                        id.equals(
                                                evidence.id()
                                        )
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(
                item.section()
        ).isEqualTo(
                section
        );

        assertThat(
                item.kind()
        ).isEqualTo(
                kind
        );

        assertThat(
                item.text()
        ).isEqualTo(
                text
        );

        assertThat(
                item.canonicalSkillKey()
        ).isNull();
    }
}