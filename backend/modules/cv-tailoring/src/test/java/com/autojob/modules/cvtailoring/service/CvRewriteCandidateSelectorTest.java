package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EditableNode;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteRequest;
import com.autojob.modules.cvtailoring.config.CvTailoringAiProperties;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.domain.MatchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CvRewriteCandidateSelectorTest {

    private CvEvidenceService evidenceService;
    private CvRewriteCandidateSelector selector;

    @BeforeEach
    void setUp() {
        CvTailoringAiProperties properties =
                new CvTailoringAiProperties();

        properties.setMaxRewriteCandidates(
                6
        );

        properties.setMaxEvidencePerCandidate(
                10
        );

        evidenceService =
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

        selector =
                new CvRewriteCandidateSelector(
                        properties,
                        evidenceService,
                        new CvSourceIdResolver()
                );
    }

    @Test
    void selectsJobRelevantNodesAndKeepsEvidenceInsideSourceScope() {
        CandidateProfile profile =
                CandidateProfile
                        .builder()
                        .professionalSummary(
                                "Backend developer building business applications."
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
                        .projects(
                                List.of(
                                        new CandidateProfile.ProjectExperience(
                                                "Reporting Platform",
                                                "Backend Developer",
                                                "Business reporting",
                                                null,
                                                null,
                                                false,
                                                "Built reporting database features.",
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
                                        "professionalSummary",
                                        Section.PROFESSIONAL_SUMMARY,
                                        "professionalSummary",
                                        EvidenceKind.TEXT,
                                        "Backend developer building business applications.",
                                        null
                                ),
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
                                ),
                                new EvidenceItem(
                                        "work:0:skill:1",
                                        Section.WORK_EXPERIENCE,
                                        "work:0",
                                        EvidenceKind.SKILL,
                                        "Spring Boot",
                                        "spring boot"
                                ),
                                new EvidenceItem(
                                        "project:0:description",
                                        Section.PROJECT,
                                        "project:0",
                                        EvidenceKind.TEXT,
                                        "Built reporting database features.",
                                        null
                                ),
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

        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .id(
                                "job-1"
                        )
                        .title(
                                "Backend Developer"
                        )
                        .skills(
                                List.of(
                                        "Java",
                                        "Spring Boot",
                                        "PostgreSQL",
                                        "AWS"
                                )
                        )
                        .requirementsText(
                                "Strong Java, Spring Boot and PostgreSQL skills. "
                                        + "AWS is preferred."
                        )
                        .descriptionText(
                                "Develop backend services and APIs."
                        )
                        .build();

        MatchResult targetMatch =
                MatchResult
                        .builder()
                        .matchedSkills(
                                List.of(
                                        "Java",
                                        "Spring Boot",
                                        "PostgreSQL"
                                )
                        )
                        .missingSkills(
                                List.of(
                                        "AWS"
                                )
                        )
                        .build();

        RewriteRequest request =
                selector.select(
                        profile,
                        job,
                        targetMatch,
                        evidenceMap
                );

        assertThat(
                request.job().title()
        ).isEqualTo(
                "Backend Developer"
        );

        assertThat(
                request.editableNodes()
        ).isNotEmpty();

        EditableNode workNode =
                request
                        .editableNodes()
                        .stream()
                        .filter(
                                node ->
                                        "work:0:responsibility:0"
                                                .equals(
                                                        node.sourceId()
                                                )
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(
                workNode.allowedEvidenceIds()
        )
                .contains(
                        "work:0:responsibility:0",
                        "work:0:skill:0",
                        "work:0:skill:1"
                )
                .doesNotContain(
                        "project:0:skill:0"
                );

        EditableNode projectNode =
                request
                        .editableNodes()
                        .stream()
                        .filter(
                                node ->
                                        "project:0:description"
                                                .equals(
                                                        node.sourceId()
                                                )
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(
                projectNode.allowedEvidenceIds()
        )
                .contains(
                        "project:0:description",
                        "project:0:skill:0"
                )
                .doesNotContain(
                        "work:0:skill:0",
                        "work:0:skill:1"
                );

        assertThat(
                request.evidenceCatalog()
        )
                .extracting(
                        evidence ->
                                evidence.value()
                )
                .doesNotContain(
                        "AWS"
                );
    }

    @Test
    void professionalSummaryMayUseRelevantQualificationEvidenceButWorkMayNot() {
        CandidateProfile profile =
                CandidateProfile
                        .builder()
                        .professionalSummary(
                                "Clinical assistant supporting patient care."
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
                        .build();

        CvEvidenceService.EvidenceMap evidenceMap =
                new CvEvidenceService.EvidenceMap(
                        List.of(
                                new EvidenceItem(
                                        "professionalSummary",
                                        Section.PROFESSIONAL_SUMMARY,
                                        "professionalSummary",
                                        EvidenceKind.TEXT,
                                        "Clinical assistant supporting patient care.",
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
                                        "work:0:skill:0",
                                        Section.WORK_EXPERIENCE,
                                        "work:0",
                                        EvidenceKind.SKILL,
                                        "Patient Care",
                                        "patient care"
                                ),
                                new EvidenceItem(
                                        "license:0:name",
                                        Section.LICENSE,
                                        "license:0",
                                        EvidenceKind.LICENSE,
                                        "Registered Nurse License",
                                        null
                                ),
                                new EvidenceItem(
                                        "certification:0:name",
                                        Section.CERTIFICATION,
                                        "certification:0",
                                        EvidenceKind.CERTIFICATION,
                                        "Basic Life Support",
                                        null
                                ),
                                new EvidenceItem(
                                        "language:0:name",
                                        Section.LANGUAGE,
                                        "language:0",
                                        EvidenceKind.LANGUAGE,
                                        "English",
                                        null
                                ),
                                new EvidenceItem(
                                        "education:0:degree",
                                        Section.EDUCATION,
                                        "education:0",
                                        EvidenceKind.EDUCATION,
                                        "Bachelor of Nursing",
                                        null
                                )
                        )
                );

        NormalizedJob job =
                NormalizedJob
                        .builder()
                        .id(
                                "job-healthcare"
                        )
                        .title(
                                "Clinical Nurse"
                        )
                        .skills(
                                List.of(
                                        "Patient Care"
                                )
                        )
                        .requirementsText(
                                "Requires Registered Nurse License, "
                                        + "Basic Life Support, "
                                        + "English, and Bachelor of Nursing."
                        )
                        .descriptionText(
                                "Provide patient care."
                        )
                        .build();

        MatchResult targetMatch =
                MatchResult
                        .builder()
                        .matchedSkills(
                                List.of(
                                        "Patient Care"
                                )
                        )
                        .missingSkills(
                                List.of()
                        )
                        .build();

        RewriteRequest request =
                selector.select(
                        profile,
                        job,
                        targetMatch,
                        evidenceMap
                );

        EditableNode summaryNode =
                request
                        .editableNodes()
                        .stream()
                        .filter(
                                node ->
                                        "professionalSummary"
                                                .equals(
                                                        node.sourceId()
                                                )
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(
                summaryNode.allowedEvidenceIds()
        )
                .contains(
                        "professionalSummary",
                        "work:0:skill:0",
                        "license:0:name",
                        "certification:0:name",
                        "language:0:name",
                        "education:0:degree"
                );

        EditableNode workNode =
                request
                        .editableNodes()
                        .stream()
                        .filter(
                                node ->
                                        "work:0:responsibility:0"
                                                .equals(
                                                        node.sourceId()
                                                )
                        )
                        .findFirst()
                        .orElseThrow();

        assertThat(
                workNode.allowedEvidenceIds()
        )
                .contains(
                        "work:0:responsibility:0",
                        "work:0:skill:0"
                )
                .doesNotContain(
                        "license:0:name",
                        "certification:0:name",
                        "language:0:name",
                        "education:0:degree"
                );
    }
}