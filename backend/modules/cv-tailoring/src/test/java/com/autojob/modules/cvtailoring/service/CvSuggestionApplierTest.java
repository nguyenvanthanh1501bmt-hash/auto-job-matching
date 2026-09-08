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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CvSuggestionApplierTest {

    @Test
    void emphasizeAddsTopLevelSkillWithoutChangingOriginalOrInflatingEvidenceSource() {

        CvEvidenceService evidenceService =
                mock(
                        CvEvidenceService.class
                );

        CvSourceIdResolver sourceIdResolver =
                new CvSourceIdResolver();

        CvSuggestionApplier applier =
                new CvSuggestionApplier(
                        evidenceService,
                        sourceIdResolver
                );

        CandidateProfile original =
                CandidateProfile
                        .builder()

                        .id(
                                "candidate-1"
                        )

                        .ownerUserId(
                                "user-1"
                        )

                        .skills(
                                List.of(
                                        new CandidateProfile.Skill(
                                                "Java",
                                                "Java",
                                                CandidateProfile
                                                        .SkillCategory
                                                        .TECHNICAL,
                                                null,
                                                CandidateProfile
                                                        .ProficiencyLevel
                                                        .UNKNOWN,
                                                null,
                                                null,
                                                List.of(
                                                        "SKILLS_SECTION"
                                                )
                                        )
                                )
                        )

                        .projects(
                                List.of(
                                        new CandidateProfile.ProjectExperience(
                                                "Project A",
                                                null,
                                                null,
                                                null,
                                                null,
                                                false,
                                                "Built a backend application.",
                                                List.of(),
                                                List.of(),
                                                List.of(
                                                        "PostgreSQL"
                                                ),
                                                List.of(),
                                                List.of(),
                                                null,
                                                null,
                                                null
                                        )
                                )
                        )

                        .build();

        CvEvidenceService.EvidenceMap evidenceMap =
                new CvEvidenceService.EvidenceMap(
                        List.of(
                                new EvidenceItem(
                                        "project:0:skill:0",
                                        Section.PROJECT,
                                        "project:0",
                                        EvidenceKind.SKILL,
                                        "PostgreSQL",
                                        "postgresql"
                                )
                        )
                );

        SuggestionItem suggestion =
                new SuggestionItem(
                        "emphasize-0",
                        SuggestionType.EMPHASIZE,
                        Section.SKILLS,
                        "skills",
                        null,
                        "PostgreSQL",
                        "Surface existing project evidence.",
                        List.of(
                                "PostgreSQL"
                        ),
                        List.of(
                                "project:0:skill:0"
                        )
                );

        when(
                evidenceService
                        .canonicalSkillKey(
                                "PostgreSQL"
                        )
        ).thenReturn(
                "postgresql"
        );

        when(
                evidenceService
                        .canonicalSkillKey(
                                "Java"
                        )
        ).thenReturn(
                "java"
        );

        CandidateProfile temporary =
                applier.apply(
                        original,
                        List.of(
                                suggestion
                        ),
                        evidenceMap
                );

        /*
         * Original không đổi.
         */
        assertThat(
                original.getSkills()
        ).hasSize(
                1
        );

        /*
         * Temporary là object khác.
         */
        assertThat(
                temporary
        ).isNotSameAs(
                original
        );

        assertThat(
                temporary.getSkills()
        ).hasSize(
                2
        );

        CandidateProfile.Skill added =
                temporary
                        .getSkills()
                        .get(
                                1
                        );

        assertThat(
                added.name()
        ).isEqualTo(
                "PostgreSQL"
        );

        /*
         * Project evidence vẫn là PROJECTS.
         */
        assertThat(
                added.evidenceSources()
        ).containsExactly(
                "PROJECTS"
        );

        /*
         * Tuyệt đối không tự nâng thành
         * evidence của Skills section.
         */
        assertThat(
                added.evidenceSources()
        ).doesNotContain(
                "SKILLS_SECTION"
        );
    }

    @Test
    void rewriteReplacesOnlyTemporaryWorkResponsibility() {

        CvEvidenceService evidenceService =
                mock(
                        CvEvidenceService.class
                );

        CvSourceIdResolver sourceIdResolver =
                new CvSourceIdResolver();

        CvSuggestionApplier applier =
                new CvSuggestionApplier(
                        evidenceService,
                        sourceIdResolver
                );

        CandidateProfile original =
                CandidateProfile
                        .builder()

                        .id(
                                "candidate-1"
                        )

                        .ownerUserId(
                                "user-1"
                        )

                        .workExperiences(
                                List.of(
                                        new CandidateProfile.WorkExperience(
                                                "Example Co",
                                                null,
                                                "Backend Developer",
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
                                                false,
                                                null,
                                                null,
                                                List.of(
                                                        "Developed backend services."
                                                ),
                                                List.of(),
                                                List.of(
                                                        "Java",
                                                        "Spring Boot"
                                                ),
                                                List.of(),
                                                List.of()
                                        )
                                )
                        )

                        .build();

        SuggestionItem suggestion =
                new SuggestionItem(
                        "rewrite-0",
                        SuggestionType.REWRITE,
                        Section.WORK_EXPERIENCE,
                        "work:0:responsibility:0",
                        "Developed backend services.",
                        "Developed RESTful backend services "
                                + "using Java and Spring Boot.",
                        "Clarifies existing evidence.",
                        List.of(
                                "Java",
                                "Spring Boot"
                        ),
                        List.of(
                                "work:0:skill:0",
                                "work:0:skill:1"
                        )
                );

        CandidateProfile temporary =
                applier.apply(
                        original,
                        List.of(
                                suggestion
                        ),
                        new CvEvidenceService.EvidenceMap(
                                List.of()
                        )
                );

        /*
         * Mongo/domain object gốc không đổi.
         */
        assertThat(
                original
                        .getWorkExperiences()
                        .getFirst()
                        .responsibilities()
                        .getFirst()
        ).isEqualTo(
                "Developed backend services."
        );

        /*
         * Chỉ temporary profile đổi.
         */
        assertThat(
                temporary
                        .getWorkExperiences()
                        .getFirst()
                        .responsibilities()
                        .getFirst()
        ).isEqualTo(
                "Developed RESTful backend services "
                        + "using Java and Spring Boot."
        );
    }
}