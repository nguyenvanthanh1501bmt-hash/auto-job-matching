package com.autojob.modules.candidateembedding.text;

import com.autojob.modules.candidateembedding.config.CandidateEmbeddingProperties;
import com.autojob.modules.cv.domain.CandidateProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class CandidateEmbeddingTextBuilder {

    private static final String QUERY_PREFIX = "query: ";

    private final CandidateEmbeddingProperties properties;

    public String build(CandidateProfile profile) {
        if (profile == null) {
            return null;
        }

        List<String> sections = new ArrayList<>();

        addListSection(
                sections,
                "Target roles",
                normalizedSorted(profile.getTargetJobTitles())
        );

        addSection(
                sections,
                "Headline",
                clean(profile.getHeadline())
        );

        addListSection(
                sections,
                "Skills",
                normalizedSkills(profile)
        );

        addSection(
                sections,
                "Seniority",
                displayEnum(profile.getSeniority())
        );

        addSection(
                sections,
                "Experience",
                formatExperience(profile.getExperienceYears())
        );

        addListSection(
                sections,
                "Preferred locations",
                normalizedSorted(profile.getPreferredLocations())
        );

        addListSection(
                sections,
                "Recent titles",
                normalizedSorted(profile.getRecentJobTitles())
        );

        String summary = firstNonBlank(
                profile.getProfessionalSummary(),
                profile.getCareerObjective()
        );

        addSection(
                sections,
                "Professional summary",
                truncateUnicodeSafe(
                        clean(summary),
                        properties.getSummaryMaxChars()
                )
        );

        addSection(
                sections,
                "Work experience",
                buildWorkExperience(profile.getWorkExperiences())
        );

        addSection(
                sections,
                "Projects",
                buildProjects(profile.getProjects())
        );

        addSection(
                sections,
                "Education",
                buildEducation(profile.getEducations())
        );

        addSection(
                sections,
                "Certifications",
                buildCertifications(profile.getCertifications())
        );

        if (sections.isEmpty()) {
            return null;
        }

        String fullText = QUERY_PREFIX
                + String.join("\n", sections);

        return truncateUnicodeSafe(
                fullText,
                properties.getTextMaxChars()
        );
    }

    private List<String> normalizedSkills(
            CandidateProfile profile
    ) {
        if (profile.getSkills() == null) {
            return List.of();
        }

        List<String> values = profile.getSkills()
                .stream()
                .filter(skill -> skill != null)
                .map(skill -> firstNonBlank(
                        skill.normalizedName(),
                        skill.name()
                ))
                .toList();

        return normalizedSorted(values);
    }

    private String buildWorkExperience(
            List<CandidateProfile.WorkExperience> workExperiences
    ) {
        return buildLimitedItems(
                workExperiences,
                properties.getWorkExperienceMaxItems(),
                properties.getWorkExperienceItemMaxChars(),
                this::formatWorkExperience
        );
    }

    private String formatWorkExperience(
            CandidateProfile.WorkExperience experience
    ) {
        if (experience == null) {
            return null;
        }

        List<String> parts = new ArrayList<>();

        addValue(
                parts,
                firstNonBlank(
                        experience.normalizedJobTitle(),
                        experience.jobTitle()
                )
        );

        addLabeledValue(
                parts,
                "Industry",
                experience.companyIndustry()
        );

        addLabeledValue(
                parts,
                "Location",
                experience.location()
        );

        addLabeledValue(
                parts,
                "Mode",
                displayEnum(experience.workMode())
        );

        addLabeledValue(
                parts,
                "Description",
                experience.description()
        );

        addLabeledList(
                parts,
                "Responsibilities",
                experience.responsibilities()
        );

        addLabeledList(
                parts,
                "Achievements",
                experience.achievements()
        );

        addLabeledList(
                parts,
                "Skills",
                experience.skills()
        );

        addLabeledList(
                parts,
                "Tools",
                experience.tools()
        );

        addLabeledList(
                parts,
                "Equipment",
                experience.equipment()
        );

        return joinParts(parts);
    }

    private String buildProjects(
            List<CandidateProfile.ProjectExperience> projects
    ) {
        return buildLimitedItems(
                projects,
                properties.getProjectMaxItems(),
                properties.getProjectItemMaxChars(),
                this::formatProject
        );
    }

    private String formatProject(
            CandidateProfile.ProjectExperience project
    ) {
        if (project == null) {
            return null;
        }

        List<String> parts = new ArrayList<>();

        addValue(
                parts,
                project.name()
        );

        addLabeledValue(
                parts,
                "Role",
                project.role()
        );

        addLabeledValue(
                parts,
                "Domain",
                project.domain()
        );

        addLabeledValue(
                parts,
                "Description",
                project.description()
        );

        addLabeledList(
                parts,
                "Responsibilities",
                project.responsibilities()
        );

        addLabeledList(
                parts,
                "Achievements",
                project.achievements()
        );

        addLabeledList(
                parts,
                "Skills",
                project.skills()
        );

        addLabeledList(
                parts,
                "Tools",
                project.tools()
        );

        addLabeledList(
                parts,
                "Equipment",
                project.equipment()
        );

        return joinParts(parts);
    }

    private String buildEducation(
            List<CandidateProfile.Education> educations
    ) {
        if (educations == null || educations.isEmpty()) {
            return null;
        }

        List<String> items = new ArrayList<>();

        for (CandidateProfile.Education education : educations) {
            if (education == null) {
                continue;
            }

            List<String> parts = new ArrayList<>();

            addValue(
                    parts,
                    education.degree()
            );

            addLabeledValue(
                    parts,
                    "Level",
                    displayEnum(
                            education.normalizedDegreeLevel()
                    )
            );

            addLabeledValue(
                    parts,
                    "Field",
                    education.fieldOfStudy()
            );

            addLabeledValue(
                    parts,
                    "Specialization",
                    education.specialization()
            );

            addLabeledValue(
                    parts,
                    "Institution",
                    education.institutionName()
            );

            addLabeledList(
                    parts,
                    "Achievements",
                    education.achievements()
            );

            String item = joinParts(parts);

            if (item != null) {
                items.add(item);
            }
        }

        return items.isEmpty()
                ? null
                : String.join(" || ", items);
    }

    private String buildCertifications(
            List<CandidateProfile.Certification> certifications
    ) {
        if (certifications == null
                || certifications.isEmpty()
                || properties.getCertificationsMaxItems() == 0) {
            return null;
        }

        List<String> items = new ArrayList<>();

        for (CandidateProfile.Certification certification
                : certifications) {

            if (certification == null) {
                continue;
            }

            List<String> parts = new ArrayList<>();

            addValue(
                    parts,
                    certification.name()
            );

            addLabeledValue(
                    parts,
                    "Issuer",
                    certification.issuer()
            );

            addLabeledList(
                    parts,
                    "Skills",
                    certification.relatedSkills()
            );

            String item = joinParts(parts);

            if (item != null) {
                items.add(item);
            }

            if (items.size()
                    >= properties.getCertificationsMaxItems()) {
                break;
            }
        }

        return items.isEmpty()
                ? null
                : String.join(" || ", items);
    }

    private <T> String buildLimitedItems(
            List<T> source,
            int maxItems,
            int itemMaxChars,
            Function<T, String> formatter
    ) {
        if (source == null
                || source.isEmpty()
                || maxItems == 0) {
            return null;
        }

        List<String> items = new ArrayList<>();

        for (T value : source) {
            String formatted = clean(
                    formatter.apply(value)
            );

            if (formatted != null) {
                items.add(
                        truncateUnicodeSafe(
                                formatted,
                                itemMaxChars
                        )
                );
            }

            if (items.size() >= maxItems) {
                break;
            }
        }

        return items.isEmpty()
                ? null
                : String.join(" || ", items);
    }

    private void addSection(
            List<String> sections,
            String label,
            String value
    ) {
        String cleaned = clean(value);

        if (cleaned != null) {
            sections.add(
                    label + ": " + cleaned
            );
        }
    }

    private void addListSection(
            List<String> sections,
            String label,
            List<String> values
    ) {
        if (values != null && !values.isEmpty()) {
            sections.add(
                    label
                            + ": "
                            + String.join(", ", values)
            );
        }
    }

    private void addValue(
            List<String> parts,
            String value
    ) {
        String cleaned = clean(value);

        if (cleaned != null) {
            parts.add(cleaned);
        }
    }

    private void addLabeledValue(
            List<String> parts,
            String label,
            String value
    ) {
        String cleaned = clean(value);

        if (cleaned != null) {
            parts.add(
                    label + ": " + cleaned
            );
        }
    }

    private void addLabeledList(
            List<String> parts,
            String label,
            Collection<String> values
    ) {
        List<String> cleaned =
                normalizedPreservingOrder(values);

        if (!cleaned.isEmpty()) {
            parts.add(
                    label
                            + ": "
                            + String.join(", ", cleaned)
            );
        }
    }

    private String joinParts(
            List<String> parts
    ) {
        if (parts.isEmpty()) {
            return null;
        }

        return String.join(
                " | ",
                parts
        );
    }

    private List<String> normalizedSorted(
            Collection<String> values
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        Map<String, String> byKey =
                new TreeMap<>();

        for (String value : values) {
            String cleaned = clean(value);

            if (cleaned == null) {
                continue;
            }

            String key =
                    cleaned.toLowerCase(Locale.ROOT);

            byKey.merge(
                    key,
                    cleaned,
                    (left, right) ->
                            Comparator
                                    .<String>naturalOrder()
                                    .compare(left, right) <= 0
                                    ? left
                                    : right
            );
        }

        return List.copyOf(
                byKey.values()
        );
    }

    private List<String> normalizedPreservingOrder(
            Collection<String> values
    ) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        Map<String, String> byKey =
                new TreeMap<>();

        List<String> keys =
                new ArrayList<>();

        for (String value : values) {
            String cleaned = clean(value);

            if (cleaned == null) {
                continue;
            }

            String key =
                    cleaned.toLowerCase(Locale.ROOT);

            if (!byKey.containsKey(key)) {
                keys.add(key);
                byKey.put(
                        key,
                        cleaned
                );
            }
        }

        LinkedHashSet<String> result =
                new LinkedHashSet<>();

        for (String key : keys) {
            result.add(
                    byKey.get(key)
            );
        }

        return List.copyOf(result);
    }

    private String formatExperience(
            Double years
    ) {
        if (years == null
                || !Double.isFinite(years)
                || years < 0) {
            return null;
        }

        String number =
                BigDecimal.valueOf(years)
                        .stripTrailingZeros()
                        .toPlainString();

        return number + " years";
    }

    private String displayEnum(
            Enum<?> value
    ) {
        if (value == null
                || "UNKNOWN".equals(value.name())) {
            return null;
        }

        String[] words =
                value.name()
                        .toLowerCase(Locale.ROOT)
                        .split("_");

        List<String> displayed =
                new ArrayList<>();

        for (String word : words) {
            if (!word.isEmpty()) {
                displayed.add(
                        Character.toUpperCase(
                                word.charAt(0)
                        )
                                + word.substring(1)
                );
            }
        }

        return String.join(
                " ",
                displayed
        );
    }

    private String firstNonBlank(
            String first,
            String second
    ) {
        String normalizedFirst =
                clean(first);

        if (normalizedFirst != null) {
            return normalizedFirst;
        }

        return clean(second);
    }

    private String clean(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value
                        .replaceAll("\\s+", " ")
                        .trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private String truncateUnicodeSafe(
            String value,
            int maxChars
    ) {
        if (value == null
                || value.length() <= maxChars) {
            return value;
        }

        int end = maxChars;

        if (end > 0
                && end < value.length()
                && Character.isHighSurrogate(
                value.charAt(end - 1)
        )
                && Character.isLowSurrogate(
                value.charAt(end)
        )) {
            end--;
        }

        return value
                .substring(0, end)
                .stripTrailing();
    }
}