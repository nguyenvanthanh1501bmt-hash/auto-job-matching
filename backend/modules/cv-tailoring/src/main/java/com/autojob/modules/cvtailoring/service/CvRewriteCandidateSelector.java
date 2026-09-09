package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EditableNode;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EvidenceValue;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.JobContext;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteRequest;
import com.autojob.modules.cvtailoring.config.CvTailoringAiProperties;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.matching.domain.MatchResult;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class CvRewriteCandidateSelector {

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    private static final Pattern NON_WORD =
            Pattern.compile("[^a-z0-9]+");

    private final CvTailoringAiProperties properties;
    private final CvEvidenceService evidenceService;
    private final CvSourceIdResolver sourceIdResolver;

    public CvRewriteCandidateSelector(
            CvTailoringAiProperties properties,
            CvEvidenceService evidenceService,
            CvSourceIdResolver sourceIdResolver
    ) {
        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );

        this.evidenceService =
                Objects.requireNonNull(
                        evidenceService,
                        "evidenceService must not be null"
                );

        this.sourceIdResolver =
                Objects.requireNonNull(
                        sourceIdResolver,
                        "sourceIdResolver must not be null"
                );
    }

    public RewriteRequest select(
            CandidateProfile profile,
            NormalizedJob job,
            MatchResult targetMatch,
            CvEvidenceService.EvidenceMap evidenceMap
    ) {
        Objects.requireNonNull(
                profile,
                "profile must not be null"
        );

        Objects.requireNonNull(
                job,
                "job must not be null"
        );

        Objects.requireNonNull(
                targetMatch,
                "targetMatch must not be null"
        );

        Objects.requireNonNull(
                evidenceMap,
                "evidenceMap must not be null"
        );

        Set<String> relevantSkillKeys =
                relevantSkillKeys(
                        targetMatch,
                        job,
                        evidenceMap
                );

        Set<String> titleTerms =
                titleTerms(
                        job.getTitle()
                );

        List<ScoredNode> scored =
                new ArrayList<>();

        for (EvidenceItem item : evidenceMap.items()) {
            if (!isEditableText(item)) {
                continue;
            }

            CvSourceIdResolver.ResolvedSource source;

            try {
                source =
                        sourceIdResolver.resolve(
                                profile,
                                item.id()
                        );
            } catch (IllegalArgumentException exception) {
                continue;
            }

            List<EvidenceItem> allowedEvidence =
                    allowedEvidence(
                            source,
                            evidenceMap.items(),
                            relevantSkillKeys
                    );

            int score =
                    score(
                            source,
                            allowedEvidence,
                            relevantSkillKeys,
                            titleTerms
                    );

            if (score <= 0) {
                continue;
            }

            scored.add(
                    new ScoredNode(
                            score,
                            source,
                            allowedEvidence
                    )
            );
        }

        scored.sort(
                Comparator
                        .comparingInt(
                                ScoredNode::score
                        )
                        .reversed()
                        .thenComparing(
                                node ->
                                        node.source()
                                                .sourceId()
                        )
        );

        List<ScoredNode> selected =
                scored
                        .stream()
                        .limit(
                                properties
                                        .getMaxRewriteCandidates()
                        )
                        .toList();

        Map<String, EvidenceValue> catalog =
                new LinkedHashMap<>();

        List<EditableNode> editableNodes =
                new ArrayList<>();

        for (ScoredNode node : selected) {
            List<String> allowedEvidenceIds =
                    new ArrayList<>();

            for (EvidenceItem item : node.evidence()) {
                allowedEvidenceIds.add(
                        item.id()
                );

                catalog.putIfAbsent(
                        item.id(),
                        toEvidenceValue(
                                item
                        )
                );
            }

            editableNodes.add(
                    new EditableNode(
                            node.source().sourceId(),
                            node.source().section().name(),
                            localContext(
                                    profile,
                                    node.source()
                            ),
                            truncate(
                                    node.source().text(),
                                    properties
                                            .getMaxEvidenceTextChars()
                                            * 2
                            ),
                            List.copyOf(
                                    allowedEvidenceIds
                            )
                    )
            );
        }

        return new RewriteRequest(
                new JobContext(
                        safeText(
                                job.getTitle()
                        ),
                        safeList(
                                job.getSkills()
                        ),
                        truncate(
                                job.getRequirementsText(),
                                properties
                                        .getMaxJobRequirementsChars()
                        ),
                        truncate(
                                job.getDescriptionText(),
                                properties
                                        .getMaxJobDescriptionChars()
                        )
                ),
                List.copyOf(
                        editableNodes
                ),
                List.copyOf(
                        catalog.values()
                )
        );
    }

    private List<EvidenceItem> allowedEvidence(
            CvSourceIdResolver.ResolvedSource source,
            List<EvidenceItem> allEvidence,
            Set<String> relevantSkillKeys
    ) {
        List<EvidenceItem> candidates =
                new ArrayList<>();

        EvidenceItem sourceEvidence =
                allEvidence
                        .stream()
                        .filter(
                                item ->
                                        item != null
                                                && source
                                                .sourceId()
                                                .equals(
                                                        item.id()
                                                )
                        )
                        .findFirst()
                        .orElse(null);

        if (sourceEvidence != null) {
            candidates.add(
                    sourceEvidence
            );
        }

        for (EvidenceItem item : allEvidence) {
            if (item == null
                    || !isSkillEvidence(item)
                    || item.canonicalSkillKey() == null
                    || item.canonicalSkillKey().isBlank()
                    || !relevantSkillKeys.contains(
                    item.canonicalSkillKey()
            )) {
                continue;
            }

            boolean allowed =
                    source.section()
                            == Section.PROFESSIONAL_SUMMARY
                            || source.scopeId()
                            .equals(
                                    item.scopeId()
                            );

            if (allowed) {
                candidates.add(
                        item
                );
            }
        }

        return candidates
                .stream()
                .filter(
                        Objects::nonNull
                )
                .distinct()
                .limit(
                        properties
                                .getMaxEvidencePerCandidate()
                )
                .toList();
    }

    private int score(
            CvSourceIdResolver.ResolvedSource source,
            List<EvidenceItem> allowedEvidence,
            Set<String> relevantSkillKeys,
            Set<String> titleTerms
    ) {
        int score = 0;

        Set<String> matchedEvidenceSkills =
                new LinkedHashSet<>();

        for (EvidenceItem item : allowedEvidence) {
            if (isSkillEvidence(item)
                    && item.canonicalSkillKey() != null
                    && relevantSkillKeys.contains(
                    item.canonicalSkillKey()
            )) {
                matchedEvidenceSkills.add(
                        item.canonicalSkillKey()
                );
            }
        }

        score += Math.min(
                4,
                matchedEvidenceSkills.size()
        ) * 5;

        String sourceKey =
                compact(
                        source.text()
                );

        for (String skillKey : relevantSkillKeys) {
            if (!skillKey.isBlank()
                    && sourceKey.contains(
                    skillKey
            )) {
                score += 2;
            }
        }

        if (source.section()
                == Section.PROFESSIONAL_SUMMARY
                && !matchedEvidenceSkills.isEmpty()) {
            score += 3;
        }

        if (source.kind()
                == CvSourceIdResolver.SourceKind.ACHIEVEMENT) {
            score += 1;
        }

        if (score == 0
                && containsAnyTerm(
                sourceKey,
                titleTerms
        )) {
            score = 1;
        }

        return score;
    }

    private Set<String> relevantSkillKeys(
            MatchResult targetMatch,
            NormalizedJob job,
            CvEvidenceService.EvidenceMap evidenceMap
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        Set<String> missing =
                new LinkedHashSet<>();

        for (String skill : safeList(
                targetMatch.getMissingSkills()
        )) {
            String key =
                    evidenceService
                            .canonicalSkillKey(
                                    skill
                            );

            if (!key.isBlank()) {
                missing.add(
                        key
                );
            }
        }

        for (String skill : safeList(
                targetMatch.getMatchedSkills()
        )) {
            String key =
                    evidenceService
                            .canonicalSkillKey(
                                    skill
                            );

            if (!key.isBlank()
                    && !missing.contains(key)) {
                result.add(
                        key
                );
            }
        }

        String jobCorpus =
                compact(
                        safeText(job.getTitle())
                                + " "
                                + String.join(
                                " ",
                                safeList(job.getSkills())
                        )
                                + " "
                                + safeText(job.getRequirementsText())
                                + " "
                                + safeText(job.getDescriptionText())
                );

        for (EvidenceItem item : evidenceMap.items()) {
            if (item == null
                    || !isSkillEvidence(item)
                    || item.canonicalSkillKey() == null
                    || item.canonicalSkillKey().isBlank()
                    || missing.contains(
                    item.canonicalSkillKey()
            )) {
                continue;
            }

            String phrase =
                    compact(
                            item.text()
                    );

            if (!phrase.isBlank()
                    && containsPhrase(
                    jobCorpus,
                    phrase
            )) {
                result.add(
                        item.canonicalSkillKey()
                );
            }
        }

        return result;
    }

    private boolean containsPhrase(
            String compactText,
            String compactPhrase
    ) {
        if (compactText == null
                || compactText.isBlank()
                || compactPhrase == null
                || compactPhrase.isBlank()) {
            return false;
        }

        return (" " + compactText + " ")
                .contains(
                        " " + compactPhrase + " "
                );
    }

    private Set<String> titleTerms(
            String title
    ) {
        String compact =
                compact(
                        title
                );

        if (compact.isBlank()) {
            return Set.of();
        }

        Set<String> result =
                new LinkedHashSet<>();

        for (String token : compact.split(" ")) {
            if (token.length() >= 4) {
                result.add(
                        token
                );
            }
        }

        return result;
    }

    private boolean containsAnyTerm(
            String value,
            Set<String> terms
    ) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }

        return false;
    }

    private boolean isEditableText(
            EvidenceItem item
    ) {
        return item != null
                && item.kind()
                == EvidenceKind.TEXT
                && (
                item.section()
                        == Section.PROFESSIONAL_SUMMARY
                        || item.section()
                        == Section.WORK_EXPERIENCE
                        || item.section()
                        == Section.PROJECT
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

    private EvidenceValue toEvidenceValue(
            EvidenceItem item
    ) {
        return new EvidenceValue(
                item.id(),
                item.kind().name(),
                truncate(
                        item.text(),
                        properties
                                .getMaxEvidenceTextChars()
                ),
                safeText(
                        item.canonicalSkillKey()
                )
        );
    }

    private String localContext(
            CandidateProfile profile,
            CvSourceIdResolver.ResolvedSource source
    ) {
        if (source.section()
                == Section.PROFESSIONAL_SUMMARY) {
            return "Professional summary";
        }

        if (source.section()
                == Section.WORK_EXPERIENCE) {
            CandidateProfile.WorkExperience work =
                    profile
                            .getWorkExperiences()
                            .get(
                                    source.parentIndex()
                            );

            return truncate(
                    "Work role: "
                            + safeText(
                            firstText(
                                    work.jobTitle(),
                                    work.normalizedJobTitle()
                            )
                    ),
                    180
            );
        }

        CandidateProfile.ProjectExperience project =
                profile
                        .getProjects()
                        .get(
                                source.parentIndex()
                        );

        return truncate(
                "Project role: "
                        + safeText(
                        project.role()
                )
                        + "; domain: "
                        + safeText(
                        project.domain()
                ),
                180
        );
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

    private String truncate(
            String value,
            int maxChars
    ) {
        if (value == null
                || value.isBlank()) {
            return "";
        }

        String trimmed =
                value.trim();

        if (trimmed.length()
                <= maxChars) {
            return trimmed;
        }

        return trimmed.substring(
                0,
                maxChars
        );
    }

    private String compact(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return "";
        }

        String normalized =
                Normalizer
                        .normalize(
                                value,
                                Normalizer.Form.NFD
                        )
                        .toLowerCase(
                                Locale.ROOT
                        );

        normalized =
                DIACRITICS
                        .matcher(
                                normalized
                        )
                        .replaceAll("");

        normalized =
                NON_WORD
                        .matcher(
                                normalized
                        )
                        .replaceAll(" ")
                        .trim();

        return normalized.replaceAll(
                "\\s+",
                " "
        );
    }

    private String safeText(
            String value
    ) {
        return value == null
                ? ""
                : value;
    }

    private List<String> safeList(
            List<String> values
    ) {
        return values == null
                ? List.of()
                : values;
    }

    private record ScoredNode(
            int score,
            CvSourceIdResolver.ResolvedSource source,
            List<EvidenceItem> evidence
    ) {
    }
}