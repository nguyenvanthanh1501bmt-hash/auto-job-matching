package com.autojob.modules.candidateembedding.text;

import com.autojob.modules.candidateembedding.config.CandidateEmbeddingProperties;
import com.autojob.modules.cv.domain.CandidateProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateEmbeddingTextBuilderTest {

    private CandidateEmbeddingProperties properties;
    private CandidateEmbeddingTextBuilder builder;

    @BeforeEach
    void setUp() {
        properties = new CandidateEmbeddingProperties();
        builder = new CandidateEmbeddingTextBuilder(properties);
    }

    @Test
    void shouldBuildSectionsInStableSignalOrder() {
        String text = builder.build(fullProfile());

        assertThat(text).containsSubsequence(
                "Target roles:",
                "Headline:",
                "Skills:",
                "Seniority:",
                "Experience:",
                "Preferred locations:",
                "Recent titles:",
                "Professional summary:",
                "Work experience:",
                "Projects:",
                "Education:",
                "Certifications:"
        );
    }

    @Test
    void shouldStartWithQueryPrefix() {
        assertThat(builder.build(fullProfile()))
                .startsWith("query: ");
    }

    @Test
    void shouldPreferNormalizedSkillName() {
        CandidateProfile profile = minimalProfile();
        profile.setSkills(List.of(
                new CandidateProfile.Skill(
                        "SpringBoot",
                        "Spring Boot",
                        CandidateProfile.SkillCategory.TECHNICAL,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                )
        ));

        assertThat(builder.build(profile))
                .contains("Skills: Spring Boot")
                .doesNotContain("SpringBoot");
    }

    @Test
    void shouldFallbackToRawSkillNameWhenNormalizedNameBlank() {
        CandidateProfile profile = minimalProfile();
        profile.setSkills(List.of(
                new CandidateProfile.Skill(
                        "Kafka",
                        "  ",
                        CandidateProfile.SkillCategory.TECHNICAL,
                        null,
                        null,
                        null,
                        null,
                        List.of()
                )
        ));

        assertThat(builder.build(profile))
                .contains("Skills: Kafka");
    }

    @Test
    void shouldDeduplicateSkillsCaseInsensitively() {
        CandidateProfile profile = minimalProfile();
        profile.setSkills(List.of(
                skill("Java", "Java"),
                skill("java", "java"),
                skill("JAVA", "JAVA")
        ));

        String text = builder.build(profile);

        assertThat(text)
                .contains("Skills: JAVA");

        assertThat(text.split("JAVA", -1))
                .hasSize(2);
    }

    @Test
    void shouldSortSkillsLocationsAndTitlesDeterministically() {
        CandidateProfile profile = minimalProfile();

        profile.setSkills(List.of(
                skill("MongoDB", "MongoDB"),
                skill("Docker", "Docker"),
                skill("Java", "Java")
        ));

        profile.setPreferredLocations(
                List.of(
                        "Remote",
                        "Hồ Chí Minh"
                )
        );

        profile.setRecentJobTitles(
                List.of(
                        "Java Developer",
                        "Backend Engineer"
                )
        );

        assertThat(builder.build(profile))
                .contains(
                        "Skills: Docker, Java, MongoDB"
                )
                .contains(
                        "Preferred locations: Hồ Chí Minh, Remote"
                )
                .contains(
                        "Recent titles: Backend Engineer, Java Developer"
                );
    }

    @Test
    void shouldExcludeFullNameEmailAndPhone() {
        CandidateProfile profile = fullProfile();

        assertThat(builder.build(profile))
                .doesNotContain(profile.getFullName())
                .doesNotContain(
                        profile
                                .getContact()
                                .email()
                )
                .doesNotContain(
                        profile
                                .getContact()
                                .phone()
                )
                .doesNotContain(
                        profile
                                .getLinks()
                                .getFirst()
                                .url()
                )
                .doesNotContain(
                        profile.getExpectedSalaryText()
                );
    }

    @Test
    void shouldExcludeSourceMetadata() {
        CandidateProfile profile = fullProfile();

        assertThat(builder.build(profile))
                .doesNotContain(
                        profile.getSourceBucket()
                )
                .doesNotContain(
                        profile.getSourceObjectKey()
                )
                .doesNotContain(
                        profile.getSourceOriginalFilename()
                )
                .doesNotContain(
                        profile.getSourceSha256()
                );
    }

    @Test
    void shouldExcludeRawText() {
        CandidateProfile profile = fullProfile();

        assertThat(builder.build(profile))
                .doesNotContain(
                        "RAW-CV-SECRET-TEXT"
                );
    }

    @Test
    void shouldNeverRenderNullLiteral() {
        CandidateProfile profile = minimalProfile();

        profile.setHeadline(null);
        profile.setProfessionalSummary(null);

        assertThat(builder.build(profile))
                .doesNotContain("null");
    }

    @Test
    void shouldSkipBlankFields() {
        CandidateProfile profile = minimalProfile();

        profile.setHeadline("   ");

        profile.setPreferredLocations(
                List.of(
                        " ",
                        "Remote"
                )
        );

        assertThat(builder.build(profile))
                .doesNotContain("Headline:")
                .contains(
                        "Preferred locations: Remote"
                );
    }

    @Test
    void shouldPreserveVietnameseUnicode() {
        CandidateProfile profile = minimalProfile();

        profile.setHeadline(
                "Kỹ sư phần mềm cao cấp"
        );

        profile.setPreferredLocations(
                List.of("Hồ Chí Minh")
        );

        assertThat(builder.build(profile))
                .contains(
                        "Kỹ sư phần mềm cao cấp"
                )
                .contains(
                        "Hồ Chí Minh"
                );
    }

    @Test
    void shouldRespectConfiguredTotalLength() {
        properties.setTextMaxChars(120);

        String text = builder.build(
                fullProfile()
        );

        assertThat(text.length())
                .isLessThanOrEqualTo(120);
    }

    @Test
    void shouldKeepHighSignalSectionsBeforeLowSignalWhenTruncated() {
        properties.setTextMaxChars(150);

        String text = builder.build(
                fullProfile()
        );

        assertThat(text)
                .startsWith(
                        "query: Target roles:"
                )
                .contains(
                        "Headline: Senior Backend Developer"
                )
                .contains("Skills:")
                .doesNotContain(
                        "Certifications:"
                );
    }

    @Test
    void shouldProduceSameTextForSameBusinessData() {
        CandidateProfile first =
                fullProfile();

        CandidateProfile second =
                fullProfile();

        assertThat(
                builder.build(second)
        ).isEqualTo(
                builder.build(first)
        );
    }

    @Test
    void shouldChangeWhenMatchingBusinessDataChanges() {
        CandidateProfile base =
                fullProfile();

        String original =
                builder.build(base);

        CandidateProfile skillChanged =
                fullProfile();

        skillChanged.setSkills(
                List.of(
                        skill("Go", "Go")
                )
        );

        CandidateProfile titleChanged =
                fullProfile();

        titleChanged.setHeadline(
                "Staff Platform Engineer"
        );

        CandidateProfile experienceChanged =
                fullProfile();

        experienceChanged.setExperienceYears(
                9.0
        );

        assertThat(
                builder.build(skillChanged)
        ).isNotEqualTo(original);

        assertThat(
                builder.build(titleChanged)
        ).isNotEqualTo(original);

        assertThat(
                builder.build(experienceChanged)
        ).isNotEqualTo(original);
    }

    @Test
    void shouldIgnoreIdTimestampAndSourceFilenameChanges() {
        CandidateProfile first =
                fullProfile();

        CandidateProfile second =
                fullProfile();

        second.setId(
                "different-profile-id"
        );

        second.setCreatedAt(
                Instant.parse(
                        "2030-01-01T00:00:00Z"
                )
        );

        second.setUpdatedAt(
                Instant.parse(
                        "2030-01-02T00:00:00Z"
                )
        );

        second.setSourceOriginalFilename(
                "totally-different.pdf"
        );

        assertThat(
                builder.build(second)
        ).isEqualTo(
                builder.build(first)
        );
    }

    @Test
    void shouldReturnNullForEmptyProfile() {
        assertThat(
                builder.build(
                        CandidateProfile
                                .builder()
                                .build()
                )
        ).isNull();
    }

    @Test
    void shouldNotSplitSurrogatePairDuringTruncation() {
        CandidateProfile profile =
                minimalProfile();

        profile.setTargetJobTitles(
                List.of("A😀B")
        );

        properties.setTextMaxChars(
                "query: Target roles: A"
                        .length()
                        + 1
        );

        String text =
                builder.build(profile);

        assertThat(text)
                .doesNotEndWith("\uD83D");

        assertThat(text.length())
                .isLessThanOrEqualTo(
                        properties
                                .getTextMaxChars()
                );
    }

    private CandidateProfile minimalProfile() {
        return CandidateProfile
                .builder()
                .id("profile-001")
                .rawCvId("raw-cv-001")
                .parserVersion("rule-v1")
                .targetJobTitles(
                        List.of(
                                "Backend Engineer"
                        )
                )
                .build();
    }

    private CandidateProfile fullProfile() {
        return CandidateProfile
                .builder()
                .id("profile-001")
                .rawCvId("raw-cv-001")
                .ownerUserId("user-001")
                .fullName(
                        "Nguyễn Văn Bí Mật"
                )
                .headline(
                        "Senior Backend Developer"
                )
                .professionalSummary(
                        "Xây dựng hệ thống Java phân tán, API và data platform."
                )
                .contact(
                        new CandidateProfile.ContactInformation(
                                "secret@example.com",
                                "+84901234567",
                                "Private address",
                                "Hồ Chí Minh",
                                null,
                                "Vietnam",
                                null
                        )
                )
                .links(
                        List.of(
                                new CandidateProfile.LinkEntry(
                                        CandidateProfile.LinkType.LINKEDIN,
                                        "https://linkedin.com/in/private-candidate",
                                        "LinkedIn"
                                )
                        )
                )
                .targetJobTitles(
                        List.of(
                                "Senior Java Backend Engineer",
                                "Backend Engineer"
                        )
                )
                .preferredLocations(
                        List.of(
                                "Remote",
                                "Hồ Chí Minh"
                        )
                )
                .skills(
                        List.of(
                                skill(
                                        "springboot",
                                        "Spring Boot"
                                ),
                                skill(
                                        "Java",
                                        "Java"
                                ),
                                skill(
                                        "Docker",
                                        "Docker"
                                ),
                                skill(
                                        "MongoDB",
                                        "MongoDB"
                                ),
                                skill(
                                        "Kafka",
                                        "Kafka"
                                )
                        )
                )
                .seniority(
                        CandidateProfile
                                .Seniority
                                .SENIOR
                )
                .experienceYears(5.0)
                .recentJobTitles(
                        List.of(
                                "Java Developer",
                                "Backend Engineer"
                        )
                )
                .workExperiences(
                        List.of(
                                new CandidateProfile.WorkExperience(
                                        "Private Company",
                                        "Fintech",
                                        "Sr Backend Dev",
                                        "Backend Engineer",
                                        CandidateProfile.EmploymentType.FULL_TIME,
                                        "Hồ Chí Minh",
                                        CandidateProfile.WorkMode.HYBRID,
                                        "2022-01",
                                        "2026-01",
                                        false,
                                        48,
                                        "Built high-throughput Java services",
                                        List.of(
                                                "Designed APIs",
                                                "Improved Kafka consumers"
                                        ),
                                        List.of(
                                                "Reduced latency 30%"
                                        ),
                                        List.of(
                                                "Java",
                                                "Spring Boot",
                                                "Kafka"
                                        ),
                                        List.of(
                                                "Docker"
                                        ),
                                        List.of()
                                )
                        )
                )
                .projects(
                        List.of(
                                new CandidateProfile.ProjectExperience(
                                        "Job Matching Platform",
                                        "Backend Engineer",
                                        "Recruitment",
                                        "2025-01",
                                        null,
                                        true,
                                        "Built MongoDB and vector-search integration",
                                        List.of(
                                                "Designed service APIs"
                                        ),
                                        List.of(
                                                "Improved relevance"
                                        ),
                                        List.of(
                                                "Java",
                                                "MongoDB"
                                        ),
                                        List.of(
                                                "Docker"
                                        ),
                                        List.of(),
                                        "4",
                                        "https://private.example/project",
                                        "https://github.com/private/repo"
                                )
                        )
                )
                .educations(
                        List.of(
                                new CandidateProfile.Education(
                                        "Đại học Bách Khoa",
                                        "Bachelor of Engineering",
                                        CandidateProfile.EducationLevel.BACHELOR,
                                        "Computer Science",
                                        null,
                                        "2016",
                                        "2020",
                                        false,
                                        null,
                                        List.of(
                                                "Distributed systems thesis"
                                        ),
                                        null
                                )
                        )
                )
                .certifications(
                        List.of(
                                new CandidateProfile.Certification(
                                        "AWS Certified Developer",
                                        "Amazon Web Services",
                                        "2024-01",
                                        null,
                                        false,
                                        "PRIVATE-CREDENTIAL-ID",
                                        "https://private.example/credential",
                                        List.of("AWS")
                                )
                        )
                )
                .expectedSalaryText("$5000")
                .rawText(
                        "RAW-CV-SECRET-TEXT"
                )
                .parserVersion("rule-v1")
                .sourceBucket(
                        "private-cv-bucket"
                )
                .sourceObjectKey(
                        "raw/private/candidate.pdf"
                )
                .sourceOriginalFilename(
                        "nguyen-van-bi-mat.pdf"
                )
                .sourceSha256(
                        "private-file-sha"
                )
                .createdAt(
                        Instant.parse(
                                "2026-08-01T00:00:00Z"
                        )
                )
                .updatedAt(
                        Instant.parse(
                                "2026-08-02T00:00:00Z"
                        )
                )
                .build();
    }

    private CandidateProfile.Skill skill(
            String name,
            String normalizedName
    ) {
        return new CandidateProfile.Skill(
                name,
                normalizedName,
                CandidateProfile.SkillCategory.TECHNICAL,
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}