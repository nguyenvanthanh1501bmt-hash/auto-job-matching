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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Component
public class CvRewriteCandidateSelector {

    private static final Pattern NON_WORD =
            Pattern.compile(
                    "[^\\p{L}\\p{N}+#]+"
            );

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

        Set<String> relevantQualificationEvidenceIds =
                relevantQualificationEvidenceIds(
                        job,
                        evidenceMap
                );

        Set<String> titleTerms =
                titleTerms(
                        job.getTitle()
                );

        List<ScoredNode> scored =
                new ArrayList<>();

        for (EvidenceItem item :
                evidenceMap.items()) {

            if (!isEditableText(
                    item
            )) {
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
                            relevantSkillKeys,
                            relevantQualificationEvidenceIds
                    );

            int score =
                    score(
                            source,
                            allowedEvidence,
                            relevantSkillKeys,
                            relevantQualificationEvidenceIds,
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
                                        node
                                                .source()
                                                .sourceId()
                        )
        );

        List<ScoredNode> selected =
                selectWithCoverage(
                        scored,
                        properties
                                .getMaxRewriteCandidates()
                );

        Map<String, EvidenceValue> catalog =
                new LinkedHashMap<>();

        List<EditableNode> editableNodes =
                new ArrayList<>();

        for (ScoredNode node : selected) {

            List<String> allowedEvidenceIds =
                    new ArrayList<>();

            for (EvidenceItem item :
                    node.evidence()) {

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
                            node
                                    .source()
                                    .sourceId(),
                            node
                                    .source()
                                    .section()
                                    .name(),
                            localContext(
                                    profile,
                                    node.source()
                            ),
                            truncate(
                                    node
                                            .source()
                                            .text(),
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

    private List<ScoredNode> selectWithCoverage(
            List<ScoredNode> scored,
            int requestedLimit
    ) {
        int limit =
                Math.max(
                        1,
                        requestedLimit
                );

        List<ScoredNode> selected =
                new ArrayList<>();

        Set<String> selectedSourceIds =
                new LinkedHashSet<>();

        Map<String, Integer> scopeCounts =
                new LinkedHashMap<>();

        addBest(
                scored,
                selected,
                selectedSourceIds,
                scopeCounts,
                limit,
                node ->
                        node
                                .source()
                                .section()
                                == Section.PROFESSIONAL_SUMMARY
        );

        addBest(
                scored,
                selected,
                selectedSourceIds,
                scopeCounts,
                limit,
                node ->
                        node
                                .source()
                                .section()
                                == Section.WORK_EXPERIENCE
        );

        addBest(
                scored,
                selected,
                selectedSourceIds,
                scopeCounts,
                limit,
                node ->
                        node
                                .source()
                                .section()
                                == Section.PROJECT
        );

        addBest(
                scored,
                selected,
                selectedSourceIds,
                scopeCounts,
                limit,
                node ->
                        node
                                .source()
                                .kind()
                                == CvSourceIdResolver
                                .SourceKind
                                .ACHIEVEMENT
        );

        /*
         * First fill:
         * avoid one Work/Project scope consuming every LLM slot.
         */
        for (ScoredNode node : scored) {

            if (selected.size()
                    >= limit) {
                break;
            }

            if (selectedSourceIds.contains(
                    node
                            .source()
                            .sourceId()
            )) {
                continue;
            }

            int scopeCount =
                    scopeCounts
                            .getOrDefault(
                                    node
                                            .source()
                                            .scopeId(),
                                    0
                            );

            if (scopeCount >= 2) {
                continue;
            }

            addNode(
                    node,
                    selected,
                    selectedSourceIds,
                    scopeCounts
            );
        }

        /*
         * Small-profile fallback.
         *
         * If there are fewer scopes, use remaining available
         * high-scoring nodes rather than wasting request capacity.
         */
        for (ScoredNode node : scored) {

            if (selected.size()
                    >= limit) {
                break;
            }

            if (selectedSourceIds.contains(
                    node
                            .source()
                            .sourceId()
            )) {
                continue;
            }

            addNode(
                    node,
                    selected,
                    selectedSourceIds,
                    scopeCounts
            );
        }

        return List.copyOf(
                selected
        );
    }

    private void addBest(
            List<ScoredNode> scored,
            List<ScoredNode> selected,
            Set<String> selectedSourceIds,
            Map<String, Integer> scopeCounts,
            int limit,
            Predicate<ScoredNode> predicate
    ) {
        if (selected.size()
                >= limit) {
            return;
        }

        for (ScoredNode node : scored) {

            if (!predicate.test(
                    node
            )
                    || selectedSourceIds.contains(
                    node
                            .source()
                            .sourceId()
            )) {
                continue;
            }

            addNode(
                    node,
                    selected,
                    selectedSourceIds,
                    scopeCounts
            );

            return;
        }
    }

    private void addNode(
            ScoredNode node,
            List<ScoredNode> selected,
            Set<String> selectedSourceIds,
            Map<String, Integer> scopeCounts
    ) {
        selected.add(
                node
        );

        selectedSourceIds.add(
                node
                        .source()
                        .sourceId()
        );

        scopeCounts.merge(
                node
                        .source()
                        .scopeId(),
                1,
                Integer::sum
        );
    }

    private List<EvidenceItem> allowedEvidence(
            CvSourceIdResolver.ResolvedSource source,
            List<EvidenceItem> allEvidence,
            Set<String> relevantSkillKeys,
            Set<String> relevantQualificationEvidenceIds
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
                        .orElse(
                                null
                        );

        if (sourceEvidence != null) {
            candidates.add(
                    sourceEvidence
            );
        }

        /*
         * Summary may use profile-level supported skill evidence.
         *
         * Work/Project nodes may only use skill evidence from their
         * own local scope.
         */
        for (EvidenceItem item :
                allEvidence) {

            if (item == null
                    || !isSkillEvidence(
                    item
            )
                    || item.canonicalSkillKey()
                    == null
                    || item
                    .canonicalSkillKey()
                    .isBlank()
                    || !relevantSkillKeys.contains(
                    item.canonicalSkillKey()
            )) {
                continue;
            }

            boolean allowed =
                    source.section()
                            == Section.PROFESSIONAL_SUMMARY
                            || source
                            .scopeId()
                            .equals(
                                    item.scopeId()
                            );

            if (allowed) {
                candidates.add(
                        item
                );
            }
        }

        /*
         * Education / certification / licence / language evidence
         * may support the summary but is not allowed to leak into
         * individual Work/Project claims.
         */
        if (source.section()
                == Section.PROFESSIONAL_SUMMARY) {

            for (EvidenceItem item :
                    allEvidence) {

                if (item == null
                        || !isQualificationEvidence(
                        item
                )
                        || !relevantQualificationEvidenceIds
                        .contains(
                                item.id()
                        )) {
                    continue;
                }

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
            Set<String> relevantQualificationEvidenceIds,
            Set<String> titleTerms
    ) {
        int score = 0;

        Set<String> matchedEvidenceSkills =
                new LinkedHashSet<>();

        Set<String> matchedQualifications =
                new LinkedHashSet<>();

        for (EvidenceItem item :
                allowedEvidence) {

            if (isSkillEvidence(
                    item
            )
                    && item.canonicalSkillKey()
                    != null
                    && relevantSkillKeys.contains(
                    item.canonicalSkillKey()
            )) {

                matchedEvidenceSkills.add(
                        item.canonicalSkillKey()
                );
            }

            if (isQualificationEvidence(
                    item
            )
                    && relevantQualificationEvidenceIds
                    .contains(
                            item.id()
                    )) {

                matchedQualifications.add(
                        item.id()
                );
            }
        }

        score +=
                Math.min(
                        4,
                        matchedEvidenceSkills.size()
                ) * 5;

        score +=
                Math.min(
                        4,
                        matchedQualifications.size()
                ) * 4;

        String sourceKey =
                compact(
                        source.text()
                );

        String sourceCanonical =
                sourceKey.replace(
                        " ",
                        ""
                );

        boolean explicitRelevantSkillInText =
                false;

        for (String skillKey :
                relevantSkillKeys) {

            String comparableSkillKey =
                    compact(
                            skillKey
                    ).replace(
                            " ",
                            ""
                    );

            if (!comparableSkillKey.isBlank()
                    && sourceCanonical.contains(
                    comparableSkillKey
            )) {

                score += 2;

                explicitRelevantSkillInText =
                        true;
            }
        }

        boolean titleRelevant =
                containsAnyTerm(
                        sourceKey,
                        titleTerms
                );

        boolean hasJobSignal =
                !matchedEvidenceSkills.isEmpty()
                        || !matchedQualifications.isEmpty()
                        || explicitRelevantSkillInText
                        || titleRelevant;

        /*
         * Important:
         *
         * Do NOT require a taxonomy hit to make a node eligible.
         *
         * A CV tailoring engine must work for domains where our
         * structured skill taxonomy is weak or absent. The LLM sees
         * the real job context and real source node and may decide
         * whether a material rewrite is justified.
         *
         * Taxonomy/title signals therefore increase priority only.
         */
        if (hasJobSignal) {
            score += 4;
        }

        if (source.section()
                == Section.PROFESSIONAL_SUMMARY) {

            score += 7;
        }

        if (source.kind()
                == CvSourceIdResolver.SourceKind.ACHIEVEMENT) {

            score += 6;

        } else if (source.kind()
                == CvSourceIdResolver.SourceKind.RESPONSIBILITY) {

            score += 4;

        } else if (source.kind()
                == CvSourceIdResolver.SourceKind.DESCRIPTION) {

            score += 1;
        }

        String original =
                safeText(
                        source.text()
                ).trim();

        if (original.length() < 90) {

            score += 2;

        } else if (original.length() > 260) {

            score += 1;
        }

        if (titleRelevant) {
            score += 2;
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

        for (String skill :
                safeList(
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

        for (String skill :
                safeList(
                        targetMatch.getMatchedSkills()
                )) {

            String key =
                    evidenceService
                            .canonicalSkillKey(
                                    skill
                            );

            if (!key.isBlank()
                    && !missing.contains(
                    key
            )) {

                result.add(
                        key
                );
            }
        }

        String jobCorpus =
                jobCorpus(
                        job
                );

        for (EvidenceItem item :
                evidenceMap.items()) {

            if (item == null
                    || !isSkillEvidence(
                    item
            )
                    || item.canonicalSkillKey()
                    == null
                    || item
                    .canonicalSkillKey()
                    .isBlank()
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

    private Set<String> relevantQualificationEvidenceIds(
            NormalizedJob job,
            CvEvidenceService.EvidenceMap evidenceMap
    ) {
        String jobCorpus =
                jobCorpus(
                        job
                );

        if (jobCorpus.isBlank()) {
            return Set.of();
        }

        Set<String> result =
                new LinkedHashSet<>();

        for (EvidenceItem item :
                evidenceMap.items()) {

            if (item == null
                    || !isQualificationEvidence(
                    item
            )
                    || item.id() == null
                    || item
                    .id()
                    .isBlank()
                    || item.text() == null
                    || item
                    .text()
                    .isBlank()) {

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
                        item.id()
                );
            }
        }

        return Set.copyOf(
                result
        );
    }

    private String jobCorpus(
            NormalizedJob job
    ) {
        return compact(
                safeText(
                        job.getTitle()
                )
                        + " "
                        + String.join(
                        " ",
                        safeList(
                                job.getSkills()
                        )
                )
                        + " "
                        + safeText(
                        job.getRequirementsText()
                )
                        + " "
                        + safeText(
                        job.getDescriptionText()
                )
        );
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
                        " "
                                + compactPhrase
                                + " "
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

        for (String token :
                compact.split(
                        " "
                )) {

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
        for (String term :
                terms) {

            if (value.contains(
                    term
            )) {
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

    private boolean isQualificationEvidence(
            EvidenceItem item
    ) {
        return item.kind()
                == EvidenceKind.EDUCATION
                || item.kind()
                == EvidenceKind.CERTIFICATION
                || item.kind()
                == EvidenceKind.LICENSE
                || item.kind()
                == EvidenceKind.LANGUAGE;
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
                value.toLowerCase(
                        Locale.ROOT
                );

        normalized =
                NON_WORD
                        .matcher(
                                normalized
                        )
                        .replaceAll(
                                " "
                        )
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