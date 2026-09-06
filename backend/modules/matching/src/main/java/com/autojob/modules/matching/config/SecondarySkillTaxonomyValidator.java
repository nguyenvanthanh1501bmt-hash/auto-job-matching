package com.autojob.modules.matching.config;

import com.autojob.modules.jobnormalizer.config.SharedSkillTaxonomyProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Fail-fast validator cho secondary-skills.yml.
 *
 * Mục tiêu:
 * sau này chỉ cần thêm skill ID vào YAML, nhưng nếu gõ nhầm
 * hoặc dùng ID chưa tồn tại trong skills.yml thì app phải báo
 * lỗi ngay lúc startup thay vì silently scoring sai.
 */
@Component
public class SecondarySkillTaxonomyValidator {

    public SecondarySkillTaxonomyValidator(
            SharedSkillTaxonomyProperties skillTaxonomy,
            MatchingProperties matchingProperties
    ) {
        Objects.requireNonNull(
                skillTaxonomy,
                "skillTaxonomy must not be null"
        );

        Objects.requireNonNull(
                matchingProperties,
                "matchingProperties must not be null"
        );

        validate(
                skillTaxonomy.getItems(),
                matchingProperties
                        .getSkillScoring()
                        .getGenericSkillIds()
        );
    }

    static void validate(
            List<SharedSkillTaxonomyProperties.SkillDefinition>
                    definitions,
            Set<String> secondarySkillIds
    ) {
        Set<String> knownSkillIds =
                new LinkedHashSet<>();

        if (definitions != null) {
            for (SharedSkillTaxonomyProperties.SkillDefinition definition
                    : definitions) {

                if (definition == null) {
                    continue;
                }

                String id = normalizeId(
                        definition.getId()
                );

                if (!id.isBlank()) {
                    knownSkillIds.add(id);
                }
            }
        }

        Set<String> unknownIds =
                new LinkedHashSet<>();

        if (secondarySkillIds != null) {
            for (String rawId : secondarySkillIds) {

                String id = normalizeId(rawId);

                if (id.isBlank()) {
                    throw new IllegalStateException(
                            "secondary-skills.yml contains a blank skill id"
                    );
                }

                if (!knownSkillIds.contains(id)) {
                    unknownIds.add(id);
                }
            }
        }

        if (!unknownIds.isEmpty()) {
            throw new IllegalStateException(
                    "secondary-skills.yml contains unknown skill ids: "
                            + unknownIds
                            + ". Add them to "
                            + "configs/taxonomy/shared/skills.yml first, "
                            + "or fix the ids in secondary-skills.yml."
            );
        }
    }

    private static String normalizeId(
            String value
    ) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(Locale.ROOT);
    }
}