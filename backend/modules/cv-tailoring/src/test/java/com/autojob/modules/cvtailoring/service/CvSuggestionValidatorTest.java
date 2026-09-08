package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CvSuggestionValidatorTest {

    @Test
    void rejectsRewriteThatUsesSkillEvidenceFromAnotherWorkScope() {

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
                CandidateProfile
                        .builder()
                        .workExperiences(
                                List.of(
                                        work(
                                                "Developed backend services.",
                                                List.of(
                                                        "Spring Boot"
                                                )
                                        ),

                                        work(
                                                "Maintained internal systems.",
                                                List.of(
                                                        "Java"
                                                )
                                        )
                                )
                        )
                        .build();

        CvEvidenceService.EvidenceMap evidenceMap =
                new CvEvidenceService.EvidenceMap(
                        List.of(
                                new EvidenceItem(
                                        "work:1:skill:0",
                                        Section.WORK_EXPERIENCE,
                                        "work:1",
                                        EvidenceKind.SKILL,
                                        "Java",
                                        "java"
                                )
                        )
                );

        SuggestionItem suggestion =
                new SuggestionItem(
                        "rewrite-1",
                        SuggestionType.REWRITE,
                        Section.WORK_EXPERIENCE,
                        "work:0:responsibility:0",
                        "Developed backend services.",
                        "Developed Java backend services.",
                        "Make existing technology evidence clearer.",
                        List.of(
                                "Java"
                        ),
                        List.of(
                                "work:1:skill:0"
                        )
                );

        when(
                evidenceService
                        .canonicalSkillKey(
                                "Java"
                        )
        ).thenReturn(
                "java"
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

    private CandidateProfile.WorkExperience work(
            String responsibility,
            List<String> skills
    ) {
        return new CandidateProfile.WorkExperience(
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
                        responsibility
                ),
                List.of(),
                skills,
                List.of(),
                List.of()
        );
    }
}