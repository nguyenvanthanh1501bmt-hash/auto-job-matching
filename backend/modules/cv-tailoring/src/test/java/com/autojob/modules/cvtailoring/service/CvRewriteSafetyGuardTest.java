package com.autojob.modules.cvtailoring.service;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CvRewriteSafetyGuardTest {

    private CvRewriteSafetyGuard guard;
    private CvEvidenceService.EvidenceMap evidenceMap;
    private NormalizedJob job;

    @BeforeEach
    void setUp() {
        CvEvidenceService evidenceService =
                mock(
                        CvEvidenceService.class
                );

        when(
                evidenceService
                        .canonicalSkillKey(
                                anyString()
                        )
        ).thenAnswer(
                invocation -> {

                    String value =
                            invocation.getArgument(
                                    0,
                                    String.class
                            );

                    return value
                            .trim()
                            .toLowerCase(
                                    Locale.ROOT
                            );
                }
        );

        guard =
                new CvRewriteSafetyGuard(
                        evidenceService
                );

        evidenceMap =
                new CvEvidenceService.EvidenceMap(
                        List.of(
                                new EvidenceItem(
                                        "work:0:responsibility:0",
                                        Section.WORK_EXPERIENCE,
                                        "work:0",
                                        EvidenceKind.TEXT,
                                        "Developed backend services.",
                                        null
                                ),

                                new EvidenceItem(
                                        "work:0:skill:0",
                                        Section.WORK_EXPERIENCE,
                                        "work:0",
                                        EvidenceKind.SKILL,
                                        "Java",
                                        "java"
                                )
                        )
                );

        job =
                NormalizedJob
                        .builder()
                        .id(
                                "job-1"
                        )
                        .skills(
                                List.of(
                                        "Java",
                                        "AWS"
                                )
                        )
                        .build();
    }

    @Test
    void allowsTruthfulRewriteUsingSupportedSkill() {

        SuggestionItem suggestion =
                suggestion(
                        "Developed backend services using Java.",
                        List.of(
                                "Java"
                        ),
                        List.of(
                                "work:0:responsibility:0",
                                "work:0:skill:0"
                        )
                );

        assertThat(
                guard.isSafe(
                        suggestion,
                        evidenceMap,
                        job
                )
        ).isTrue();
    }

    @Test
    void rejectsNewUnsupportedNumber() {

        SuggestionItem suggestion =
                suggestion(
                        "Developed 40 backend services using Java.",
                        List.of(
                                "Java"
                        ),
                        List.of(
                                "work:0:responsibility:0",
                                "work:0:skill:0"
                        )
                );

        assertThat(
                guard.isSafe(
                        suggestion,
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void rejectsUnsupportedLeadershipClaim() {

        SuggestionItem suggestion =
                suggestion(
                        "Led backend development using Java.",
                        List.of(
                                "Java"
                        ),
                        List.of(
                                "work:0:responsibility:0",
                                "work:0:skill:0"
                        )
                );

        assertThat(
                guard.isSafe(
                        suggestion,
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    @Test
    void rejectsJobSkillIntroducedWithoutBeingTrackedAsTargetSkill() {

        SuggestionItem suggestion =
                suggestion(
                        "Developed backend services using Java and AWS.",
                        List.of(
                                "Java"
                        ),
                        List.of(
                                "work:0:responsibility:0",
                                "work:0:skill:0"
                        )
                );

        assertThat(
                guard.isSafe(
                        suggestion,
                        evidenceMap,
                        job
                )
        ).isFalse();
    }

    private SuggestionItem suggestion(
            String suggested,
            List<String> targetSkills,
            List<String> evidenceIds
    ) {
        return new SuggestionItem(
                "rewrite-ai-0",
                SuggestionType.REWRITE,
                Section.WORK_EXPERIENCE,
                "work:0:responsibility:0",
                "Developed backend services.",
                suggested,
                "Makes existing evidence clearer.",
                targetSkills,
                evidenceIds
        );
    }
}