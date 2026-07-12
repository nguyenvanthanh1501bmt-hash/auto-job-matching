package com.autojob.modules.jobnormalizer.normalization;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SkillNormalizer {

    /**
     * Không tách bằng dấu "/" vì đây có thể là một phần của skill:
     *
     * UI/UX
     * Import/Export
     * B2B/B2C
     * CI/CD
     */
    private static final Pattern SKILL_SEPARATOR =
            Pattern.compile("[,;|\\n]+");

    /**
     * Đây là alias map, không phải whitelist.
     *
     * Skill có alias sẽ được chuẩn hóa về canonical name.
     * Skill không có alias vẫn được giữ nguyên sau khi cleanup.
     */
    private static final Map<String, String> CANONICAL_ALIASES =
            createCanonicalAliases();

    private final TextNormalizer textNormalizer;

    public List<String> normalize(List<String> rawSkills) {
        if (rawSkills == null || rawSkills.isEmpty()) {
            return List.of();
        }

        List<String> normalizedSkills = new ArrayList<>();
        Set<String> seenSkills = new LinkedHashSet<>();

        for (String rawSkillGroup : rawSkills) {
            if (rawSkillGroup == null) {
                continue;
            }

            String[] skillParts = SKILL_SEPARATOR.split(rawSkillGroup);

            for (String skillPart : skillParts) {
                String normalizedSkill = normalizeSingleSkill(skillPart);

                if (normalizedSkill == null) {
                    continue;
                }

                /*
                 * Deduplicate không phân biệt:
                 * - hoa thường
                 * - dấu tiếng Việt
                 * - khoảng trắng
                 *
                 * Ví dụ:
                 * "Kỹ năng giao tiếp"
                 * "ky nang giao tiep"
                 * được xem là cùng một giá trị.
                 */
                String deduplicationKey =
                        NormalizationTextSupport.fold(normalizedSkill);

                if (seenSkills.add(deduplicationKey)) {
                    normalizedSkills.add(normalizedSkill);
                }
            }
        }

        return List.copyOf(normalizedSkills);
    }

    private String normalizeSingleSkill(String rawSkill) {
        String cleaned = textNormalizer.normalizeInline(rawSkill);

        if (cleaned == null) {
            return null;
        }

        String aliasKey =
                NormalizationTextSupport.compactKey(cleaned);

        /*
         * Alias đã biết:
         * "springboot" -> "Spring Boot"
         *
         * Alias chưa biết:
         * "Vận hành máy CNC" -> giữ nguyên "Vận hành máy CNC"
         */
        return CANONICAL_ALIASES.getOrDefault(
                aliasKey,
                cleaned
        );
    }

    private static Map<String, String> createCanonicalAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();

        /*
         * Các alias phổ biến và ít nhập nhằng.
         *
         * Không cần khai báo toàn bộ kỹ năng của mọi ngành.
         * Kỹ năng chưa có trong map vẫn được giữ nguyên.
         */
        register(
                aliases,
                "Spring Boot",
                "spring boot",
                "springboot",
                "spring-boot"
        );

        register(
                aliases,
                "JavaScript",
                "javascript",
                "java script",
                "js"
        );

        register(
                aliases,
                "TypeScript",
                "typescript",
                "type script",
                "ts"
        );

        register(
                aliases,
                "Node.js",
                "nodejs",
                "node.js",
                "node js"
        );

        register(
                aliases,
                "React",
                "react",
                "reactjs",
                "react.js",
                "react js"
        );

        register(
                aliases,
                "MongoDB",
                "mongodb",
                "mongo db"
        );

        register(
                aliases,
                "PostgreSQL",
                "postgres",
                "postgresql",
                "postgre sql"
        );

        register(
                aliases,
                "Kubernetes",
                "k8s",
                "kubernetes"
        );

        register(
                aliases,
                "AWS",
                "aws",
                "amazon web services"
        );

        register(
                aliases,
                "Microsoft Excel",
                "excel",
                "ms excel",
                "microsoft excel"
        );

        register(
                aliases,
                "Microsoft Word",
                "word",
                "ms word",
                "microsoft word"
        );

        register(
                aliases,
                "Microsoft PowerPoint",
                "powerpoint",
                "power point",
                "ms powerpoint",
                "microsoft powerpoint"
        );

        register(
                aliases,
                "Adobe Photoshop",
                "photoshop",
                "adobe photoshop"
        );

        register(
                aliases,
                "Adobe Illustrator",
                "illustrator",
                "adobe illustrator"
        );

        register(
                aliases,
                "Google Ads",
                "google ads",
                "google adwords",
                "adwords"
        );

        register(
                aliases,
                "Facebook Ads",
                "facebook ads",
                "meta ads"
        );

        register(
                aliases,
                "Search Engine Optimization",
                "seo",
                "search engine optimization"
        );

        register(
                aliases,
                "Customer Relationship Management",
                "crm",
                "customer relationship management"
        );

        return Map.copyOf(aliases);
    }

    private static void register(
            Map<String, String> aliases,
            String canonical,
            String... values
    ) {
        for (String value : values) {
            aliases.put(
                    NormalizationTextSupport.compactKey(value),
                    canonical
            );
        }
    }
}