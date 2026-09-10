package com.autojob.modules.cvtailoring.config;

import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceKind;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Validated
@ConfigurationProperties(
        prefix = "autojob.taxonomy.shared.cv-rewrite-safety"
)
public class CvRewriteSafetyTaxonomyProperties {

    @NotEmpty
    private Map<String, List<String>> highRiskClaimGroups =
            new LinkedHashMap<>();

    private Map<String, List<EvidenceKind>>
            requiredEvidenceKindsByGroup =
            new LinkedHashMap<>();

    private Map<String, List<String>>
            genericTokensByGroup =
            new LinkedHashMap<>();

    private List<String>
            companionIdentityRequiredGroups =
            new ArrayList<>();

    public Map<String, List<String>>
    getHighRiskClaimGroups() {
        return highRiskClaimGroups;
    }

    public void setHighRiskClaimGroups(
            Map<String, List<String>> highRiskClaimGroups
    ) {
        this.highRiskClaimGroups =
                highRiskClaimGroups == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(
                        highRiskClaimGroups
                );
    }

    public Map<String, List<EvidenceKind>>
    getRequiredEvidenceKindsByGroup() {
        return requiredEvidenceKindsByGroup;
    }

    public void setRequiredEvidenceKindsByGroup(
            Map<String, List<EvidenceKind>>
                    requiredEvidenceKindsByGroup
    ) {
        this.requiredEvidenceKindsByGroup =
                requiredEvidenceKindsByGroup == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(
                        requiredEvidenceKindsByGroup
                );
    }

    public Map<String, List<String>>
    getGenericTokensByGroup() {
        return genericTokensByGroup;
    }

    public void setGenericTokensByGroup(
            Map<String, List<String>>
                    genericTokensByGroup
    ) {
        this.genericTokensByGroup =
                genericTokensByGroup == null
                        ? new LinkedHashMap<>()
                        : new LinkedHashMap<>(
                        genericTokensByGroup
                );
    }

    public List<String>
    getCompanionIdentityRequiredGroups() {
        return companionIdentityRequiredGroups;
    }

    public void setCompanionIdentityRequiredGroups(
            List<String> companionIdentityRequiredGroups
    ) {
        this.companionIdentityRequiredGroups =
                companionIdentityRequiredGroups == null
                        ? new ArrayList<>()
                        : new ArrayList<>(
                        companionIdentityRequiredGroups
                );
    }

    public Set<String> getHighRiskClaimPhrases() {
        Set<String> result =
                new LinkedHashSet<>();

        for (List<String> phrases :
                highRiskClaimGroups.values()) {

            if (phrases == null) {
                continue;
            }

            for (String phrase : phrases) {
                if (phrase == null
                        || phrase.isBlank()) {

                    continue;
                }

                result.add(
                        phrase.trim()
                );
            }
        }

        return Set.copyOf(
                result
        );
    }

    public Set<EvidenceKind>
    getRequiredEvidenceKinds(
            String group
    ) {
        if (group == null
                || group.isBlank()) {

            return Set.of();
        }

        List<EvidenceKind> configured =
                requiredEvidenceKindsByGroup.get(
                        group
                );

        if (configured == null
                || configured.isEmpty()) {

            return Set.of();
        }

        Set<EvidenceKind> result =
                new LinkedHashSet<>();

        for (EvidenceKind kind : configured) {
            if (kind != null) {
                result.add(
                        kind
                );
            }
        }

        return Set.copyOf(
                result
        );
    }

    public Set<String> getGenericTokens(
            String group
    ) {
        if (group == null
                || group.isBlank()) {

            return Set.of();
        }

        List<String> configured =
                genericTokensByGroup.get(
                        group
                );

        if (configured == null
                || configured.isEmpty()) {

            return Set.of();
        }

        Set<String> result =
                new LinkedHashSet<>();

        for (String token : configured) {
            if (token != null
                    && !token.isBlank()) {

                result.add(
                        token.trim()
                );
            }
        }

        return Set.copyOf(
                result
        );
    }

    public boolean requiresCompanionIdentity(
            String group
    ) {
        if (group == null
                || group.isBlank()) {

            return false;
        }

        return companionIdentityRequiredGroups
                .stream()
                .filter(
                        value ->
                                value != null
                                        && !value.isBlank()
                )
                .map(
                        String::trim
                )
                .anyMatch(
                        group::equals
                );
    }

    @AssertTrue(
            message =
                    "CV rewrite safety typed-evidence groups "
                            + "must reference existing claim groups"
    )
    public boolean isTypedEvidenceConfigurationValid() {
        return highRiskClaimGroups
                .keySet()
                .containsAll(
                        requiredEvidenceKindsByGroup
                                .keySet()
                );
    }

    @AssertTrue(
            message =
                    "CV rewrite safety generic-token groups "
                            + "must reference existing claim groups"
    )
    public boolean isGenericTokenConfigurationValid() {
        return highRiskClaimGroups
                .keySet()
                .containsAll(
                        genericTokensByGroup
                                .keySet()
                );
    }

    @AssertTrue(
            message =
                    "CV rewrite safety companion-identity groups "
                            + "must reference existing typed claim groups"
    )
    public boolean isCompanionIdentityConfigurationValid() {
        Set<String> companionGroups =
                new LinkedHashSet<>();

        for (String group :
                companionIdentityRequiredGroups) {

            if (group == null
                    || group.isBlank()) {

                continue;
            }

            companionGroups.add(
                    group.trim()
            );
        }

        return highRiskClaimGroups
                .keySet()
                .containsAll(
                        companionGroups
                )
                && requiredEvidenceKindsByGroup
                .keySet()
                .containsAll(
                        companionGroups
                );
    }
}