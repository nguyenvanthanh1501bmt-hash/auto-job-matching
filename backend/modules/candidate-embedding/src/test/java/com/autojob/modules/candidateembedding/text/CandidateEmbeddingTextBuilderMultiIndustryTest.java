package com.autojob.modules.candidateembedding.text;

import com.autojob.modules.candidateembedding.config.CandidateEmbeddingProperties;
import com.autojob.modules.cv.domain.CandidateProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateEmbeddingTextBuilderMultiIndustryTest {

    private CandidateEmbeddingTextBuilder builder;

    @BeforeEach
    void setUp() {
        CandidateEmbeddingProperties properties =
                new CandidateEmbeddingProperties();

        properties.setTextMaxChars(
                10_000
        );

        builder =
                new CandidateEmbeddingTextBuilder(
                        properties
                );
    }

    @Test
    void accountingEvidenceIsRenderedWithoutTechnologyAssumptions() {
        CandidateProfile profile =
                baseProfile(
                        "Accountant",
                        List.of(
                                skill(
                                        "Excel",
                                        CandidateProfile.SkillCategory.SOFTWARE
                                ),
                                skill(
                                        "Financial Reporting",
                                        CandidateProfile.SkillCategory.ACCOUNTING
                                )
                        ),
                        new CandidateProfile.WorkExperience(
                                "Example Accounting Co",
                                "Accounting",
                                "Accountant",
                                "Accountant",
                                CandidateProfile.EmploymentType.FULL_TIME,
                                "Ho Chi Minh City",
                                CandidateProfile.WorkMode.ONSITE,
                                "2023-01",
                                null,
                                true,
                                null,
                                "Prepared monthly financial reports.",
                                List.of(
                                        "Reconciled financial records"
                                ),
                                List.of(),
                                List.of(
                                        "Financial Reporting"
                                ),
                                List.of(
                                        "Excel"
                                ),
                                List.of()
                        )
                );

        String text =
                builder.build(
                        profile
                );

        assertThat(text)
                .contains(
                        "Target roles: Accountant"
                )
                .contains(
                        "Excel"
                )
                .contains(
                        "Financial Reporting"
                )
                .contains(
                        "Prepared monthly financial reports."
                )
                .doesNotContain(
                        "Java"
                )
                .doesNotContain(
                        "Spring Boot"
                );
    }

    @Test
    void salesEvidenceIsRenderedWithoutTechnologyAssumptions() {
        CandidateProfile profile =
                baseProfile(
                        "B2B Account Executive",
                        List.of(
                                skill(
                                        "B2B Sales",
                                        CandidateProfile.SkillCategory.SALES
                                ),
                                skill(
                                        "Account Management",
                                        CandidateProfile.SkillCategory.SALES
                                )
                        ),
                        new CandidateProfile.WorkExperience(
                                "Example Sales Co",
                                "Business Services",
                                "Account Executive",
                                "Account Executive",
                                CandidateProfile.EmploymentType.FULL_TIME,
                                "Hanoi",
                                CandidateProfile.WorkMode.HYBRID,
                                "2022-01",
                                null,
                                true,
                                null,
                                "Managed business client accounts.",
                                List.of(
                                        "Handled B2B client relationships"
                                ),
                                List.of(),
                                List.of(
                                        "B2B Sales",
                                        "Account Management"
                                ),
                                List.of(),
                                List.of()
                        )
                );

        assertThat(
                builder.build(
                        profile
                )
        )
                .contains(
                        "B2B Sales"
                )
                .contains(
                        "Account Management"
                )
                .contains(
                        "Handled B2B client relationships"
                );
    }

    @Test
    void logisticsToolsAndEquipmentAreRendered() {
        CandidateProfile profile =
                baseProfile(
                        "Warehouse Coordinator",
                        List.of(
                                skill(
                                        "Inventory Control",
                                        CandidateProfile.SkillCategory.BUSINESS
                                )
                        ),
                        new CandidateProfile.WorkExperience(
                                "Example Logistics Co",
                                "Logistics",
                                "Warehouse Coordinator",
                                "Warehouse Coordinator",
                                CandidateProfile.EmploymentType.FULL_TIME,
                                "Binh Duong",
                                CandidateProfile.WorkMode.ONSITE,
                                "2021-01",
                                null,
                                true,
                                null,
                                "Coordinated warehouse inventory operations.",
                                List.of(
                                        "Performed inventory control"
                                ),
                                List.of(),
                                List.of(
                                        "Inventory Control"
                                ),
                                List.of(
                                        "WMS"
                                ),
                                List.of(
                                        "Forklift"
                                )
                        )
                );

        assertThat(
                builder.build(
                        profile
                )
        )
                .contains(
                        "Inventory Control"
                )
                .contains(
                        "Tools: WMS"
                )
                .contains(
                        "Equipment: Forklift"
                );
    }

    @Test
    void healthcareEvidenceAndCertificationAreRendered() {
        CandidateProfile profile =
                baseProfile(
                        "Clinical Assistant",
                        List.of(
                                skill(
                                        "Patient Care",
                                        CandidateProfile.SkillCategory.HEALTHCARE
                                )
                        ),
                        new CandidateProfile.WorkExperience(
                                "Example Clinic",
                                "Healthcare",
                                "Clinical Assistant",
                                "Clinical Assistant",
                                CandidateProfile.EmploymentType.FULL_TIME,
                                "Da Nang",
                                CandidateProfile.WorkMode.ONSITE,
                                "2024-01",
                                null,
                                true,
                                null,
                                "Supported patient care and clinical procedures.",
                                List.of(
                                        "Assisted with patient care"
                                ),
                                List.of(),
                                List.of(
                                        "Patient Care"
                                ),
                                List.of(),
                                List.of()
                        )
                );

        profile.setCertifications(
                List.of(
                        new CandidateProfile.Certification(
                                "Basic Life Support",
                                "Example Medical Association",
                                "2025-01",
                                null,
                                false,
                                "credential-private",
                                "https://private.example/credential",
                                List.of(
                                        "Patient Care"
                                )
                        )
                )
        );

        String text =
                builder.build(
                        profile
                );

        assertThat(text)
                .contains(
                        "Patient Care"
                )
                .contains(
                        "Supported patient care and clinical procedures."
                )
                .contains(
                        "Certifications: Basic Life Support"
                )
                /*
                 * Credential identifiers/URLs are not useful
                 * matching signals and must not leak into
                 * embedding text.
                 */
                .doesNotContain(
                        "credential-private"
                )
                .doesNotContain(
                        "https://private.example/credential"
                );
    }

    private CandidateProfile baseProfile(
            String targetRole,
            List<CandidateProfile.Skill> skills,
            CandidateProfile.WorkExperience workExperience
    ) {
        return CandidateProfile
                .builder()
                .id(
                        "candidate-1"
                )
                .ownerUserId(
                        "user-1"
                )
                .targetJobTitles(
                        List.of(
                                targetRole
                        )
                )
                .skills(
                        skills
                )
                .workExperiences(
                        List.of(
                                workExperience
                        )
                )
                .parserVersion(
                        "parser-v1"
                )
                .build();
    }

    private CandidateProfile.Skill skill(
            String name,
            CandidateProfile.SkillCategory category
    ) {
        return new CandidateProfile.Skill(
                name,
                name,
                category,
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}