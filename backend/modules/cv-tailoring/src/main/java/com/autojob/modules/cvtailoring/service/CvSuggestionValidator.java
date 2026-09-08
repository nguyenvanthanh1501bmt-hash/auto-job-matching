package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class CvSuggestionValidator {

    private static final int
            MAX_SUGGESTED_TEXT_LENGTH =
            4000;

    private final CvEvidenceService
            cvEvidenceService;

    private final CvSourceIdResolver
            sourceIdResolver;

    public CvSuggestionValidator(
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

    public List<SuggestionItem> validateAll(
            CandidateProfile profile,
            CvEvidenceService.EvidenceMap evidenceMap,
            List<SuggestionItem> suggestions
    ) {
        Objects.requireNonNull(
                profile,
                "profile must not be null"
        );

        Objects.requireNonNull(
                evidenceMap,
                "evidenceMap must not be null"
        );

        if (suggestions == null
                || suggestions.isEmpty()) {

            return List.of();
        }

        Map<String, EvidenceItem> evidenceById =
                indexEvidence(
                        evidenceMap.items()
                );

        Set<String> suggestionIds =
                new HashSet<>();

        Set<String> rewriteSourceIds =
                new HashSet<>();

        List<SuggestionItem> validated =
                new ArrayList<>();

        for (SuggestionItem suggestion : suggestions) {

            if (suggestion == null) {
                throw new IllegalArgumentException(
                        "Suggestion must not be null"
                );
            }

            requireText(
                    suggestion.id(),
                    "suggestion.id"
            );

            if (!suggestionIds.add(
                    suggestion.id()
            )) {

                throw new IllegalArgumentException(
                        "Duplicate suggestion id: "
                                + suggestion.id()
                );
            }

            if (suggestion.type() == null) {

                throw new IllegalArgumentException(
                        "Suggestion type must not be null: "
                                + suggestion.id()
                );
            }

            if (suggestion.section() == null) {

                throw new IllegalArgumentException(
                        "Suggestion section must not be null: "
                                + suggestion.id()
                );
            }

            requireText(
                    suggestion.reason(),
                    "suggestion.reason"
            );

            switch (suggestion.type()) {

                case EMPHASIZE ->
                        validateEmphasize(
                                evidenceMap,
                                evidenceById,
                                suggestion
                        );

                case REWRITE -> {

                    validateRewrite(
                            profile,
                            evidenceById,
                            suggestion
                    );

                    if (!rewriteSourceIds.add(
                            suggestion.sourceId()
                    )) {

                        throw new IllegalArgumentException(
                                "Only one rewrite suggestion "
                                        + "is allowed per sourceId: "
                                        + suggestion.sourceId()
                        );
                    }
                }

                case GAP_WARNING ->
                        throw new IllegalArgumentException(
                                "GAP_WARNING is informational "
                                        + "and cannot be an accepted suggestion"
                        );
            }

            validated.add(
                    suggestion
            );
        }

        return List.copyOf(
                validated
        );
    }

    private void validateEmphasize(
            CvEvidenceService.EvidenceMap evidenceMap,
            Map<String, EvidenceItem> evidenceById,
            SuggestionItem suggestion
    ) {
        if (suggestion.section()
                != Section.SKILLS) {

            throw new IllegalArgumentException(
                    "EMPHASIZE must target SKILLS: "
                            + suggestion.id()
            );
        }

        if (!"skills".equals(
                suggestion.sourceId()
        )) {

            throw new IllegalArgumentException(
                    "EMPHASIZE sourceId must be skills: "
                            + suggestion.id()
            );
        }

        requireSuggestedText(
                suggestion
        );

        List<String> targetSkills =
                safeList(
                        suggestion.targetSkills()
                );

        if (targetSkills.size() != 1) {

            throw new IllegalArgumentException(
                    "EMPHASIZE must declare exactly "
                            + "one target skill: "
                            + suggestion.id()
            );
        }

        String targetKey =
                cvEvidenceService
                        .canonicalSkillKey(
                                targetSkills.getFirst()
                        );

        String suggestedKey =
                cvEvidenceService
                        .canonicalSkillKey(
                                suggestion.suggested()
                        );

        if (targetKey.isBlank()
                || !targetKey.equals(
                suggestedKey
        )) {

            throw new IllegalArgumentException(
                    "EMPHASIZE suggested skill must "
                            + "match targetSkills: "
                            + suggestion.id()
            );
        }

        if (evidenceMap
                .topLevelSkillKeys()
                .contains(
                        targetKey
                )) {

            throw new IllegalArgumentException(
                    "EMPHASIZE cannot add an "
                            + "already surfaced skill: "
                            + suggestion.id()
            );
        }

        List<EvidenceItem> evidence =
                resolveEvidence(
                        evidenceById,
                        suggestion.evidenceIds(),
                        suggestion.id()
                );

        if (evidence.isEmpty()) {

            throw new IllegalArgumentException(
                    "EMPHASIZE requires "
                            + "supporting evidence: "
                            + suggestion.id()
            );
        }

        for (EvidenceItem item : evidence) {

            if (!isProfessionalSkillEvidence(
                    item
            )) {

                throw new IllegalArgumentException(
                        "EMPHASIZE evidence must come "
                                + "from Work Experience or Projects: "
                                + suggestion.id()
                );
            }

            if (!targetKey.equals(
                    item.canonicalSkillKey()
            )) {

                throw new IllegalArgumentException(
                        "EMPHASIZE evidence does not "
                                + "support target skill: "
                                + suggestion.id()
                );
            }
        }
    }

    private void validateRewrite(
            CandidateProfile profile,
            Map<String, EvidenceItem> evidenceById,
            SuggestionItem suggestion
    ) {
        CvSourceIdResolver.ResolvedSource source =
                sourceIdResolver.resolve(
                        profile,
                        suggestion.sourceId()
                );

        if (source.section()
                != suggestion.section()) {

            throw new IllegalArgumentException(
                    "REWRITE section does not "
                            + "match sourceId: "
                            + suggestion.id()
            );
        }

        if (!Objects.equals(
                source.text(),
                suggestion.original()
        )) {

            throw new IllegalArgumentException(
                    "REWRITE original text does not "
                            + "match CandidateProfile: "
                            + suggestion.id()
            );
        }

        requireSuggestedText(
                suggestion
        );

        if (source.text().equals(
                suggestion.suggested()
        )) {

            throw new IllegalArgumentException(
                    "REWRITE suggested text must "
                            + "differ from original: "
                            + suggestion.id()
            );
        }

        List<EvidenceItem> evidence =
                resolveEvidence(
                        evidenceById,
                        suggestion.evidenceIds(),
                        suggestion.id()
                );

        if (evidence.isEmpty()) {

            throw new IllegalArgumentException(
                    "REWRITE requires evidence: "
                            + suggestion.id()
            );
        }

        if (source.section()
                == Section.WORK_EXPERIENCE
                || source.section()
                == Section.PROJECT) {

            for (EvidenceItem item : evidence) {

                if (!source
                        .scopeId()
                        .equals(
                                item.scopeId()
                        )) {

                    throw new IllegalArgumentException(
                            "REWRITE evidence is "
                                    + "outside source scope: "
                                    + suggestion.id()
                    );
                }
            }
        }

        List<String> targetSkills =
                safeList(
                        suggestion.targetSkills()
                );

        /*
         * Rewrite chỉ wording,
         * không claim thêm skill.
         *
         * Ít nhất phải cite chính source text.
         */
        if (targetSkills.isEmpty()) {

            boolean originalIncluded =
                    evidence
                            .stream()
                            .anyMatch(
                                    item ->
                                            suggestion
                                                    .sourceId()
                                                    .equals(
                                                            item.id()
                                                    )
                            );

            if (!originalIncluded) {

                throw new IllegalArgumentException(
                        "REWRITE without targetSkills "
                                + "must cite its original source "
                                + "as evidence: "
                                + suggestion.id()
                );
            }

            return;
        }

        Set<String> supportedSkillKeys =
                new LinkedHashSet<>();

        for (EvidenceItem item : evidence) {

            if (isSkillEvidence(
                    item
            )
                    && item.canonicalSkillKey()
                    != null
                    && !item
                    .canonicalSkillKey()
                    .isBlank()) {

                supportedSkillKeys.add(
                        item.canonicalSkillKey()
                );
            }
        }

        for (String targetSkill : targetSkills) {

            String targetKey =
                    cvEvidenceService
                            .canonicalSkillKey(
                                    targetSkill
                            );

            if (targetKey.isBlank()
                    || !supportedSkillKeys
                    .contains(
                            targetKey
                    )) {

                throw new IllegalArgumentException(
                        "REWRITE target skill has "
                                + "no supporting evidence: "
                                + targetSkill
                );
            }
        }
    }

    private Map<String, EvidenceItem>
    indexEvidence(
            List<EvidenceItem> items
    ) {
        Map<String, EvidenceItem> result =
                new HashMap<>();

        if (items == null) {
            return result;
        }

        for (EvidenceItem item : items) {

            if (item == null
                    || item.id() == null
                    || item.id().isBlank()) {

                continue;
            }

            EvidenceItem previous =
                    result.put(
                            item.id(),
                            item
                    );

            if (previous != null) {

                throw new IllegalArgumentException(
                        "Duplicate evidence id: "
                                + item.id()
                );
            }
        }

        return result;
    }

    private List<EvidenceItem> resolveEvidence(
            Map<String, EvidenceItem> evidenceById,
            List<String> evidenceIds,
            String suggestionId
    ) {
        if (evidenceIds == null
                || evidenceIds.isEmpty()) {

            return List.of();
        }

        Set<String> seen =
                new LinkedHashSet<>();

        List<EvidenceItem> result =
                new ArrayList<>();

        for (String evidenceId : evidenceIds) {

            requireText(
                    evidenceId,
                    "evidenceId"
            );

            if (!seen.add(
                    evidenceId
            )) {

                throw new IllegalArgumentException(
                        "Duplicate evidence id "
                                + "in suggestion: "
                                + suggestionId
                );
            }

            EvidenceItem item =
                    evidenceById.get(
                            evidenceId
                    );

            if (item == null) {

                throw new IllegalArgumentException(
                        "Unknown evidence id "
                                + "in suggestion "
                                + suggestionId
                                + ": "
                                + evidenceId
                );
            }

            result.add(
                    item
            );
        }

        return List.copyOf(
                result
        );
    }

    private boolean isProfessionalSkillEvidence(
            EvidenceItem item
    ) {
        return (
                item.section()
                        == Section.WORK_EXPERIENCE
                        || item.section()
                        == Section.PROJECT
        )
                && isSkillEvidence(
                item
        );
    }

    private boolean isSkillEvidence(
            EvidenceItem item
    ) {
        return item.kind()
                == EvidenceKind.SKILL
                || item.kind()
                == EvidenceKind.TOOL
                || item.kind()
                == EvidenceKind.EQUIPMENT;
    }

    private void requireSuggestedText(
            SuggestionItem suggestion
    ) {
        requireText(
                suggestion.suggested(),
                "suggestion.suggested"
        );

        if (suggestion
                .suggested()
                .length()
                > MAX_SUGGESTED_TEXT_LENGTH) {

            throw new IllegalArgumentException(
                    "Suggestion text is too long: "
                            + suggestion.id()
            );
        }
    }

    private List<String> safeList(
            List<String> values
    ) {
        return values == null
                ? List.of()
                : values;
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
}