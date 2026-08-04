package com.autojob.modules.cv.service;

import com.autojob.modules.cv.client.dto.CvParseResponse;
import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.domain.CvProcessingStatus;
import com.autojob.modules.cv.domain.RawCv;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateProfileMapperTest {

    private final CandidateProfileMapper mapper =
            new CandidateProfileMapper();

    @Test
    void shouldMapFullWarehouseProfileAndSourceMetadata() {
        Instant createdAt = Instant.parse(
                "2026-08-01T01:00:00Z"
        );
        Instant updatedAt = Instant.parse(
                "2026-08-04T03:00:00Z"
        );

        RawCv rawCv = rawCv();

        CandidateProfile existing =
                CandidateProfile.builder()
                        .id("profile-001")
                        .rawCvId("raw-cv-001")
                        .ownerUserId("user-001")
                        .parserVersion("rule-v0")
                        .createdAt(createdAt)
                        .updatedAt(createdAt)
                        .build();

        CandidateProfile result = mapper.toDocument(
                rawCv,
                response(),
                existing,
                updatedAt
        );

        assertThat(result.getId())
                .isEqualTo("profile-001");
        assertThat(result.getRawCvId())
                .isEqualTo("raw-cv-001");
        assertThat(result.getOwnerUserId())
                .isEqualTo("user-001");
        assertThat(result.getCreatedAt())
                .isEqualTo(createdAt);
        assertThat(result.getUpdatedAt())
                .isEqualTo(updatedAt);

        assertThat(result.getFullName())
                .isEqualTo("Trần Quốc Huy");
        assertThat(result.getHeadline())
                .isEqualTo("Warehouse Supervisor");
        assertThat(result.getProfessionalSummary())
                .contains("warehouse operations");
        assertThat(result.getCareerObjective())
                .contains("distribution center");

        assertThat(result.getContact().email())
                .isEqualTo("quoc.huy@example.com");
        assertThat(result.getContact().phone())
                .isEqualTo("+84901234567");
        assertThat(result.getContact().city())
                .isEqualTo("Binh Duong");

        assertThat(result.getLinks())
                .singleElement()
                .satisfies(link -> {
                    assertThat(link.type())
                            .isEqualTo(
                                    CandidateProfile.LinkType
                                            .LINKEDIN
                            );
                    assertThat(link.url())
                            .contains("linkedin.com");
                });

        assertThat(result.getTargetJobTitles())
                .containsExactly(
                        "Warehouse Supervisor",
                        "Distribution Center Supervisor"
                );
        assertThat(result.getTargetIndustries())
                .containsExactly(
                        "Logistics",
                        "Supply Chain"
                );
        assertThat(result.getPreferredLocations())
                .containsExactly(
                        "Binh Duong",
                        "Ho Chi Minh City"
                );
        assertThat(result.getPreferredWorkModes())
                .containsExactly(
                        CandidateProfile.WorkMode.ONSITE
                );
        assertThat(
                result.getPreferredEmploymentTypes()
        ).containsExactly(
                CandidateProfile.EmploymentType
                        .FULL_TIME
        );
        assertThat(result.getExpectedSalaryText())
                .isEqualTo("25,000,000 VND");
        assertThat(result.getAvailabilityText())
                .isEqualTo("Available in 30 days");

        assertThat(result.getSkills())
                .singleElement()
                .satisfies(skill -> {
                    assertThat(skill.name())
                            .isEqualTo(
                                    "Forklift Operation"
                            );
                    assertThat(skill.category())
                            .isEqualTo(
                                    CandidateProfile
                                            .SkillCategory
                                            .EQUIPMENT
                            );
                    assertThat(
                            skill.normalizedProficiency()
                    ).isEqualTo(
                            CandidateProfile
                                    .ProficiencyLevel
                                    .ADVANCED
                    );
                    assertThat(
                            skill.evidenceSources()
                    ).containsExactly(
                            "SKILLS",
                            "WORK_EXPERIENCE"
                    );
                });

        assertThat(result.getWorkExperiences())
                .singleElement()
                .satisfies(experience -> {
                    assertThat(
                            experience.companyName()
                    ).isEqualTo(
                            "An Phat Logistics"
                    );
                    assertThat(
                            experience.employmentType()
                    ).isEqualTo(
                            CandidateProfile
                                    .EmploymentType
                                    .FULL_TIME
                    );
                    assertThat(experience.workMode())
                            .isEqualTo(
                                    CandidateProfile
                                            .WorkMode
                                            .ONSITE
                            );
                    assertThat(
                            experience.durationMonths()
                    ).isEqualTo(54);
                    assertThat(experience.tools())
                            .containsExactly("SAP EWM");
                    assertThat(experience.equipment())
                            .containsExactly(
                                    "Reach Truck",
                                    "Handheld Scanner"
                            );
                });

        assertThat(result.getProjects())
                .singleElement()
                .satisfies(project -> {
                    assertThat(project.name())
                            .isEqualTo(
                                    "Warehouse Slotting Optimization"
                            );
                    assertThat(project.domain())
                            .isEqualTo("Logistics");
                    assertThat(project.teamSizeText())
                            .isEqualTo("8 people");
                });

        assertThat(result.getEducations())
                .singleElement()
                .satisfies(education -> {
                    assertThat(education.degree())
                            .isEqualTo(
                                    "Diploma in Logistics"
                            );
                    assertThat(
                            education
                                    .normalizedDegreeLevel()
                    ).isEqualTo(
                            CandidateProfile
                                    .EducationLevel
                                    .DIPLOMA
                    );
                });

        assertThat(result.getCertifications())
                .singleElement()
                .satisfies(certification -> {
                    assertThat(certification.name())
                            .isEqualTo(
                                    "Occupational Safety Training"
                            );
                    assertThat(
                            certification.relatedSkills()
                    ).containsExactly(
                            "Workplace Safety"
                    );
                });

        assertThat(result.getLicenses())
                .singleElement()
                .satisfies(license -> {
                    assertThat(license.name())
                            .isEqualTo(
                                    "Forklift Operator License"
                            );
                    assertThat(
                            license.licenseNumber()
                    ).isEqualTo(
                            "FL-2024-001"
                    );
                    assertThat(
                            license.jurisdiction()
                    ).isEqualTo("Vietnam");
                });

        assertThat(result.getTrainingCourses())
                .singleElement()
                .satisfies(training -> {
                    assertThat(training.name())
                            .isEqualTo(
                                    "Lean Warehouse Management"
                            );
                    assertThat(training.provider())
                            .isEqualTo(
                                    "Vietnam Logistics Association"
                            );
                });

        assertThat(result.getLanguages())
                .singleElement()
                .satisfies(language -> {
                    assertThat(language.language())
                            .isEqualTo("English");
                    assertThat(
                            language
                                    .normalizedProficiency()
                    ).isEqualTo(
                            CandidateProfile
                                    .ProficiencyLevel
                                    .INTERMEDIATE
                    );
                });

        assertThat(result.getAwards())
                .singleElement()
                .satisfies(award ->
                        assertThat(award.name())
                                .isEqualTo(
                                        "Safety Champion 2025"
                                )
                );

        assertThat(result.getPublications())
                .singleElement()
                .satisfies(publication -> {
                    assertThat(publication.title())
                            .isEqualTo(
                                    "Reducing Picking Errors"
                            );
                    assertThat(publication.authors())
                            .containsExactly(
                                    "Trần Quốc Huy"
                            );
                });

        assertThat(
                result.getVolunteerExperiences()
        )
                .singleElement()
                .satisfies(volunteer ->
                        assertThat(
                                volunteer
                                        .organizationName()
                        ).isEqualTo(
                                "Food Bank Binh Duong"
                        )
                );

        assertThat(result.getActivities())
                .singleElement()
                .satisfies(activity ->
                        assertThat(
                                activity.organization()
                        ).isEqualTo(
                                "Vietnam Logistics Association"
                        )
                );

        assertThat(result.getInterests())
                .containsExactly(
                        "Warehouse safety",
                        "Process improvement"
                );
        assertThat(result.getExperienceYears())
                .isEqualTo(8.5);
        assertThat(result.getSeniority())
                .isEqualTo(
                        CandidateProfile.Seniority
                                .SUPERVISOR
                );
        assertThat(
                result.getHighestEducationLevel()
        ).isEqualTo(
                CandidateProfile.EducationLevel
                        .DIPLOMA
        );
        assertThat(result.getRecentJobTitles())
                .containsExactly(
                        "Warehouse Supervisor"
                );
        assertThat(result.getRecentCompanies())
                .containsExactly(
                        "An Phat Logistics"
                );

        assertThat(result.getDetectedLanguage())
                .isEqualTo(
                        CandidateProfile
                                .DetectedLanguage
                                .EN
                );
        assertThat(result.getRawText())
                .isEqualTo(
                        "Warehouse Supervisor profile"
                );

        assertThat(result.getSections())
                .singleElement()
                .satisfies(section -> {
                    assertThat(
                            section.sectionType()
                    ).isEqualTo(
                            CandidateProfile
                                    .SectionType
                                    .WORK_EXPERIENCE
                    );
                    assertThat(section.startOffset())
                            .isZero();
                    assertThat(section.endOffset())
                            .isEqualTo(28);
                });

        assertThat(result.getParserVersion())
                .isEqualTo("rule-v1");
        assertThat(result.getParserWarnings())
                .containsExactly(
                        "MULTI_COLUMN_LAYOUT_SUSPECTED",
                        "TEXT_LAYOUT_MAY_BE_LOST"
                );
        assertThat(
                result.getParseQuality()
                        .overallScore()
        ).isEqualTo(0.91);
        assertThat(
                result.getParseQuality()
                        .ambiguousFields()
        ).containsExactly(
                "expectedSalaryText"
        );

        assertThat(result.getSourceBucket())
                .isEqualTo("autojob-cvs");
        assertThat(result.getSourceObjectKey())
                .isEqualTo(
                        "raw/2026/08/04/raw-cv-001/warehouse.pdf"
                );
        assertThat(
                result.getSourceOriginalFilename()
        ).isEqualTo(
                "warehouse.pdf"
        );
        assertThat(result.getSourceContentType())
                .isEqualTo("application/pdf");
        assertThat(result.getSourceSizeBytes())
                .isEqualTo(125_000L);
        assertThat(result.getSourceSha256())
                .isEqualTo("sha256-value");
    }

    @Test
    void shouldSetCreatedAtForNewProfile() {
        Instant now = Instant.parse(
                "2026-08-04T03:00:00Z"
        );

        CandidateProfile result = mapper.toDocument(
                rawCv(),
                response(),
                null,
                now
        );

        assertThat(result.getId()).isNull();
        assertThat(result.getCreatedAt())
                .isEqualTo(now);
        assertThat(result.getUpdatedAt())
                .isEqualTo(now);
    }

    private RawCv rawCv() {
        return RawCv.builder()
                .id("raw-cv-001")
                .ownerUserId("user-001")
                .bucket("autojob-cvs")
                .objectKey(
                        "raw/2026/08/04/raw-cv-001/warehouse.pdf"
                )
                .originalFilename("warehouse.pdf")
                .extension("pdf")
                .contentType("application/pdf")
                .sizeBytes(125_000L)
                .sha256("sha256-value")
                .status(
                        CvProcessingStatus.UPLOADED
                )
                .uploadedAt(
                        Instant.parse(
                                "2026-08-04T02:00:00Z"
                        )
                )
                .build();
    }

    private CvParseResponse response() {
        CvParseResponse.CandidateProfilePayload payload =
                new CvParseResponse
                        .CandidateProfilePayload(
                        "Trần Quốc Huy",
                        "Warehouse Supervisor",
                        "Supervisor with 8 years of warehouse operations experience.",
                        "Lead a safe and efficient distribution center.",
                        new CvParseResponse
                                .ContactInformation(
                                "quoc.huy@example.com",
                                "+84901234567",
                                "Thu Dau Mot, Binh Duong",
                                "Binh Duong",
                                "Binh Duong",
                                "Vietnam",
                                "75000"
                        ),
                        List.of(
                                new CvParseResponse
                                        .LinkEntry(
                                        CvParseResponse
                                                .LinkType
                                                .LINKEDIN,
                                        "https://linkedin.com/in/quochuy",
                                        "LinkedIn"
                                )
                        ),
                        List.of(
                                "Warehouse Supervisor",
                                "Distribution Center Supervisor"
                        ),
                        List.of(
                                "Logistics",
                                "Supply Chain"
                        ),
                        List.of(
                                "Binh Duong",
                                "Ho Chi Minh City"
                        ),
                        List.of(
                                CvParseResponse
                                        .WorkMode
                                        .ONSITE
                        ),
                        List.of(
                                CvParseResponse
                                        .EmploymentType
                                        .FULL_TIME
                        ),
                        "25,000,000 VND",
                        "Available in 30 days",
                        List.of(
                                new CvParseResponse.Skill(
                                        "Forklift Operation",
                                        "Forklift Operation",
                                        CvParseResponse
                                                .SkillCategory
                                                .EQUIPMENT,
                                        "Advanced",
                                        CvParseResponse
                                                .ProficiencyLevel
                                                .ADVANCED,
                                        6.0,
                                        "2026-07",
                                        List.of(
                                                "SKILLS",
                                                "WORK_EXPERIENCE"
                                        )
                                )
                        ),
                        List.of(
                                new CvParseResponse
                                        .WorkExperience(
                                        "An Phat Logistics",
                                        "Logistics",
                                        "Warehouse Supervisor",
                                        "Warehouse Supervisor",
                                        CvParseResponse
                                                .EmploymentType
                                                .FULL_TIME,
                                        "Binh Duong",
                                        CvParseResponse
                                                .WorkMode
                                                .ONSITE,
                                        "2022-01",
                                        "2026-07",
                                        false,
                                        54,
                                        "Managed inbound and outbound warehouse operations.",
                                        List.of(
                                                "Supervised 25 warehouse staff"
                                        ),
                                        List.of(
                                                "Reduced picking errors by 35%"
                                        ),
                                        List.of(
                                                "Inventory Control",
                                                "Team Leadership"
                                        ),
                                        List.of("SAP EWM"),
                                        List.of(
                                                "Reach Truck",
                                                "Handheld Scanner"
                                        )
                                )
                        ),
                        List.of(
                                new CvParseResponse
                                        .ProjectExperience(
                                        "Warehouse Slotting Optimization",
                                        "Project Lead",
                                        "Logistics",
                                        "2025-01",
                                        "2025-06",
                                        false,
                                        "Redesigned picking zones.",
                                        List.of(
                                                "Analyzed SKU velocity"
                                        ),
                                        List.of(
                                                "Improved picking productivity by 20%"
                                        ),
                                        List.of(
                                                "Warehouse Optimization"
                                        ),
                                        List.of("Excel"),
                                        List.of(
                                                "Handheld Scanner"
                                        ),
                                        "8 people",
                                        null,
                                        null
                                )
                        ),
                        List.of(
                                new CvParseResponse.Education(
                                        "Binh Duong College",
                                        "Diploma in Logistics",
                                        CvParseResponse
                                                .EducationLevel
                                                .DIPLOMA,
                                        "Logistics",
                                        "Warehouse Operations",
                                        "2014",
                                        "2016",
                                        false,
                                        "Good",
                                        List.of(
                                                "Merit Scholarship"
                                        ),
                                        "Logistics and warehouse program"
                                )
                        ),
                        List.of(
                                new CvParseResponse
                                        .Certification(
                                        "Occupational Safety Training",
                                        "Department of Labor",
                                        "2025-03",
                                        "2027-03",
                                        false,
                                        "CERT-001",
                                        "https://example.com/certification",
                                        List.of(
                                                "Workplace Safety"
                                        )
                                )
                        ),
                        List.of(
                                new CvParseResponse
                                        .LicenseEntry(
                                        "Forklift Operator License",
                                        "Binh Duong Vocational Center",
                                        "FL-2024-001",
                                        "2024-02",
                                        "2027-02",
                                        false,
                                        "Vietnam"
                                )
                        ),
                        List.of(
                                new CvParseResponse
                                        .LanguageSkill(
                                        "English",
                                        "Intermediate",
                                        CvParseResponse
                                                .ProficiencyLevel
                                                .INTERMEDIATE,
                                        "CEFR",
                                        "B1"
                                )
                        ),
                        List.of(
                                new CvParseResponse.Award(
                                        "Safety Champion 2025",
                                        "An Phat Logistics",
                                        "2025",
                                        "Recognized for warehouse safety leadership."
                                )
                        ),
                        List.of(
                                new CvParseResponse
                                        .Publication(
                                        "Reducing Picking Errors",
                                        List.of(
                                                "Trần Quốc Huy"
                                        ),
                                        "Vietnam Logistics Review",
                                        "2025-09",
                                        "https://example.com/article",
                                        "Operational improvement article."
                                )
                        ),
                        List.of(
                                new CvParseResponse
                                        .VolunteerExperience(
                                        "Food Bank Binh Duong",
                                        "Warehouse Volunteer",
                                        "2023",
                                        "2024",
                                        "Organized donated food inventory.",
                                        List.of(
                                                "Coordinated inbound donations"
                                        ),
                                        List.of(
                                                "Inventory Control"
                                        )
                                )
                        ),
                        List.of(
                                new CvParseResponse
                                        .ProfessionalActivity(
                                        "Logistics Operations Forum",
                                        "Vietnam Logistics Association",
                                        "Member",
                                        "2024",
                                        null,
                                        "Participates in operations workshops."
                                )
                        ),
                        List.of(
                                new CvParseResponse
                                        .TrainingCourse(
                                        "Lean Warehouse Management",
                                        "Vietnam Logistics Association",
                                        "2025-11",
                                        "24 hours",
                                        "Lean operations training.",
                                        List.of(
                                                "Process Improvement"
                                        )
                                )
                        ),
                        List.of(
                                "Warehouse safety",
                                "Process improvement"
                        ),
                        8.5,
                        CvParseResponse
                                .Seniority
                                .SUPERVISOR,
                        CvParseResponse
                                .EducationLevel
                                .DIPLOMA,
                        List.of(
                                "Warehouse Supervisor"
                        ),
                        List.of(
                                "An Phat Logistics"
                        ),
                        "Warehouse Supervisor profile",
                        List.of(
                                new CvParseResponse
                                        .ParsedSection(
                                        CvParseResponse
                                                .SectionType
                                                .WORK_EXPERIENCE,
                                        "Work Experience",
                                        0,
                                        28,
                                        "Warehouse Supervisor profile"
                                )
                        ),
                        List.of(
                                "TEXT_LAYOUT_MAY_BE_LOST"
                        ),
                        new CvParseResponse.ParseQuality(
                                0.91,
                                1.0,
                                0.88,
                                0.93,
                                List.of(),
                                List.of(
                                        "expectedSalaryText"
                                )
                        )
                );

        return new CvParseResponse(
                "raw-cv-001",
                "rule-v1",
                28,
                CvParseResponse
                        .DetectedLanguage
                        .EN,
                payload,
                List.of(
                        "MULTI_COLUMN_LAYOUT_SUSPECTED",
                        "TEXT_LAYOUT_MAY_BE_LOST"
                )
        );
    }
}