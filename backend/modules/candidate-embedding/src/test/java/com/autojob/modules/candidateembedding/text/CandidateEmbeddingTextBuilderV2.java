package com.autojob.modules.candidateembedding.text;

import com.autojob.modules.candidateembedding.config.CandidateEmbeddingProperties;
import com.autojob.modules.cv.domain.CandidateProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateEmbeddingTextBuilderV2Test {

    private CandidateEmbeddingProperties properties;

    private CandidateEmbeddingTextBuilder builder;

    @BeforeEach
    void setUp() {
        properties =
                new CandidateEmbeddingProperties();

        properties.setTextVersion(
                CandidateEmbeddingProperties.TEXT_VERSION_V2
        );

        properties.setTextMaxChars(
                10_000
        );

        builder =
                new CandidateEmbeddingTextBuilder(
                        properties
                );
    }

    @Test
    void v2AddsLicenseAndLanguageSections() {
        CandidateProfile profile =
                CandidateProfile
                        .builder()
                        .targetJobTitles(
                                List.of(
                                        "Clinical Nurse"
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

        String text =
                builder.build(
                        profile
                );

        assertThat(text)
                .contains(
                        "Licenses: Registered Nurse License"
                )
                .contains(
                        "Authority: Example Nursing Board"
                )
                .contains(
                        "Jurisdiction: Vietnam"
                )
                .contains(
                        "Languages: English"
                )
                .contains(
                        "Proficiency: Fluent"
                )
                .contains(
                        "Framework: IELTS"
                )
                .contains(
                        "Score: 7.5"
                )
                .doesNotContain(
                        "PRIVATE-LICENSE-NUMBER"
                );
    }

    @Test
    void v2ExcludesExpiredCertificationsAndLicenses() {
        CandidateProfile profile =
                CandidateProfile
                        .builder()
                        .targetJobTitles(
                                List.of(
                                        "Safety Coordinator"
                                )
                        )
                        .certifications(
                                List.of(
                                        new CandidateProfile.Certification(
                                                "Active Safety Certificate",
                                                "Safety Board",
                                                "2025",
                                                null,
                                                false,
                                                "ACTIVE-ID",
                                                "https://private.example/active",
                                                List.of()
                                        ),
                                        new CandidateProfile.Certification(
                                                "Expired Safety Certificate",
                                                "Old Safety Board",
                                                "2019",
                                                "2020",
                                                true,
                                                "EXPIRED-ID",
                                                "https://private.example/expired",
                                                List.of()
                                        )
                                )
                        )
                        .licenses(
                                List.of(
                                        new CandidateProfile.LicenseEntry(
                                                "Active Equipment License",
                                                "Equipment Board",
                                                "ACTIVE-LICENSE",
                                                "2025",
                                                null,
                                                false,
                                                "Vietnam"
                                        ),
                                        new CandidateProfile.LicenseEntry(
                                                "Expired Equipment License",
                                                "Old Equipment Board",
                                                "EXPIRED-LICENSE",
                                                "2019",
                                                "2020",
                                                true,
                                                "Vietnam"
                                        )
                                )
                        )
                        .build();

        String text =
                builder.build(
                        profile
                );

        assertThat(text)
                .contains(
                        "Active Safety Certificate"
                )
                .contains(
                        "Active Equipment License"
                )
                .doesNotContain(
                        "Expired Safety Certificate"
                )
                .doesNotContain(
                        "Expired Equipment License"
                )
                .doesNotContain(
                        "ACTIVE-ID"
                )
                .doesNotContain(
                        "EXPIRED-ID"
                )
                .doesNotContain(
                        "https://private.example/active"
                )
                .doesNotContain(
                        "https://private.example/expired"
                )
                .doesNotContain(
                        "ACTIVE-LICENSE"
                )
                .doesNotContain(
                        "EXPIRED-LICENSE"
                );
    }

    @Test
    void v1ContractDoesNotSilentlyGainLicenseOrLanguageSignals() {
        CandidateEmbeddingProperties v1Properties =
                new CandidateEmbeddingProperties();

        v1Properties.setTextVersion(
                CandidateEmbeddingProperties.TEXT_VERSION_V1
        );

        v1Properties.setTextMaxChars(
                10_000
        );

        CandidateEmbeddingTextBuilder v1Builder =
                new CandidateEmbeddingTextBuilder(
                        v1Properties
                );

        CandidateProfile profile =
                CandidateProfile
                        .builder()
                        .targetJobTitles(
                                List.of(
                                        "Clinical Nurse"
                                )
                        )
                        .licenses(
                                List.of(
                                        new CandidateProfile.LicenseEntry(
                                                "Registered Nurse License",
                                                "Example Nursing Board",
                                                null,
                                                null,
                                                null,
                                                false,
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
                                                null,
                                                null
                                        )
                                )
                        )
                        .build();

        String text =
                v1Builder.build(
                        profile
                );

        assertThat(text)
                .doesNotContain(
                        "Licenses:"
                )
                .doesNotContain(
                        "Registered Nurse License"
                )
                .doesNotContain(
                        "Languages:"
                )
                .doesNotContain(
                        "English"
                )
                .doesNotContain(
                        "Fluent"
                );
    }

    @Test
    void v2TextChangesWhenLicenseOrLanguageEvidenceChanges() {
        CandidateProfile first =
                CandidateProfile
                        .builder()
                        .targetJobTitles(
                                List.of(
                                        "Clinical Nurse"
                                )
                        )
                        .licenses(
                                List.of(
                                        new CandidateProfile.LicenseEntry(
                                                "Registered Nurse License",
                                                "Example Nursing Board",
                                                null,
                                                null,
                                                null,
                                                false,
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
                                                null,
                                                null
                                        )
                                )
                        )
                        .build();

        CandidateProfile second =
                CandidateProfile
                        .builder()
                        .targetJobTitles(
                                List.of(
                                        "Clinical Nurse"
                                )
                        )
                        .licenses(
                                List.of(
                                        new CandidateProfile.LicenseEntry(
                                                "Practical Nurse License",
                                                "Example Nursing Board",
                                                null,
                                                null,
                                                null,
                                                false,
                                                "Vietnam"
                                        )
                                )
                        )
                        .languages(
                                List.of(
                                        new CandidateProfile.LanguageSkill(
                                                "English",
                                                "Professional proficiency",
                                                CandidateProfile
                                                        .ProficiencyLevel
                                                        .ADVANCED,
                                                null,
                                                null
                                        )
                                )
                        )
                        .build();

        assertThat(
                builder.build(
                        second
                )
        ).isNotEqualTo(
                builder.build(
                        first
                )
        );
    }

    @Test
    void v2RespectsQualificationItemLimits() {
        properties.setLicensesMaxItems(
                1
        );

        properties.setLanguagesMaxItems(
                1
        );

        CandidateProfile profile =
                CandidateProfile
                        .builder()
                        .targetJobTitles(
                                List.of(
                                        "Operations Coordinator"
                                )
                        )
                        .licenses(
                                List.of(
                                        new CandidateProfile.LicenseEntry(
                                                "License A",
                                                null,
                                                null,
                                                null,
                                                null,
                                                false,
                                                null
                                        ),
                                        new CandidateProfile.LicenseEntry(
                                                "License B",
                                                null,
                                                null,
                                                null,
                                                null,
                                                false,
                                                null
                                        )
                                )
                        )
                        .languages(
                                List.of(
                                        new CandidateProfile.LanguageSkill(
                                                "English",
                                                null,
                                                null,
                                                null,
                                                null
                                        ),
                                        new CandidateProfile.LanguageSkill(
                                                "Japanese",
                                                null,
                                                null,
                                                null,
                                                null
                                        )
                                )
                        )
                        .build();

        assertThat(
                builder.build(
                        profile
                )
        )
                .contains(
                        "License A"
                )
                .doesNotContain(
                        "License B"
                )
                .contains(
                        "English"
                )
                .doesNotContain(
                        "Japanese"
                );
    }
}