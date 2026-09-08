package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CvSourceIdResolver {

    private static final Pattern WORK_DESCRIPTION =
            Pattern.compile("^work:(\\d+):description$");

    private static final Pattern WORK_RESPONSIBILITY =
            Pattern.compile(
                    "^work:(\\d+):responsibility:(\\d+)$"
            );

    private static final Pattern WORK_ACHIEVEMENT =
            Pattern.compile(
                    "^work:(\\d+):achievement:(\\d+)$"
            );

    private static final Pattern PROJECT_DESCRIPTION =
            Pattern.compile(
                    "^project:(\\d+):description$"
            );

    private static final Pattern PROJECT_RESPONSIBILITY =
            Pattern.compile(
                    "^project:(\\d+):responsibility:(\\d+)$"
            );

    private static final Pattern PROJECT_ACHIEVEMENT =
            Pattern.compile(
                    "^project:(\\d+):achievement:(\\d+)$"
            );

    public ResolvedSource resolve(
            CandidateProfile profile,
            String sourceId
    ) {
        Objects.requireNonNull(
                profile,
                "profile must not be null"
        );

        requireText(
                sourceId,
                "sourceId"
        );

        if ("professionalSummary".equals(
                sourceId
        )) {

            return new ResolvedSource(
                    sourceId,
                    Section.PROFESSIONAL_SUMMARY,
                    "professionalSummary",
                    SourceKind.PROFESSIONAL_SUMMARY,
                    -1,
                    -1,
                    requireSourceText(
                            profile.getProfessionalSummary(),
                            sourceId
                    )
            );
        }

        Matcher matcher =
                WORK_DESCRIPTION.matcher(
                        sourceId
                );

        if (matcher.matches()) {

            int workIndex =
                    index(
                            matcher,
                            1,
                            sourceId
                    );

            CandidateProfile.WorkExperience work =
                    work(
                            profile,
                            workIndex,
                            sourceId
                    );

            return new ResolvedSource(
                    sourceId,
                    Section.WORK_EXPERIENCE,
                    "work:" + workIndex,
                    SourceKind.DESCRIPTION,
                    workIndex,
                    -1,
                    requireSourceText(
                            work.description(),
                            sourceId
                    )
            );
        }

        matcher =
                WORK_RESPONSIBILITY.matcher(
                        sourceId
                );

        if (matcher.matches()) {

            int workIndex =
                    index(
                            matcher,
                            1,
                            sourceId
                    );

            int itemIndex =
                    index(
                            matcher,
                            2,
                            sourceId
                    );

            CandidateProfile.WorkExperience work =
                    work(
                            profile,
                            workIndex,
                            sourceId
                    );

            return new ResolvedSource(
                    sourceId,
                    Section.WORK_EXPERIENCE,
                    "work:" + workIndex,
                    SourceKind.RESPONSIBILITY,
                    workIndex,
                    itemIndex,
                    listValue(
                            work.responsibilities(),
                            itemIndex,
                            sourceId
                    )
            );
        }

        matcher =
                WORK_ACHIEVEMENT.matcher(
                        sourceId
                );

        if (matcher.matches()) {

            int workIndex =
                    index(
                            matcher,
                            1,
                            sourceId
                    );

            int itemIndex =
                    index(
                            matcher,
                            2,
                            sourceId
                    );

            CandidateProfile.WorkExperience work =
                    work(
                            profile,
                            workIndex,
                            sourceId
                    );

            return new ResolvedSource(
                    sourceId,
                    Section.WORK_EXPERIENCE,
                    "work:" + workIndex,
                    SourceKind.ACHIEVEMENT,
                    workIndex,
                    itemIndex,
                    listValue(
                            work.achievements(),
                            itemIndex,
                            sourceId
                    )
            );
        }

        matcher =
                PROJECT_DESCRIPTION.matcher(
                        sourceId
                );

        if (matcher.matches()) {

            int projectIndex =
                    index(
                            matcher,
                            1,
                            sourceId
                    );

            CandidateProfile.ProjectExperience project =
                    project(
                            profile,
                            projectIndex,
                            sourceId
                    );

            return new ResolvedSource(
                    sourceId,
                    Section.PROJECT,
                    "project:" + projectIndex,
                    SourceKind.DESCRIPTION,
                    projectIndex,
                    -1,
                    requireSourceText(
                            project.description(),
                            sourceId
                    )
            );
        }

        matcher =
                PROJECT_RESPONSIBILITY.matcher(
                        sourceId
                );

        if (matcher.matches()) {

            int projectIndex =
                    index(
                            matcher,
                            1,
                            sourceId
                    );

            int itemIndex =
                    index(
                            matcher,
                            2,
                            sourceId
                    );

            CandidateProfile.ProjectExperience project =
                    project(
                            profile,
                            projectIndex,
                            sourceId
                    );

            return new ResolvedSource(
                    sourceId,
                    Section.PROJECT,
                    "project:" + projectIndex,
                    SourceKind.RESPONSIBILITY,
                    projectIndex,
                    itemIndex,
                    listValue(
                            project.responsibilities(),
                            itemIndex,
                            sourceId
                    )
            );
        }

        matcher =
                PROJECT_ACHIEVEMENT.matcher(
                        sourceId
                );

        if (matcher.matches()) {

            int projectIndex =
                    index(
                            matcher,
                            1,
                            sourceId
                    );

            int itemIndex =
                    index(
                            matcher,
                            2,
                            sourceId
                    );

            CandidateProfile.ProjectExperience project =
                    project(
                            profile,
                            projectIndex,
                            sourceId
                    );

            return new ResolvedSource(
                    sourceId,
                    Section.PROJECT,
                    "project:" + projectIndex,
                    SourceKind.ACHIEVEMENT,
                    projectIndex,
                    itemIndex,
                    listValue(
                            project.achievements(),
                            itemIndex,
                            sourceId
                    )
            );
        }

        throw new IllegalArgumentException(
                "Unsupported CV sourceId: "
                        + sourceId
        );
    }

    private CandidateProfile.WorkExperience work(
            CandidateProfile profile,
            int index,
            String sourceId
    ) {
        List<CandidateProfile.WorkExperience> values =
                profile.getWorkExperiences();

        if (values == null
                || index < 0
                || index >= values.size()
                || values.get(index) == null) {

            throw new IllegalArgumentException(
                    "Invalid work sourceId: "
                            + sourceId
            );
        }

        return values.get(index);
    }

    private CandidateProfile.ProjectExperience project(
            CandidateProfile profile,
            int index,
            String sourceId
    ) {
        List<CandidateProfile.ProjectExperience> values =
                profile.getProjects();

        if (values == null
                || index < 0
                || index >= values.size()
                || values.get(index) == null) {

            throw new IllegalArgumentException(
                    "Invalid project sourceId: "
                            + sourceId
            );
        }

        return values.get(index);
    }

    private String listValue(
            List<String> values,
            int index,
            String sourceId
    ) {
        if (values == null
                || index < 0
                || index >= values.size()) {

            throw new IllegalArgumentException(
                    "Invalid CV sourceId: "
                            + sourceId
            );
        }

        return requireSourceText(
                values.get(index),
                sourceId
        );
    }

    private int index(
            Matcher matcher,
            int group,
            String sourceId
    ) {
        try {
            return Integer.parseInt(
                    matcher.group(
                            group
                    )
            );
        } catch (NumberFormatException exception) {

            throw new IllegalArgumentException(
                    "Invalid CV sourceId index: "
                            + sourceId,
                    exception
            );
        }
    }

    private String requireSourceText(
            String value,
            String sourceId
    ) {
        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    "CV source is blank: "
                            + sourceId
            );
        }

        return value;
    }

    private void requireText(
            String value,
            String fieldName
    ) {
        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    fieldName
                            + " must not be blank"
            );
        }
    }

    public enum SourceKind {
        PROFESSIONAL_SUMMARY,
        DESCRIPTION,
        RESPONSIBILITY,
        ACHIEVEMENT
    }

    public record ResolvedSource(
            String sourceId,
            Section section,
            String scopeId,
            SourceKind kind,
            int parentIndex,
            int itemIndex,
            String text
    ) {
    }
}