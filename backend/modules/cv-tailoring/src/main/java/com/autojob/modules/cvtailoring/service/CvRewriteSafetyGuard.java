package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class CvRewriteSafetyGuard {

    private static final Pattern DIACRITICS =
            Pattern.compile("\\p{M}+");

    /*
     * Keep characters that can legitimately be part of
     * technology names:
     *
     * C++
     * C#
     * .NET
     * Node.js
     *
     * Sentence-ending dots are removed separately below.
     */
    private static final Pattern NON_WORD =
            Pattern.compile("[^a-z0-9%+.#]+");

    /*
     * Remove a period when it behaves like punctuation rather
     * than part of a technology token.
     *
     * Examples:
     *
     * AWS.       -> AWS
     * C++.       -> C++
     * C#.        -> C#
     *
     * But:
     *
     * Node.js    -> Node.js
     * .NET       -> .NET
     */
    private static final Pattern TRAILING_DOT =
            Pattern.compile("\\.(?=\\s|$)");

    private static final Pattern NUMBER =
            Pattern.compile(
                    "(?<![a-z0-9])\\d+(?:[.,]\\d+)?%?(?![a-z0-9])",
                    Pattern.CASE_INSENSITIVE
            );

    private static final List<String> HIGH_RISK_CLAIMS =
            List.of(
                    "architected",
                    "architecture",
                    "led",
                    "managed",
                    "mentored",
                    "owned",
                    "optimized",
                    "increased",
                    "reduced",
                    "improved",
                    "scaled",
                    "scalable",
                    "highly scalable",
                    "microservices",
                    "distributed systems",
                    "expert",
                    "expertise",
                    "advanced proficiency",
                    "proficient",
                    "senior",
                    "leadership"
            );

    private final CvEvidenceService evidenceService;

    public CvRewriteSafetyGuard(
            CvEvidenceService evidenceService
    ) {
        this.evidenceService =
                Objects.requireNonNull(
                        evidenceService,
                        "evidenceService must not be null"
                );
    }

    public boolean isSafe(
            SuggestionItem suggestion,
            CvEvidenceService.EvidenceMap evidenceMap,
            NormalizedJob job
    ) {
        if (suggestion == null
                || suggestion.suggested() == null
                || suggestion.suggested().isBlank()
                || suggestion.original() == null
                || suggestion.original().isBlank()) {
            return false;
        }

        String original =
                compact(
                        suggestion.original()
                );

        String suggested =
                compact(
                        suggestion.suggested()
                );

        if (suggested.isBlank()
                || suggested.equals(
                original
        )) {
            return false;
        }

        Map<String, EvidenceItem> evidenceById =
                indexEvidence(
                        evidenceMap.items()
                );

        List<EvidenceItem> citedEvidence =
                new ArrayList<>();

        for (String evidenceId : safeList(
                suggestion.evidenceIds()
        )) {
            EvidenceItem item =
                    evidenceById.get(
                            evidenceId
                    );

            if (item == null) {
                return false;
            }

            citedEvidence.add(
                    item
            );
        }

        String evidenceCorpus =
                evidenceCorpus(
                        suggestion.original(),
                        citedEvidence
                );

        if (introducesUnsupportedNumber(
                suggestion.suggested(),
                evidenceCorpus
        )) {
            return false;
        }

        if (introducesUnsupportedHighRiskClaim(
                suggested,
                compact(
                        evidenceCorpus
                )
        )) {
            return false;
        }

        return newlyExplicitSkillsAreTracked(
                suggestion,
                evidenceMap,
                job
        );
    }

    private boolean introducesUnsupportedNumber(
            String suggested,
            String evidenceCorpus
    ) {
        Set<String> supportedNumbers =
                numbers(
                        evidenceCorpus
                );

        for (String number : numbers(
                suggested
        )) {
            if (!supportedNumbers.contains(
                    number
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean introducesUnsupportedHighRiskClaim(
            String suggested,
            String evidenceCorpus
    ) {
        for (String claim : HIGH_RISK_CLAIMS) {

            String normalizedClaim =
                    compact(
                            claim
                    );

            if (containsPhrase(
                    suggested,
                    normalizedClaim
            )
                    && !containsPhrase(
                    evidenceCorpus,
                    normalizedClaim
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean newlyExplicitSkillsAreTracked(
            SuggestionItem suggestion,
            CvEvidenceService.EvidenceMap evidenceMap,
            NormalizedJob job
    ) {
        Set<String> targetSkillKeys =
                new LinkedHashSet<>();

        /*
         * targetSkills is not merely display metadata.
         *
         * It is the explicit declaration of which skills the
         * rewrite claims to surface.
         */
        for (String targetSkill : safeList(
                suggestion.targetSkills()
        )) {
            if (targetSkill == null
                    || targetSkill.isBlank()) {
                return false;
            }

            String key =
                    evidenceService
                            .canonicalSkillKey(
                                    targetSkill
                            );

            if (key == null
                    || key.isBlank()) {
                return false;
            }

            targetSkillKeys.add(
                    key
            );
        }

        /*
         * Skills that backend knows about and therefore must
         * not silently appear inside suggested wording.
         *
         * Sources:
         *
         * 1. Candidate evidence
         * 2. Target JD skills
         */
        Map<String, String> watchedSkills =
                new HashMap<>();

        if (evidenceMap != null
                && evidenceMap.items() != null) {

            for (EvidenceItem item :
                    evidenceMap.items()) {

                if (item != null
                        && isSkillEvidence(
                        item
                )
                        && item.canonicalSkillKey()
                        != null
                        && !item.canonicalSkillKey()
                        .isBlank()
                        && item.text() != null
                        && !item.text().isBlank()) {

                    String phrase =
                            compact(
                                    item.text()
                            );

                    if (!phrase.isBlank()) {
                        watchedSkills.putIfAbsent(
                                phrase,
                                item.canonicalSkillKey()
                        );
                    }
                }
            }
        }

        if (job != null) {
            for (String jobSkill : safeList(
                    job.getSkills()
            )) {
                if (jobSkill == null
                        || jobSkill.isBlank()) {
                    continue;
                }

                String key =
                        evidenceService
                                .canonicalSkillKey(
                                        jobSkill
                                );

                String phrase =
                        compact(
                                jobSkill
                        );

                if (key != null
                        && !key.isBlank()
                        && !phrase.isBlank()) {

                    watchedSkills.putIfAbsent(
                            phrase,
                            key
                    );
                }
            }
        }

        String original =
                compact(
                        suggestion.original()
                );

        String suggested =
                compact(
                        suggestion.suggested()
                );

        /*
         * Example:
         *
         * original:
         *   Developed backend services.
         *
         * suggested:
         *   Developed backend services using Java and AWS.
         *
         * targetSkills:
         *   Java
         *
         * AWS is newly introduced but not declared as a target
         * skill. Reject immediately.
         *
         * Later CvSuggestionValidator performs the stronger
         * evidence/scope validation for every declared target.
         */
        for (Map.Entry<String, String> watched :
                watchedSkills.entrySet()) {

            String phrase =
                    watched.getKey();

            String canonicalKey =
                    watched.getValue();

            boolean existedBefore =
                    containsPhrase(
                            original,
                            phrase
                    );

            boolean existsAfter =
                    containsPhrase(
                            suggested,
                            phrase
                    );

            if (!existedBefore
                    && existsAfter
                    && !targetSkillKeys.contains(
                    canonicalKey
            )) {

                return false;
            }
        }

        return true;
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

    private String evidenceCorpus(
            String original,
            List<EvidenceItem> evidence
    ) {
        StringBuilder builder =
                new StringBuilder(
                        original == null
                                ? ""
                                : original
                );

        for (EvidenceItem item : evidence) {
            if (item != null
                    && item.text() != null
                    && !item.text().isBlank()) {

                builder.append(
                        ' '
                ).append(
                        item.text()
                );
            }
        }

        return builder.toString();
    }

    private Map<String, EvidenceItem> indexEvidence(
            List<EvidenceItem> items
    ) {
        Map<String, EvidenceItem> result =
                new HashMap<>();

        if (items == null) {
            return result;
        }

        for (EvidenceItem item : items) {
            if (item != null
                    && item.id() != null
                    && !item.id().isBlank()) {

                result.put(
                        item.id(),
                        item
                );
            }
        }

        return result;
    }

    private Set<String> numbers(
            String value
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        if (value == null
                || value.isBlank()) {
            return result;
        }

        Matcher matcher =
                NUMBER.matcher(
                        value.toLowerCase(
                                Locale.ROOT
                        )
                );

        while (matcher.find()) {
            result.add(
                    matcher.group()
            );
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
                        " "
                                + compactPhrase
                                + " "
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
                        )
                        .replace(
                                'đ',
                                'd'
                        );

        normalized =
                DIACRITICS
                        .matcher(
                                normalized
                        )
                        .replaceAll(
                                ""
                        );

        normalized =
                NON_WORD
                        .matcher(
                                normalized
                        )
                        .replaceAll(
                                " "
                        );

        /*
         * After NON_WORD normalization:
         *
         * "AWS."     -> "aws."
         * "C++."     -> "c++."
         * "Node.js"  -> "node.js"
         * ".NET"     -> ".net"
         *
         * Remove only period-as-punctuation.
         */
        normalized =
                TRAILING_DOT
                        .matcher(
                                normalized
                        )
                        .replaceAll(
                                " "
                        );

        normalized =
                normalized.trim();

        return normalized.replaceAll(
                "\\s+",
                " "
        );
    }

    private List<String> safeList(
            List<String> values
    ) {
        return values == null
                ? List.of()
                : values;
    }
}