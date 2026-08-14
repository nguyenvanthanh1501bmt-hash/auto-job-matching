package com.autojob.modules.jobembedding.text;

import com.autojob.modules.jobembedding.config.JobEmbeddingProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import com.autojob.modules.jobnormalizer.normalization.TextNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JobEmbeddingTextBuilderTest {

    private JobEmbeddingProperties properties;
    private JobEmbeddingTextBuilder builder;

    @BeforeEach
    void setUp() {
        properties =
                new JobEmbeddingProperties();

        properties.setTextVersion(
                "job-text-v2"
        );

        properties.setTextMaxChars(
                2_400
        );

        builder =
                new JobEmbeddingTextBuilder(
                        new TextNormalizer(),
                        properties
                );
    }

    @Test
    void shouldBuildE5PassageText() {
        NormalizedJob job =
                NormalizedJob.builder()
                        .title(
                                "Senior Java Backend Engineer"
                        )
                        .companyName(
                                "AutoJob"
                        )
                        .skills(
                                List.of(
                                        "Spring Boot",
                                        "Java"
                                )
                        )
                        .locations(
                                List.of(
                                        "Hồ Chí Minh"
                                )
                        )
                        .seniority(
                                SeniorityLevel.SENIOR
                        )
                        .jobType(
                                NormalizedJobType.FULL_TIME
                        )
                        .experienceMin(3.0)
                        .experienceMax(5.0)
                        .requirementsText(
                                "Strong Java and Spring Boot experience"
                        )
                        .build();

        String text =
                builder.build(job);

        assertThat(text)
                .startsWith(
                        "passage: Title: Senior Java Backend Engineer"
                )
                .contains(
                        "Skills: Java, Spring Boot"
                )
                .contains(
                        "Seniority: Senior"
                )
                .contains(
                        "Experience: 3 - 5 years"
                )
                .contains(
                        "Locations: Hồ Chí Minh"
                )
                .doesNotStartWith(
                        "query: "
                );
    }

    @Test
    void shouldBeDeterministicForSkillOrder() {
        NormalizedJob first =
                NormalizedJob.builder()
                        .title(
                                "Backend Engineer"
                        )
                        .skills(
                                List.of(
                                        "Spring Boot",
                                        "Java"
                                )
                        )
                        .build();

        NormalizedJob second =
                NormalizedJob.builder()
                        .title(
                                "Backend Engineer"
                        )
                        .skills(
                                List.of(
                                        "Java",
                                        "Spring Boot"
                                )
                        )
                        .build();

        assertThat(
                builder.build(first)
        ).isEqualTo(
                builder.build(second)
        );
    }

    @Test
    void shouldRespectTotalCharacterLimit() {
        properties.setTextMaxChars(
                500
        );

        NormalizedJob job =
                NormalizedJob.builder()
                        .title(
                                "Backend Engineer"
                        )
                        .descriptionText(
                                "x".repeat(2_000)
                        )
                        .build();

        String text =
                builder.build(job);

        assertThat(text)
                .hasSizeLessThanOrEqualTo(
                        500
                );

        assertThat(text)
                .startsWith(
                        "passage: "
                );
    }

    @Test
    void shouldReturnNullWhenJobHasNoEmbeddableContent() {
        assertThat(
                builder.build(
                        NormalizedJob.builder()
                                .build()
                )
        ).isNull();
    }
}