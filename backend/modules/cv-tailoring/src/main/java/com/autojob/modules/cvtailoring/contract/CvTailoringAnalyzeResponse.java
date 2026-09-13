package com.autojob.modules.cvtailoring.contract;

import java.time.Instant;
import java.util.List;

public record CvTailoringAnalyzeResponse(
        String analysisId,
        Instant expiresAt,
        String candidateProfileId,
        String normalizedJobId,
        JobSnapshot job,
        CurrentMatch currentMatch,
        List<EvidenceItem> evidence,
        List<SuggestionItem> suggestions,
        List<CoachingItem> coaching,
        List<GapItem> gaps
) {

    public CvTailoringAnalyzeResponse {
        evidence = copy(evidence);
        suggestions = copy(suggestions);
        coaching = copy(coaching);
        gaps = copy(gaps);
    }

    /**
     * Backward-compatible constructor kept for older tests and callers.
     */
    public CvTailoringAnalyzeResponse(
            String analysisId,
            Instant expiresAt,
            String candidateProfileId,
            String normalizedJobId,
            JobSnapshot job,
            CurrentMatch currentMatch,
            List<EvidenceItem> evidence,
            List<SuggestionItem> suggestions,
            List<GapItem> gaps
    ) {
        this(
                analysisId,
                expiresAt,
                candidateProfileId,
                normalizedJobId,
                job,
                currentMatch,
                evidence,
                suggestions,
                List.of(),
                gaps
        );
    }

    private static <T> List<T> copy(
            List<T> values
    ) {
        return values == null
                ? List.of()
                : List.copyOf(values);
    }

    public record JobSnapshot(
            String normalizedJobId,
            int rank,
            String title,
            String companyName,
            String locationText,
            String salaryText,
            String detailUrl,
            String applyUrl
    ) {
    }

    public record CurrentMatch(
            String rankingVersion,
            double finalScore,
            double semanticScore,
            double skillScore,
            double seniorityScore,
            double locationScore,
            double freshnessScore,
            boolean skillKnown,
            boolean seniorityKnown,
            boolean locationKnown,
            boolean freshnessKnown,
            List<String> matchedSkills,
            List<String> missingSkills
    ) {

        public CurrentMatch {
            matchedSkills = copy(matchedSkills);
            missingSkills = copy(missingSkills);
        }
    }

    public record EvidenceItem(
            String id,
            Section section,
            String scopeId,
            EvidenceKind kind,
            String text,
            String canonicalSkillKey
    ) {
    }

    public record SuggestionItem(
            String id,
            SuggestionType type,
            SuggestionCategory category,
            SuggestionPriority priority,
            Section section,
            String sourceId,
            String original,
            String suggested,
            String diagnosis,
            String reason,
            List<String> targetSkills,
            List<String> evidenceIds
    ) {

        public SuggestionItem {
            category = category == null
                    ? defaultCategory(type, section)
                    : category;

            priority = priority == null
                    ? SuggestionPriority.MEDIUM
                    : priority;

            diagnosis = hasText(diagnosis)
                    ? diagnosis.trim()
                    : reason;

            targetSkills = copy(targetSkills);
            evidenceIds = copy(evidenceIds);
        }

        /**
         * Backward-compatible constructor for the original suggestion contract.
         */
        public SuggestionItem(
                String id,
                SuggestionType type,
                Section section,
                String sourceId,
                String original,
                String suggested,
                String reason,
                List<String> targetSkills,
                List<String> evidenceIds
        ) {
            this(
                    id,
                    type,
                    defaultCategory(type, section),
                    SuggestionPriority.MEDIUM,
                    section,
                    sourceId,
                    original,
                    suggested,
                    reason,
                    reason,
                    targetSkills,
                    evidenceIds
            );
        }
    }

    public record CoachingItem(
            String id,
            CoachingType type,
            SuggestionPriority priority,
            Section section,
            String sourceId,
            String original,
            String question,
            String reason,
            List<String> targetSkills,
            List<String> evidenceIds
    ) {

        public CoachingItem {
            type = type == null
                    ? CoachingType.NEEDS_INPUT
                    : type;

            priority = priority == null
                    ? SuggestionPriority.MEDIUM
                    : priority;

            targetSkills = copy(targetSkills);
            evidenceIds = copy(evidenceIds);
        }
    }

    public record GapItem(
            String id,
            SuggestionType type,
            SuggestionPriority priority,
            String skill,
            String reason
    ) {

        public GapItem {
            priority = priority == null
                    ? SuggestionPriority.HIGH
                    : priority;
        }

        /**
         * Backward-compatible constructor for the original gap contract.
         */
        public GapItem(
                String id,
                SuggestionType type,
                String skill,
                String reason
        ) {
            this(
                    id,
                    type,
                    SuggestionPriority.HIGH,
                    skill,
                    reason
            );
        }
    }

    public enum SuggestionType {
        REWRITE,
        EMPHASIZE,
        GAP_WARNING
    }

    public enum SuggestionCategory {
        REWRITE,
        POSITION,
        SURFACE
    }

    public enum CoachingType {
        NEEDS_INPUT
    }

    public enum SuggestionPriority {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum Section {
        PROFESSIONAL_SUMMARY,
        SKILLS,
        WORK_EXPERIENCE,
        PROJECT,
        EDUCATION,
        CERTIFICATION,
        LICENSE,
        LANGUAGE
    }

    public enum EvidenceKind {
        TEXT,
        SKILL,
        TOOL,
        EQUIPMENT,
        EDUCATION,
        CERTIFICATION,
        LICENSE,
        LANGUAGE
    }

    private static SuggestionCategory defaultCategory(
            SuggestionType type,
            Section section
    ) {
        if (type == SuggestionType.EMPHASIZE) {
            return SuggestionCategory.SURFACE;
        }

        if (type == SuggestionType.REWRITE
                && section == Section.PROFESSIONAL_SUMMARY) {
            return SuggestionCategory.POSITION;
        }

        return SuggestionCategory.REWRITE;
    }

    private static boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }
}