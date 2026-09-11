package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cvtailoring.config.CvRewriteSafetyTaxonomyProperties;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

    private static final Pattern NON_WORD =
            Pattern.compile("[^a-z0-9%+.#]+");

    private static final Pattern TRAILING_DOT =
            Pattern.compile("\\.(?=\\s|$)");

    private static final Pattern NUMBER =
            Pattern.compile(
                    "(?<![a-z0-9])\\d+(?:[.,]\\d+)?%?(?![a-z0-9])",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * Rewrite được phép rõ hơn và đầy đủ hơn một chút,
     * nhưng không được biến một bullet ngắn thành paragraph
     * chứa hàng loạt keyword chỉ để tác động embedding.
     */
    private static final double
            MAX_REWRITE_TOKEN_MULTIPLIER =
            2.0d;

    private static final int
            MAX_REWRITE_EXTRA_TOKENS =
            12;

    private static final double
            MAX_REWRITE_CHAR_MULTIPLIER =
            2.25d;

    private static final int
            MAX_REWRITE_EXTRA_CHARS =
            180;

    private static final Set<String>
            FREQUENCY_IGNORED_TOKENS =
            Set.of(
                    "a",
                    "an",
                    "and",
                    "as",
                    "at",
                    "by",
                    "for",
                    "from",
                    "in",
                    "into",
                    "of",
                    "on",
                    "or",
                    "the",
                    "to",
                    "with",
                    "across",
                    "using",
                    "through",
                    "via"
            );

    private static final Set<String>
            JOB_COPY_IGNORED_TOKENS =
            Set.of(
                    "a",
                    "an",
                    "and",
                    "as",
                    "at",
                    "by",
                    "for",
                    "from",
                    "in",
                    "into",
                    "of",
                    "on",
                    "or",
                    "the",
                    "to",
                    "with",
                    "across",
                    "using",
                    "through",
                    "via",
                    "role",
                    "job",
                    "position"
            );

    private final CvEvidenceService
            evidenceService;

    private final Map<String, List<String>>
            highRiskClaimGroups;

    private final Map<String, Set<EvidenceKind>>
            requiredEvidenceKindsByGroup;

    private final Map<String, Set<String>>
            genericTokensByGroup;

    private final Set<String>
            companionIdentityRequiredGroups;

    public CvRewriteSafetyGuard(
            CvEvidenceService evidenceService,
            CvRewriteSafetyTaxonomyProperties safetyTaxonomy
    ) {
        this.evidenceService =
                Objects.requireNonNull(
                        evidenceService,
                        "evidenceService must not be null"
                );

        Objects.requireNonNull(
                safetyTaxonomy,
                "safetyTaxonomy must not be null"
        );

        this.highRiskClaimGroups =
                normalizeClaimGroups(
                        safetyTaxonomy
                                .getHighRiskClaimGroups()
                );

        if (highRiskClaimGroups.isEmpty()) {

            throw new IllegalArgumentException(
                    "CV rewrite safety taxonomy "
                            + "must contain at least one "
                            + "high-risk claim group"
            );
        }

        this.requiredEvidenceKindsByGroup =
                normalizeEvidenceKindRequirements(
                        safetyTaxonomy
                );

        this.genericTokensByGroup =
                normalizeGenericTokens(
                        safetyTaxonomy
                );

        this.companionIdentityRequiredGroups =
                normalizeCompanionIdentityGroups(
                        safetyTaxonomy
                );
    }

    public boolean isSafe(
            SuggestionItem suggestion,
            CvEvidenceService.EvidenceMap evidenceMap,
            NormalizedJob job
    ) {
        if (suggestion == null
                || evidenceMap == null
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

        for (
                String evidenceId
                : safeList(
                suggestion.evidenceIds()
        )
        ) {

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
                compact(
                        evidenceCorpus(
                                suggestion.original(),
                                citedEvidence
                        )
                );

        /*
         * Model không được tạo metric,
         * %, năm, số lượng... mới.
         */
        if (introducesUnsupportedNumber(
                suggested,
                evidenceCorpus
        )) {

            return false;
        }

        /*
         * Các claim có rủi ro cao như:
         *
         * leadership
         * impact
         * seniority
         * certification
         * license
         * education
         * language proficiency
         *
         * phải có evidence phù hợp.
         */
        if (introducesUnsupportedHighRiskClaim(
                suggested,
                evidenceCorpus,
                citedEvidence
        )) {

            return false;
        }

        /*
         * Không cho một bullet ngắn biến thành
         * một đoạn dài bất thường.
         */
        if (isExcessivelyExpanded(
                original,
                suggested
        )) {

            return false;
        }

        /*
         * Skill có thật vẫn không được spam
         * để cố tình đẩy semantic similarity.
         */
        if (isKeywordStuffed(
                suggestion,
                suggested,
                evidenceCorpus
        )) {

            return false;
        }

        /*
         * JD là context, không phải candidate evidence.
         *
         * Nếu model lấy một term chỉ xuất hiện trong JD
         * rồi đưa nó vào CV trong khi evidence không có,
         * suggestion bị reject.
         *
         * Ví dụ:
         *
         * CV:
         *   Developed backend services using Java.
         *
         * JD:
         *   Financial services platform...
         *
         * AI:
         *   Developed financial services using Java.
         *
         * => reject.
         *
         * Những skill thật như Java/Spring Boot không bị
         * ảnh hưởng vì chúng đã xuất hiện trong cited
         * candidate evidence.
         */
        if (introducesJobOnlyTerms(
                suggested,
                evidenceCorpus,
                job
        )) {

            return false;
        }

        /*
         * Skill mới xuất hiện rõ trong rewrite phải:
         *
         * - được tracking trong targetSkills
         * - và sau đó CvSuggestionValidator sẽ kiểm tra
         *   candidate evidence thực sự support skill đó.
         */
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

        for (
                String number
                : numbers(
                suggested
        )
        ) {

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
            String evidenceCorpus,
            List<EvidenceItem> citedEvidence
    ) {
        for (
                Map.Entry<String, List<String>> entry
                : highRiskClaimGroups.entrySet()
        ) {

            String group =
                    entry.getKey();

            Set<EvidenceKind> requiredKinds =
                    requiredEvidenceKindsByGroup
                            .getOrDefault(
                                    group,
                                    Set.of()
                            );

            for (
                    String phrase
                    : entry.getValue()
            ) {

                if (!containsPhrase(
                        suggested,
                        phrase
                )) {

                    continue;
                }

                /*
                 * Một số claim có typed evidence.
                 *
                 * Ví dụ:
                 *
                 * "licensed" phải đến từ LICENSE,
                 * không được lấy từ một TEXT arbitrary.
                 */
                if (!requiredKinds.isEmpty()) {

                    if (!supportsTypedClaim(
                            group,
                            suggested,
                            citedEvidence,
                            requiredKinds
                    )) {

                        return true;
                    }

                    continue;
                }

                /*
                 * Với claim không có typed evidence riêng,
                 * wording đó phải đã được chứng minh trong
                 * source hoặc cited evidence.
                 */
                if (!containsPhrase(
                        evidenceCorpus,
                        phrase
                )) {

                    return true;
                }
            }
        }

        return false;
    }

    private boolean supportsTypedClaim(
            String group,
            String suggested,
            List<EvidenceItem> citedEvidence,
            Set<EvidenceKind> requiredKinds
    ) {
        List<EvidenceItem> typedEvidence =
                citedEvidence
                        .stream()
                        .filter(
                                Objects::nonNull
                        )
                        .filter(
                                item ->
                                        item.kind() != null
                                                && requiredKinds
                                                .contains(
                                                        item.kind()
                                                )
                        )
                        .toList();

        if (typedEvidence.isEmpty()) {
            return false;
        }

        Set<String> genericTokens =
                genericTokensByGroup
                        .getOrDefault(
                                group,
                                Set.of()
                        );

        if (companionIdentityRequiredGroups
                .contains(
                        group
                )) {

            return supportsCompanionIdentityClaim(
                    group,
                    suggested,
                    typedEvidence,
                    citedEvidence,
                    genericTokens
            );
        }

        for (
                EvidenceItem item
                : typedEvidence
        ) {

            String evidenceText =
                    compact(
                            item.text()
                    );

            if (evidenceText.isBlank()) {
                continue;
            }

            /*
             * Strongest case:
             *
             * Evidence:
             *   Basic Life Support
             *
             * Suggested:
             *   Basic Life Support certification
             */
            if (containsPhrase(
                    suggested,
                    evidenceText
            )) {

                return true;
            }

            Set<String> identityTokens =
                    identityTokens(
                            evidenceText,
                            genericTokens
                    );

            /*
             * Cho phép morphology nhỏ.
             *
             * Evidence:
             *   Registered Nurse License
             *
             * Suggested:
             *   licensed registered nurse
             */
            if (!identityTokens.isEmpty()
                    && containsAllTokens(
                    suggested,
                    identityTokens
            )) {

                return true;
            }
        }

        return false;
    }

    private boolean supportsCompanionIdentityClaim(
            String group,
            String suggested,
            List<EvidenceItem> typedEvidence,
            List<EvidenceItem> citedEvidence,
            Set<String> genericTokens
    ) {
        List<String> claimPhrases =
                highRiskClaimGroups
                        .getOrDefault(
                                group,
                                List.of()
                        );

        for (
                EvidenceItem claimEvidence
                : typedEvidence
        ) {

            String evidenceText =
                    compact(
                            claimEvidence.text()
                    );

            if (evidenceText.isBlank()
                    || !containsAnyClaimPhrase(
                    evidenceText,
                    claimPhrases
            )) {

                continue;
            }

            Set<String> ownIdentity =
                    identityTokens(
                            evidenceText,
                            genericTokens
                    );

            /*
             * Ví dụ evidence:
             *
             * Fluent English
             *
             * đã chứa cả property + identity.
             */
            if (!ownIdentity.isEmpty()
                    && containsPhrase(
                    suggested,
                    evidenceText
            )) {

                return true;
            }

            /*
             * Evidence:
             *
             * language:0:proficiency = Fluent
             *
             * thì suggestion ít nhất phải thật sự
             * sử dụng property "Fluent".
             */
            if (!containsPhrase(
                    suggested,
                    evidenceText
            )) {

                continue;
            }

            /*
             * Sau đó cần identity cùng scope:
             *
             * language:0:name = English
             */
            if (hasMatchingCompanionEvidence(
                    claimEvidence,
                    citedEvidence,
                    suggested,
                    genericTokens
            )) {

                return true;
            }
        }

        return false;
    }

    private boolean containsAnyClaimPhrase(
            String evidenceText,
            List<String> claimPhrases
    ) {
        for (
                String phrase
                : claimPhrases
        ) {

            if (containsPhrase(
                    evidenceText,
                    phrase
            )) {

                return true;
            }
        }

        return false;
    }

    private boolean hasMatchingCompanionEvidence(
            EvidenceItem source,
            List<EvidenceItem> citedEvidence,
            String suggested,
            Set<String> genericTokens
    ) {
        if (source.scopeId() == null
                || source.scopeId().isBlank()) {

            return false;
        }

        for (
                EvidenceItem companion
                : citedEvidence
        ) {

            if (companion == null
                    || companion == source
                    || !Objects.equals(
                    source.scopeId(),
                    companion.scopeId()
            )) {

                continue;
            }

            String companionText =
                    compact(
                            companion.text()
                    );

            Set<String> companionIdentity =
                    identityTokens(
                            companionText,
                            genericTokens
                    );

            if (!companionIdentity.isEmpty()
                    && containsAllTokens(
                    suggested,
                    companionIdentity
            )) {

                return true;
            }
        }

        return false;
    }

    private Set<String> identityTokens(
            String compactEvidence,
            Set<String> genericTokens
    ) {
        if (compactEvidence == null
                || compactEvidence.isBlank()) {

            return Set.of();
        }

        Set<String> result =
                new LinkedHashSet<>();

        for (
                String token
                : compactEvidence.split(
                " "
        )
        ) {

            if (token.isBlank()
                    || genericTokens.contains(
                    token
            )) {

                continue;
            }

            result.add(
                    token
            );
        }

        return Set.copyOf(
                result
        );
    }

    private boolean containsAllTokens(
            String compactText,
            Set<String> tokens
    ) {
        if (compactText == null
                || compactText.isBlank()
                || tokens == null
                || tokens.isEmpty()) {

            return false;
        }

        Set<String> textTokens =
                new LinkedHashSet<>(
                        List.of(
                                compactText.split(
                                        " "
                                )
                        )
                );

        return textTokens.containsAll(
                tokens
        );
    }

    private boolean isExcessivelyExpanded(
            String original,
            String suggested
    ) {
        int originalChars =
                original.length();

        int suggestedChars =
                suggested.length();

        int allowedChars =
                Math.max(
                        (int) Math.ceil(
                                originalChars
                                        * MAX_REWRITE_CHAR_MULTIPLIER
                        ),
                        originalChars
                                + MAX_REWRITE_EXTRA_CHARS
                );

        if (suggestedChars > allowedChars) {

            return true;
        }

        int originalTokens =
                tokens(
                        original
                ).size();

        int suggestedTokens =
                tokens(
                        suggested
                ).size();

        int allowedTokens =
                Math.max(
                        (int) Math.ceil(
                                originalTokens
                                        * MAX_REWRITE_TOKEN_MULTIPLIER
                        ),
                        originalTokens
                                + MAX_REWRITE_EXTRA_TOKENS
                );

        return suggestedTokens
                > allowedTokens;
    }

    private boolean isKeywordStuffed(
            SuggestionItem suggestion,
            String suggested,
            String evidenceCorpus
    ) {
        Map<String, Integer> suggestedCounts =
                tokenCounts(
                        suggested,
                        FREQUENCY_IGNORED_TOKENS
                );

        Map<String, Integer> evidenceCounts =
                tokenCounts(
                        evidenceCorpus,
                        FREQUENCY_IGNORED_TOKENS
                );

        /*
         * Generic repeated-token protection.
         *
         * Ví dụ:
         *
         * Java Java Java Java
         * backend backend backend backend
         */
        for (
                Map.Entry<String, Integer> entry
                : suggestedCounts.entrySet()
        ) {

            String token =
                    entry.getKey();

            int suggestedCount =
                    entry.getValue();

            /*
             * Short technical tokens như C / R
             * không dùng heuristic này.
             */
            if (token.length() < 3
                    || suggestedCount <= 3) {

                continue;
            }

            int evidenceCount =
                    evidenceCounts
                            .getOrDefault(
                                    token,
                                    0
                            );

            int allowed =
                    Math.max(
                            3,
                            evidenceCount + 2
                    );

            if (suggestedCount > allowed) {

                return true;
            }
        }

        /*
         * Skill phrase protection.
         *
         * Dù Java là skill thật,
         *
         * "Java Java Java Java"
         *
         * vẫn không phải một rewrite hợp lệ.
         */
        for (
                String targetSkill
                : safeList(
                suggestion.targetSkills()
        )
        ) {

            String phrase =
                    compact(
                            targetSkill
                    );

            if (phrase.isBlank()) {
                continue;
            }

            int suggestedCount =
                    countPhraseOccurrences(
                            suggested,
                            phrase
                    );

            int evidenceCount =
                    countPhraseOccurrences(
                            evidenceCorpus,
                            phrase
                    );

            int allowed =
                    Math.max(
                            2,
                            evidenceCount + 1
                    );

            if (suggestedCount > allowed) {

                return true;
            }
        }

        return false;
    }

    private boolean introducesJobOnlyTerms(
            String suggested,
            String evidenceCorpus,
            NormalizedJob job
    ) {
        if (job == null) {
            return false;
        }

        String jobCorpus =
                compact(
                        jobCorpus(
                                job
                        )
                );

        if (jobCorpus.isBlank()) {
            return false;
        }

        Set<String> evidenceTokens =
                new LinkedHashSet<>(
                        tokens(
                                evidenceCorpus
                        )
                );

        Set<String> jobTokens =
                new LinkedHashSet<>(
                        tokens(
                                jobCorpus
                        )
                );

        /*
         * Nếu một content token:
         *
         * 1. xuất hiện trong rewritten CV,
         * 2. chưa có trong candidate evidence,
         * 3. lại xuất hiện trong JD,
         *
         * thì có khả năng cao model đang copy JD
         * thay vì rewrite candidate truth.
         */
        for (
                String token
                : tokens(
                suggested
        )
        ) {

            if (token.isBlank()
                    || JOB_COPY_IGNORED_TOKENS
                    .contains(
                            token
                    )
                    || evidenceTokens
                    .contains(
                            token
                    )) {

                continue;
            }

            if (jobTokens.contains(
                    token
            )) {

                return true;
            }
        }

        return false;
    }

    private String jobCorpus(
            NormalizedJob job
    ) {
        StringBuilder builder =
                new StringBuilder();

        appendCorpus(
                builder,
                job.getTitle()
        );

        appendCorpus(
                builder,
                job.getCompanyName()
        );

        appendCorpus(
                builder,
                job.getLocationText()
        );

        appendCorpus(
                builder,
                job.getDescriptionText()
        );

        appendCorpus(
                builder,
                job.getRequirementsText()
        );

        for (
                String skill
                : safeList(
                job.getSkills()
        )
        ) {

            appendCorpus(
                    builder,
                    skill
            );
        }

        for (
                String location
                : safeList(
                job.getLocations()
        )
        ) {

            appendCorpus(
                    builder,
                    location
            );
        }

        return builder.toString();
    }

    private void appendCorpus(
            StringBuilder builder,
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            return;
        }

        if (builder.length() > 0) {

            builder.append(
                    ' '
            );
        }

        builder.append(
                value
        );
    }

    private boolean newlyExplicitSkillsAreTracked(
            SuggestionItem suggestion,
            CvEvidenceService.EvidenceMap evidenceMap,
            NormalizedJob job
    ) {
        Set<String> targetSkillKeys =
                new LinkedHashSet<>();

        for (
                String targetSkill
                : safeList(
                suggestion.targetSkills()
        )
        ) {

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

        Map<String, String> watchedSkills =
                new HashMap<>();

        /*
         * Watch mọi skill/tool/equipment
         * đã tồn tại trong CandidateProfile.
         */
        for (
                EvidenceItem item
                : safeList(
                evidenceMap.items()
        )
        ) {

            if (item == null
                    || !isSkillEvidence(
                    item
            )
                    || item.canonicalSkillKey() == null
                    || item.canonicalSkillKey().isBlank()
                    || item.text() == null
                    || item.text().isBlank()) {

                continue;
            }

            String phrase =
                    compact(
                            item.text()
                    );

            if (!phrase.isBlank()) {

                watchedSkills
                        .putIfAbsent(
                                phrase,
                                item.canonicalSkillKey()
                        );
            }
        }

        /*
         * Watch skill trong JD luôn.
         *
         * Nếu AWS chỉ có trong JD rồi AI tự đưa AWS
         * vào rewrite mà không declare/support target skill,
         * reject.
         */
        if (job != null) {

            for (
                    String jobSkill
                    : safeList(
                    job.getSkills()
            )
            ) {

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

                    watchedSkills
                            .putIfAbsent(
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

        for (
                Map.Entry<String, String> watched
                : watchedSkills.entrySet()
        ) {

            boolean existedBefore =
                    containsPhrase(
                            original,
                            watched.getKey()
                    );

            boolean existsAfter =
                    containsPhrase(
                            suggested,
                            watched.getKey()
                    );

            if (!existedBefore
                    && existsAfter
                    && !targetSkillKeys.contains(
                    watched.getValue()
            )) {

                return false;
            }
        }

        return true;
    }

    private Map<String, List<String>>
    normalizeClaimGroups(
            Map<String, List<String>> configured
    ) {
        if (configured == null) {

            return Map.of();
        }

        Map<String, List<String>> result =
                new LinkedHashMap<>();

        for (
                Map.Entry<String, List<String>> entry
                : configured.entrySet()
        ) {

            if (entry.getKey() == null
                    || entry.getKey().isBlank()) {

                continue;
            }

            Set<String> normalized =
                    new LinkedHashSet<>();

            for (
                    String phrase
                    : safeList(
                    entry.getValue()
            )
            ) {

                String value =
                        compact(
                                phrase
                        );

                if (!value.isBlank()) {

                    normalized.add(
                            value
                    );
                }
            }

            if (!normalized.isEmpty()) {

                result.put(
                        entry
                                .getKey()
                                .trim(),
                        List.copyOf(
                                normalized
                        )
                );
            }
        }

        return Map.copyOf(
                result
        );
    }

    private Map<String, Set<EvidenceKind>>
    normalizeEvidenceKindRequirements(
            CvRewriteSafetyTaxonomyProperties taxonomy
    ) {
        Map<String, Set<EvidenceKind>> result =
                new LinkedHashMap<>();

        for (
                String group
                : highRiskClaimGroups.keySet()
        ) {

            Set<EvidenceKind> kinds =
                    taxonomy
                            .getRequiredEvidenceKinds(
                                    group
                            );

            if (!kinds.isEmpty()) {

                result.put(
                        group,
                        Set.copyOf(
                                kinds
                        )
                );
            }
        }

        return Map.copyOf(
                result
        );
    }

    private Map<String, Set<String>>
    normalizeGenericTokens(
            CvRewriteSafetyTaxonomyProperties taxonomy
    ) {
        Map<String, Set<String>> result =
                new LinkedHashMap<>();

        for (
                String group
                : highRiskClaimGroups.keySet()
        ) {

            Set<String> configured =
                    taxonomy
                            .getGenericTokens(
                                    group
                            );

            if (configured.isEmpty()) {

                continue;
            }

            Set<String> normalized =
                    new LinkedHashSet<>();

            for (
                    String token
                    : configured
            ) {

                String value =
                        compact(
                                token
                        );

                if (value.isBlank()) {

                    continue;
                }

                for (
                        String part
                        : value.split(
                        " "
                )
                ) {

                    if (!part.isBlank()) {

                        normalized.add(
                                part
                        );
                    }
                }
            }

            if (!normalized.isEmpty()) {

                result.put(
                        group,
                        Set.copyOf(
                                normalized
                        )
                );
            }
        }

        return Map.copyOf(
                result
        );
    }

    private Set<String>
    normalizeCompanionIdentityGroups(
            CvRewriteSafetyTaxonomyProperties taxonomy
    ) {
        Set<String> result =
                new LinkedHashSet<>();

        for (
                String group
                : taxonomy
                .getCompanionIdentityRequiredGroups()
        ) {

            if (group == null
                    || group.isBlank()) {

                continue;
            }

            String normalized =
                    group.trim();

            if (highRiskClaimGroups
                    .containsKey(
                            normalized
                    )
                    && requiredEvidenceKindsByGroup
                    .containsKey(
                            normalized
                    )) {

                result.add(
                        normalized
                );
            }
        }

        return Set.copyOf(
                result
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

        for (
                EvidenceItem item
                : evidence
        ) {

            if (item != null
                    && item.text() != null
                    && !item.text().isBlank()) {

                builder.append(
                        ' '
                );

                builder.append(
                        item.text()
                );
            }
        }

        return builder.toString();
    }

    private Map<String, EvidenceItem>
    indexEvidence(
            List<EvidenceItem> items
    ) {
        Map<String, EvidenceItem> result =
                new HashMap<>();

        for (
                EvidenceItem item
                : safeList(
                items
        )
        ) {

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

    private Map<String, Integer> tokenCounts(
            String compactText,
            Set<String> ignoredTokens
    ) {
        Map<String, Integer> result =
                new HashMap<>();

        for (
                String token
                : tokens(
                compactText
        )
        ) {

            if (token.isBlank()
                    || ignoredTokens.contains(
                    token
            )) {

                continue;
            }

            result.merge(
                    token,
                    1,
                    Integer::sum
            );
        }

        return result;
    }

    private List<String> tokens(
            String compactText
    ) {
        if (compactText == null
                || compactText.isBlank()) {

            return List.of();
        }

        return List.of(
                compactText.split(
                        " "
                )
        );
    }

    private int countPhraseOccurrences(
            String compactText,
            String compactPhrase
    ) {
        if (compactText == null
                || compactText.isBlank()
                || compactPhrase == null
                || compactPhrase.isBlank()) {

            return 0;
        }

        String haystack =
                " "
                        + compactText
                        + " ";

        String needle =
                " "
                        + compactPhrase
                        + " ";

        int count = 0;
        int fromIndex = 0;

        while (true) {

            int index =
                    haystack.indexOf(
                            needle,
                            fromIndex
                    );

            if (index < 0) {

                return count;
            }

            count++;

            fromIndex =
                    index
                            + needle.length();
        }
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

        return (
                " "
                        + compactText
                        + " "
        ).contains(
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

        normalized =
                TRAILING_DOT
                        .matcher(
                                normalized
                        )
                        .replaceAll(
                                " "
                        );

        normalized =
                normalized
                        .trim();

        return normalized
                .replaceAll(
                        "\\s+",
                        " "
                );
    }

    private <T> List<T> safeList(
            List<T> values
    ) {
        return values == null
                ? List.of()
                : values;
    }
}