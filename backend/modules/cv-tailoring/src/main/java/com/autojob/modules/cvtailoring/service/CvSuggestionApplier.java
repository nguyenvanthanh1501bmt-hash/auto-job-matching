package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class CvSuggestionApplier {

    private final CvEvidenceService
            cvEvidenceService;

    private final CvSourceIdResolver
            sourceIdResolver;

    public CvSuggestionApplier(
            CvEvidenceService cvEvidenceService,
            CvSourceIdResolver sourceIdResolver
    ) {
        this.cvEvidenceService =
                Objects.requireNonNull(
                        cvEvidenceService,
                        "cvEvidenceService must not be null"
                );

        this.sourceIdResolver =
                Objects.requireNonNull(
                        sourceIdResolver,
                        "sourceIdResolver must not be null"
                );
    }

    public CandidateProfile apply(
            CandidateProfile original,
            List<SuggestionItem> suggestions,
            CvEvidenceService.EvidenceMap evidenceMap
    ) {
        Objects.requireNonNull(
                original,
                "original must not be null"
        );

        Objects.requireNonNull(
                evidenceMap,
                "evidenceMap must not be null"
        );

        /*
         * QUAN TRỌNG:
         *
         * Không mutate CandidateProfile lấy từ Mongo.
         */
        CandidateProfile result =
                copyProfile(
                        original
                );

        if (suggestions == null
                || suggestions.isEmpty()) {

            return result;
        }

        for (SuggestionItem suggestion : suggestions) {

            if (suggestion == null) {

                throw new IllegalArgumentException(
                        "Suggestion must not be null"
                );
            }

            switch (suggestion.type()) {

                case EMPHASIZE ->
                        applyEmphasize(
                                result,
                                suggestion,
                                evidenceMap
                        );

                case REWRITE ->
                        applyRewrite(
                                result,
                                suggestion
                        );

                case GAP_WARNING ->
                        throw new IllegalArgumentException(
                                "GAP_WARNING cannot be applied"
                        );
            }
        }

        return result;
    }

    private void applyEmphasize(
            CandidateProfile profile,
            SuggestionItem suggestion,
            CvEvidenceService.EvidenceMap evidenceMap
    ) {
        if (suggestion.type()
                != SuggestionType.EMPHASIZE
                || suggestion.section()
                != Section.SKILLS
                || !"skills".equals(
                suggestion.sourceId()
        )) {

            throw new IllegalArgumentException(
                    "Invalid EMPHASIZE suggestion: "
                            + suggestion.id()
            );
        }

        String canonicalKey =
                cvEvidenceService
                        .canonicalSkillKey(
                                suggestion.suggested()
                        );

        /*
         * Defense-in-depth.
         *
         * Validator đã check nhưng Applier
         * vẫn không cho duplicate top-level skill.
         */
        for (
                CandidateProfile.Skill skill
                : safeList(
                profile.getSkills()
        )
        ) {

            if (skill == null) {
                continue;
            }

            String existingKey =
                    cvEvidenceService
                            .canonicalSkillKey(
                                    firstText(
                                            skill.normalizedName(),
                                            skill.name()
                                    )
                            );

            if (canonicalKey.equals(
                    existingKey
            )) {

                throw new IllegalArgumentException(
                        "Skill is already surfaced: "
                                + suggestion.suggested()
                );
            }
        }

        List<EvidenceItem> supportingEvidence =
                resolveEvidence(
                        evidenceMap.items(),
                        suggestion.evidenceIds()
                );

        /*
         * CỰC KỲ QUAN TRỌNG:
         *
         * Skill được surface từ Project
         * thì provenance vẫn là PROJECTS.
         *
         * Không được biến thành SKILLS_SECTION,
         * vì SkillScorer sẽ hiểu đó là evidence mạnh hơn.
         */
        List<String> evidenceSources =
                provenance(
                        supportingEvidence
                );

        CandidateProfile.SkillCategory category =
                inferCategory(
                        supportingEvidence
                );

        CandidateProfile.Skill newSkill =
                new CandidateProfile.Skill(
                        suggestion.suggested(),
                        suggestion.suggested(),
                        category,
                        null,
                        CandidateProfile
                                .ProficiencyLevel
                                .UNKNOWN,
                        null,
                        null,
                        evidenceSources
                );

        List<CandidateProfile.Skill> skills =
                new ArrayList<>(
                        safeList(
                                profile.getSkills()
                        )
                );

        skills.add(
                newSkill
        );

        profile.setSkills(
                skills
        );
    }

    private void applyRewrite(
            CandidateProfile profile,
            SuggestionItem suggestion
    ) {
        CvSourceIdResolver.ResolvedSource source =
                sourceIdResolver.resolve(
                        profile,
                        suggestion.sourceId()
                );

        String replacement =
                suggestion.suggested();

        if (source.kind()
                == CvSourceIdResolver
                .SourceKind
                .PROFESSIONAL_SUMMARY) {

            profile.setProfessionalSummary(
                    replacement
            );

            return;
        }

        if (source.section()
                == Section.WORK_EXPERIENCE) {

            applyWorkRewrite(
                    profile,
                    source,
                    replacement
            );

            return;
        }

        if (source.section()
                == Section.PROJECT) {

            applyProjectRewrite(
                    profile,
                    source,
                    replacement
            );

            return;
        }

        throw new IllegalArgumentException(
                "Unsupported REWRITE sourceId: "
                        + suggestion.sourceId()
        );
    }

    private void applyWorkRewrite(
            CandidateProfile profile,
            CvSourceIdResolver.ResolvedSource source,
            String replacement
    ) {
        List<CandidateProfile.WorkExperience> values =
                new ArrayList<>(
                        safeList(
                                profile.getWorkExperiences()
                        )
                );

        CandidateProfile.WorkExperience current =
                values.get(
                        source.parentIndex()
                );

        String description =
                current.description();

        List<String> responsibilities =
                mutable(
                        current.responsibilities()
                );

        List<String> achievements =
                mutable(
                        current.achievements()
                );

        switch (source.kind()) {

            case DESCRIPTION ->
                    description =
                            replacement;

            case RESPONSIBILITY ->
                    responsibilities.set(
                            source.itemIndex(),
                            replacement
                    );

            case ACHIEVEMENT ->
                    achievements.set(
                            source.itemIndex(),
                            replacement
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported work rewrite source: "
                                    + source.sourceId()
                    );
        }

        values.set(
                source.parentIndex(),

                new CandidateProfile.WorkExperience(
                        current.companyName(),
                        current.companyIndustry(),
                        current.jobTitle(),
                        current.normalizedJobTitle(),
                        current.employmentType(),
                        current.location(),
                        current.workMode(),
                        current.startDate(),
                        current.endDate(),
                        current.current(),
                        current.durationMonths(),
                        description,
                        responsibilities,
                        achievements,
                        mutable(
                                current.skills()
                        ),
                        mutable(
                                current.tools()
                        ),
                        mutable(
                                current.equipment()
                        )
                )
        );

        profile.setWorkExperiences(
                values
        );
    }

    private void applyProjectRewrite(
            CandidateProfile profile,
            CvSourceIdResolver.ResolvedSource source,
            String replacement
    ) {
        List<CandidateProfile.ProjectExperience> values =
                new ArrayList<>(
                        safeList(
                                profile.getProjects()
                        )
                );

        CandidateProfile.ProjectExperience current =
                values.get(
                        source.parentIndex()
                );

        String description =
                current.description();

        List<String> responsibilities =
                mutable(
                        current.responsibilities()
                );

        List<String> achievements =
                mutable(
                        current.achievements()
                );

        switch (source.kind()) {

            case DESCRIPTION ->
                    description =
                            replacement;

            case RESPONSIBILITY ->
                    responsibilities.set(
                            source.itemIndex(),
                            replacement
                    );

            case ACHIEVEMENT ->
                    achievements.set(
                            source.itemIndex(),
                            replacement
                    );

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported project rewrite source: "
                                    + source.sourceId()
                    );
        }

        values.set(
                source.parentIndex(),

                new CandidateProfile.ProjectExperience(
                        current.name(),
                        current.role(),
                        current.domain(),
                        current.startDate(),
                        current.endDate(),
                        current.current(),
                        description,
                        responsibilities,
                        achievements,
                        mutable(
                                current.skills()
                        ),
                        mutable(
                                current.tools()
                        ),
                        mutable(
                                current.equipment()
                        ),
                        current.teamSizeText(),
                        current.projectUrl(),
                        current.repositoryUrl()
                )
        );

        profile.setProjects(
                values
        );
    }

    private List<EvidenceItem> resolveEvidence(
            List<EvidenceItem> evidence,
            List<String> evidenceIds
    ) {
        List<EvidenceItem> result =
                new ArrayList<>();

        for (
                String evidenceId
                : safeList(
                evidenceIds
        )
        ) {

            EvidenceItem match =
                    safeList(
                            evidence
                    )
                            .stream()
                            .filter(
                                    Objects::nonNull
                            )
                            .filter(
                                    item ->
                                            evidenceId.equals(
                                                    item.id()
                                            )
                            )
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Unknown evidence id: "
                                                            + evidenceId
                                            )
                            );

            result.add(
                    match
            );
        }

        return result;
    }

    private List<String> provenance(
            List<EvidenceItem> evidence
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        for (EvidenceItem item : evidence) {

            if (item.section()
                    == Section.WORK_EXPERIENCE) {

                result.add(
                        "WORK_EXPERIENCE"
                );

            } else if (item.section()
                    == Section.PROJECT) {

                result.add(
                        "PROJECTS"
                );
            }
        }

        if (result.isEmpty()) {

            result.add(
                    "SCOPED_TEXT"
            );
        }

        return List.copyOf(
                result
        );
    }

    private CandidateProfile.SkillCategory inferCategory(
            List<EvidenceItem> evidence
    ) {
        boolean hasEquipment =
                evidence
                        .stream()
                        .anyMatch(
                                item ->
                                        item.kind()
                                                == EvidenceKind.EQUIPMENT
                        );

        if (hasEquipment) {
            return CandidateProfile
                    .SkillCategory
                    .EQUIPMENT;
        }

        boolean hasTool =
                evidence
                        .stream()
                        .anyMatch(
                                item ->
                                        item.kind()
                                                == EvidenceKind.TOOL
                        );

        if (hasTool) {
            return CandidateProfile
                    .SkillCategory
                    .TOOL;
        }

        /*
         * Work/Project structured skill không có
         * category chi tiết trong current model.
         *
         * Không tự đoán TECHNICAL/SOFTWARE/...
         */
        return CandidateProfile
                .SkillCategory
                .OTHER;
    }

    /*
     * Tạo object CandidateProfile mới.
     *
     * Các vùng chúng ta có thể mutate
     * (skills/work/projects) được copy riêng.
     *
     * Không save object này xuống CandidateProfileRepository.
     */
    private CandidateProfile copyProfile(
            CandidateProfile source
    ) {
        return CandidateProfile
                .builder()

                .id(
                        source.getId()
                )

                .rawCvId(
                        source.getRawCvId()
                )

                .ownerUserId(
                        source.getOwnerUserId()
                )

                .fullName(
                        source.getFullName()
                )

                .headline(
                        source.getHeadline()
                )

                .professionalSummary(
                        source.getProfessionalSummary()
                )

                .careerObjective(
                        source.getCareerObjective()
                )

                .contact(
                        source.getContact()
                )

                .links(
                        mutable(
                                source.getLinks()
                        )
                )

                .targetJobTitles(
                        mutable(
                                source.getTargetJobTitles()
                        )
                )

                .targetIndustries(
                        mutable(
                                source.getTargetIndustries()
                        )
                )

                .preferredLocations(
                        mutable(
                                source.getPreferredLocations()
                        )
                )

                .preferredWorkModes(
                        mutable(
                                source.getPreferredWorkModes()
                        )
                )

                .preferredEmploymentTypes(
                        mutable(
                                source.getPreferredEmploymentTypes()
                        )
                )

                .expectedSalaryText(
                        source.getExpectedSalaryText()
                )

                .availabilityText(
                        source.getAvailabilityText()
                )

                .skills(
                        copySkills(
                                source.getSkills()
                        )
                )

                .workExperiences(
                        copyWorkExperiences(
                                source.getWorkExperiences()
                        )
                )

                .projects(
                        copyProjects(
                                source.getProjects()
                        )
                )

                .educations(
                        mutable(
                                source.getEducations()
                        )
                )

                .certifications(
                        mutable(
                                source.getCertifications()
                        )
                )

                .licenses(
                        mutable(
                                source.getLicenses()
                        )
                )

                .languages(
                        mutable(
                                source.getLanguages()
                        )
                )

                .awards(
                        mutable(
                                source.getAwards()
                        )
                )

                .publications(
                        mutable(
                                source.getPublications()
                        )
                )

                .volunteerExperiences(
                        mutable(
                                source.getVolunteerExperiences()
                        )
                )

                .activities(
                        mutable(
                                source.getActivities()
                        )
                )

                .trainingCourses(
                        mutable(
                                source.getTrainingCourses()
                        )
                )

                .interests(
                        mutable(
                                source.getInterests()
                        )
                )

                .experienceYears(
                        source.getExperienceYears()
                )

                .seniority(
                        source.getSeniority()
                )

                .highestEducationLevel(
                        source.getHighestEducationLevel()
                )

                .recentJobTitles(
                        mutable(
                                source.getRecentJobTitles()
                        )
                )

                .recentCompanies(
                        mutable(
                                source.getRecentCompanies()
                        )
                )

                .detectedLanguage(
                        source.getDetectedLanguage()
                )

                .rawText(
                        source.getRawText()
                )

                .sections(
                        mutable(
                                source.getSections()
                        )
                )

                .parserVersion(
                        source.getParserVersion()
                )

                .parserWarnings(
                        mutable(
                                source.getParserWarnings()
                        )
                )

                .parseQuality(
                        source.getParseQuality()
                )

                .sourceBucket(
                        source.getSourceBucket()
                )

                .sourceObjectKey(
                        source.getSourceObjectKey()
                )

                .sourceOriginalFilename(
                        source.getSourceOriginalFilename()
                )

                .sourceContentType(
                        source.getSourceContentType()
                )

                .sourceSizeBytes(
                        source.getSourceSizeBytes()
                )

                .sourceSha256(
                        source.getSourceSha256()
                )

                .createdAt(
                        source.getCreatedAt()
                )

                .updatedAt(
                        source.getUpdatedAt()
                )

                .build();
    }

    private List<CandidateProfile.Skill> copySkills(
            List<CandidateProfile.Skill> values
    ) {
        List<CandidateProfile.Skill> result =
                new ArrayList<>();

        for (
                CandidateProfile.Skill value
                : safeList(
                values
        )
        ) {

            if (value == null) {
                result.add(
                        null
                );

                continue;
            }

            result.add(
                    new CandidateProfile.Skill(
                            value.name(),
                            value.normalizedName(),
                            value.category(),
                            value.proficiencyText(),
                            value.normalizedProficiency(),
                            value.yearsOfExperience(),
                            value.lastUsedDate(),
                            mutable(
                                    value.evidenceSources()
                            )
                    )
            );
        }

        return result;
    }

    private List<CandidateProfile.WorkExperience>
    copyWorkExperiences(
            List<CandidateProfile.WorkExperience> values
    ) {
        List<CandidateProfile.WorkExperience> result =
                new ArrayList<>();

        for (
                CandidateProfile.WorkExperience value
                : safeList(
                values
        )
        ) {

            if (value == null) {
                result.add(
                        null
                );

                continue;
            }

            result.add(
                    new CandidateProfile.WorkExperience(
                            value.companyName(),
                            value.companyIndustry(),
                            value.jobTitle(),
                            value.normalizedJobTitle(),
                            value.employmentType(),
                            value.location(),
                            value.workMode(),
                            value.startDate(),
                            value.endDate(),
                            value.current(),
                            value.durationMonths(),
                            value.description(),
                            mutable(
                                    value.responsibilities()
                            ),
                            mutable(
                                    value.achievements()
                            ),
                            mutable(
                                    value.skills()
                            ),
                            mutable(
                                    value.tools()
                            ),
                            mutable(
                                    value.equipment()
                            )
                    )
            );
        }

        return result;
    }

    private List<CandidateProfile.ProjectExperience>
    copyProjects(
            List<CandidateProfile.ProjectExperience> values
    ) {
        List<CandidateProfile.ProjectExperience> result =
                new ArrayList<>();

        for (
                CandidateProfile.ProjectExperience value
                : safeList(
                values
        )
        ) {

            if (value == null) {
                result.add(
                        null
                );

                continue;
            }

            result.add(
                    new CandidateProfile.ProjectExperience(
                            value.name(),
                            value.role(),
                            value.domain(),
                            value.startDate(),
                            value.endDate(),
                            value.current(),
                            value.description(),
                            mutable(
                                    value.responsibilities()
                            ),
                            mutable(
                                    value.achievements()
                            ),
                            mutable(
                                    value.skills()
                            ),
                            mutable(
                                    value.tools()
                            ),
                            mutable(
                                    value.equipment()
                            ),
                            value.teamSizeText(),
                            value.projectUrl(),
                            value.repositoryUrl()
                    )
            );
        }

        return result;
    }

    private <T> List<T> mutable(
            List<T> values
    ) {
        return values == null
                ? new ArrayList<>()
                : new ArrayList<>(
                values
        );
    }

    private <T> List<T> safeList(
            List<T> values
    ) {
        return values == null
                ? List.of()
                : values;
    }

    private String firstText(
            String first,
            String second
    ) {
        if (first != null
                && !first.isBlank()) {

            return first;
        }

        return second;
    }
}