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
        List<GapItem> gaps
) {

    public CvTailoringAnalyzeResponse {
        evidence = copy(evidence);
        suggestions = copy(suggestions);
        gaps = copy(gaps);
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
            Section section,
            String sourceId,
            String original,
            String suggested,
            String reason,
            List<String> targetSkills,
            List<String> evidenceIds
    ) {

        public SuggestionItem {
            targetSkills = copy(targetSkills);
            evidenceIds = copy(evidenceIds);
        }
    }

    public record GapItem(
            String id,
            SuggestionType type,
            String skill,
            String reason
    ) {
    }

    public enum SuggestionType {
        REWRITE,
        EMPHASIZE,
        GAP_WARNING
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
}