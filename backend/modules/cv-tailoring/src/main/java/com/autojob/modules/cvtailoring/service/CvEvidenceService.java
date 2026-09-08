package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.jobnormalizer.normalization.SkillNormalizer;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class CvEvidenceService {

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_KEY =
            Pattern.compile("[^a-z0-9]+");

    private final SkillNormalizer skillNormalizer;

    public CvEvidenceService(
            SkillNormalizer skillNormalizer
    ) {
        this.skillNormalizer =
                Objects.requireNonNull(
                        skillNormalizer,
                        "skillNormalizer must not be null"
                );
    }

    public EvidenceMap build(
            CandidateProfile profile
    ) {
        Objects.requireNonNull(
                profile,
                "profile must not be null"
        );

        List<EvidenceItem> evidence =
                new ArrayList<>();

        addTextEvidence(
                evidence,
                "professionalSummary",
                Section.PROFESSIONAL_SUMMARY,
                "professionalSummary",
                profile.getProfessionalSummary()
        );

        addTopLevelSkills(
                evidence,
                profile.getSkills()
        );

        addWorkExperienceEvidence(
                evidence,
                profile.getWorkExperiences()
        );

        addProjectEvidence(
                evidence,
                profile.getProjects()
        );

        return new EvidenceMap(
                evidence
        );
    }

    public String canonicalSkillKey(
            String rawSkill
    ) {
        if (!hasText(rawSkill)) {
            return "";
        }

        List<String> normalized =
                skillNormalizer.normalize(
                        List.of(rawSkill)
                );

        String value =
                normalized != null
                        && !normalized.isEmpty()
                        && hasText(normalized.getFirst())
                        ? normalized.getFirst()
                        : rawSkill;

        return compact(
                value
        );
    }

    private void addTopLevelSkills(
            List<EvidenceItem> target,
            List<CandidateProfile.Skill> skills
    ) {
        if (skills == null) {
            return;
        }

        for (int index = 0;
             index < skills.size();
             index++) {

            CandidateProfile.Skill skill =
                    skills.get(index);

            if (skill == null) {
                continue;
            }

            String display =
                    firstText(
                            skill.name(),
                            skill.normalizedName()
                    );

            String canonicalSource =
                    firstText(
                            skill.normalizedName(),
                            skill.name()
                    );

            addSkillEvidence(
                    target,
                    "skill:" + index,
                    Section.SKILLS,
                    "skills",
                    EvidenceKind.SKILL,
                    display,
                    canonicalSource
            );
        }
    }

    private void addWorkExperienceEvidence(
            List<EvidenceItem> target,
            List<CandidateProfile.WorkExperience> experiences
    ) {
        if (experiences == null) {
            return;
        }

        for (int workIndex = 0;
             workIndex < experiences.size();
             workIndex++) {

            CandidateProfile.WorkExperience experience =
                    experiences.get(workIndex);

            if (experience == null) {
                continue;
            }

            String scopeId =
                    "work:" + workIndex;

            addTextEvidence(
                    target,
                    scopeId + ":description",
                    Section.WORK_EXPERIENCE,
                    scopeId,
                    experience.description()
            );

            addTextList(
                    target,
                    scopeId + ":responsibility:",
                    Section.WORK_EXPERIENCE,
                    scopeId,
                    experience.responsibilities()
            );

            addTextList(
                    target,
                    scopeId + ":achievement:",
                    Section.WORK_EXPERIENCE,
                    scopeId,
                    experience.achievements()
            );

            addSkillList(
                    target,
                    scopeId + ":skill:",
                    Section.WORK_EXPERIENCE,
                    scopeId,
                    EvidenceKind.SKILL,
                    experience.skills()
            );

            addSkillList(
                    target,
                    scopeId + ":tool:",
                    Section.WORK_EXPERIENCE,
                    scopeId,
                    EvidenceKind.TOOL,
                    experience.tools()
            );

            addSkillList(
                    target,
                    scopeId + ":equipment:",
                    Section.WORK_EXPERIENCE,
                    scopeId,
                    EvidenceKind.EQUIPMENT,
                    experience.equipment()
            );
        }
    }

    private void addProjectEvidence(
            List<EvidenceItem> target,
            List<CandidateProfile.ProjectExperience> projects
    ) {
        if (projects == null) {
            return;
        }

        for (int projectIndex = 0;
             projectIndex < projects.size();
             projectIndex++) {

            CandidateProfile.ProjectExperience project =
                    projects.get(projectIndex);

            if (project == null) {
                continue;
            }

            String scopeId =
                    "project:" + projectIndex;

            addTextEvidence(
                    target,
                    scopeId + ":description",
                    Section.PROJECT,
                    scopeId,
                    project.description()
            );

            addTextList(
                    target,
                    scopeId + ":responsibility:",
                    Section.PROJECT,
                    scopeId,
                    project.responsibilities()
            );

            addTextList(
                    target,
                    scopeId + ":achievement:",
                    Section.PROJECT,
                    scopeId,
                    project.achievements()
            );

            addSkillList(
                    target,
                    scopeId + ":skill:",
                    Section.PROJECT,
                    scopeId,
                    EvidenceKind.SKILL,
                    project.skills()
            );

            addSkillList(
                    target,
                    scopeId + ":tool:",
                    Section.PROJECT,
                    scopeId,
                    EvidenceKind.TOOL,
                    project.tools()
            );

            addSkillList(
                    target,
                    scopeId + ":equipment:",
                    Section.PROJECT,
                    scopeId,
                    EvidenceKind.EQUIPMENT,
                    project.equipment()
            );
        }
    }

    private void addTextList(
            List<EvidenceItem> target,
            String idPrefix,
            Section section,
            String scopeId,
            List<String> values
    ) {
        if (values == null) {
            return;
        }

        for (int index = 0;
             index < values.size();
             index++) {

            addTextEvidence(
                    target,
                    idPrefix + index,
                    section,
                    scopeId,
                    values.get(index)
            );
        }
    }

    private void addSkillList(
            List<EvidenceItem> target,
            String idPrefix,
            Section section,
            String scopeId,
            EvidenceKind kind,
            List<String> values
    ) {
        if (values == null) {
            return;
        }

        for (int index = 0;
             index < values.size();
             index++) {

            String value =
                    values.get(index);

            addSkillEvidence(
                    target,
                    idPrefix + index,
                    section,
                    scopeId,
                    kind,
                    value,
                    value
            );
        }
    }

    private void addTextEvidence(
            List<EvidenceItem> target,
            String id,
            Section section,
            String scopeId,
            String text
    ) {
        if (!hasText(text)) {
            return;
        }

        target.add(
                new EvidenceItem(
                        id,
                        section,
                        scopeId,
                        EvidenceKind.TEXT,
                        text.trim(),
                        null
                )
        );
    }

    private void addSkillEvidence(
            List<EvidenceItem> target,
            String id,
            Section section,
            String scopeId,
            EvidenceKind kind,
            String displayText,
            String canonicalSource
    ) {
        if (!hasText(displayText)
                && !hasText(canonicalSource)) {
            return;
        }

        String display =
                firstText(
                        displayText,
                        canonicalSource
                );

        String canonicalKey =
                canonicalSkillKey(
                        firstText(
                                canonicalSource,
                                displayText
                        )
                );

        if (canonicalKey.isBlank()) {
            return;
        }

        target.add(
                new EvidenceItem(
                        id,
                        section,
                        scopeId,
                        kind,
                        display.trim(),
                        canonicalKey
                )
        );
    }

    private String compact(
            String value
    ) {
        if (!hasText(value)) {
            return "";
        }

        String decomposed =
                Normalizer.normalize(
                        value,
                        Normalizer.Form.NFD
                );

        String folded =
                DIACRITICS
                        .matcher(decomposed)
                        .replaceAll("")
                        .replace('đ', 'd')
                        .replace('Đ', 'D')
                        .toLowerCase(Locale.ROOT)
                        .trim();

        return NON_KEY
                .matcher(folded)
                .replaceAll("");
    }

    private String firstText(
            String first,
            String second
    ) {
        if (hasText(first)) {
            return first;
        }

        return hasText(second)
                ? second
                : null;
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    public record EvidenceMap(
            List<EvidenceItem> items
    ) {

        public EvidenceMap {
            items = items == null
                    ? List.of()
                    : List.copyOf(items);
        }

        public Set<String> topLevelSkillKeys() {
            Set<String> result =
                    new LinkedHashSet<>();

            for (EvidenceItem item : items) {
                if (item == null
                        || item.section() != Section.SKILLS
                        || item.kind() != EvidenceKind.SKILL
                        || item.canonicalSkillKey() == null
                        || item.canonicalSkillKey().isBlank()) {
                    continue;
                }

                result.add(
                        item.canonicalSkillKey()
                );
            }

            return Set.copyOf(result);
        }

        public List<EvidenceItem>
        findSurfaceableProfessionalEvidence(
                String canonicalSkill
        ) {
            if (canonicalSkill == null
                    || canonicalSkill.isBlank()) {
                return List.of();
            }

            return items
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(
                            item ->
                                    item.section()
                                            == Section.WORK_EXPERIENCE
                                            || item.section()
                                            == Section.PROJECT
                    )
                    .filter(
                            item ->
                                    item.kind()
                                            == EvidenceKind.SKILL
                                            || item.kind()
                                            == EvidenceKind.TOOL
                                            || item.kind()
                                            == EvidenceKind.EQUIPMENT
                    )
                    .filter(
                            item ->
                                    canonicalSkill.equals(
                                            item.canonicalSkillKey()
                                    )
                    )
                    .toList();
        }
    }
}