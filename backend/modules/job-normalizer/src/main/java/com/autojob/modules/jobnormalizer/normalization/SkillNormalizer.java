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

    private static final int RICH_RAW_SKILL_COUNT = 2;

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

    private static final Set<String> AMBIGUOUS_PROSE_ALIASES = Set.of(
            "r",
            "c",
            "go",
            "ai",
            "hr",
            "js",
            "ts",
            "react",
            "word",
            "excel"
    );

    private static final Set<String> SAFE_SHORT_PROSE_ALIASES = Set.of(
            "5s",
            "c&b"
    );

    /**
     * Taxonomy này dùng cho hai mục đích:
     *
     * 1. canonicalize alias rõ ràng trong rawJob.skills;
     * 2. fallback extraction các known skill từ prose khi rawJob.skills rỗng
     *    hoặc chỉ có một skill.
     *
     * Đây KHÔNG phải whitelist. Skill không có trong taxonomy nhưng xuất hiện
     * trong rawJob.skills luôn được giữ nguyên sau cleanup.
     */
    private static final List<SkillDefinition> SKILL_TAXONOMY =
            createSkillTaxonomy();

    private static final Map<String, String> CANONICAL_ALIASES =
            createCanonicalAliases();

    private static final List<SkillMatcher> PROSE_MATCHERS =
            createProseMatchers();

    private final TextNormalizer textNormalizer;

    public List<String> normalize(List<String> rawSkills) {
        return normalizeRawSkills(rawSkills);
    }

    /**
     * General-purpose normalization cho service layer.
     *
     * rawJob.skills vẫn là nguồn chính. Chỉ khi nguồn này rỗng hoặc quá nghèo
     * dữ liệu (0-1 skill sau cleanup), known-skill extraction mới bổ sung thêm
     * signal từ title/requirements/description.
     */
    public List<String> normalize(
            List<String> rawSkills,
            String title,
            String requirementsText,
            String descriptionText
    ) {
        List<String> normalizedRawSkills = normalizeRawSkills(rawSkills);

        if (normalizedRawSkills.size() >= RICH_RAW_SKILL_COUNT) {
            return normalizedRawSkills;
        }

        List<String> extractedSkills = extractKnownSkills(
                title,
                requirementsText,
                descriptionText
        );

        return mergeSkills(
                normalizedRawSkills,
                extractedSkills
        );
    }

    private List<String> normalizeRawSkills(List<String> rawSkills) {
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

                String deduplicationKey = NormalizationTextSupport.fold(
                        normalizedSkill
                );

                if (seenSkills.add(deduplicationKey)) {
                    normalizedSkills.add(normalizedSkill);
                }
            }
        }

        return List.copyOf(normalizedSkills);
    }

    private List<String> extractKnownSkills(
            String title,
            String requirementsText,
            String descriptionText
    ) {
        StringBuilder prose = new StringBuilder();
        appendProse(prose, title);
        appendProse(prose, requirementsText);
        appendProse(prose, descriptionText);

        String foldedProse = NormalizationTextSupport.fold(prose.toString());

        if (foldedProse.isBlank()) {
            return List.of();
        }

        List<String> extracted = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (SkillMatcher matcher : PROSE_MATCHERS) {
            if (!matcher.pattern().matcher(foldedProse).find()) {
                continue;
            }

            String key = NormalizationTextSupport.fold(matcher.canonical());

            if (seen.add(key)) {
                extracted.add(matcher.canonical());
            }
        }

        return List.copyOf(extracted);
    }

    private void appendProse(StringBuilder prose, String value) {
        String normalized = textNormalizer.normalizeMultiline(value);

        if (normalized == null) {
            return;
        }

        if (!prose.isEmpty()) {
            prose.append('\n');
        }

        prose.append(normalized);
    }

    private List<String> mergeSkills(
            List<String> primary,
            List<String> supplemental
    ) {
        if (supplemental.isEmpty()) {
            return primary;
        }

        List<String> merged = new ArrayList<>(primary);
        Set<String> seen = new LinkedHashSet<>();

        for (String skill : primary) {
            seen.add(NormalizationTextSupport.fold(skill));
        }

        for (String skill : supplemental) {
            if (seen.add(NormalizationTextSupport.fold(skill))) {
                merged.add(skill);
            }
        }

        return List.copyOf(merged);
    }

    private String normalizeSingleSkill(String rawSkill) {
        String cleaned = textNormalizer.normalizeInline(rawSkill);

        if (cleaned == null) {
            return null;
        }

        String aliasKey = NormalizationTextSupport.compactKey(cleaned);

        return CANONICAL_ALIASES.getOrDefault(
                aliasKey,
                cleaned
        );
    }

    private static Map<String, String> createCanonicalAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();

        for (SkillDefinition definition : SKILL_TAXONOMY) {
            registerAlias(
                    aliases,
                    definition.canonical(),
                    definition.canonical()
            );

            for (String alias : definition.aliases()) {
                registerAlias(
                        aliases,
                        definition.canonical(),
                        alias
                );
            }
        }

        return Map.copyOf(aliases);
    }

    private static void registerAlias(
            Map<String, String> aliases,
            String canonical,
            String alias
    ) {
        aliases.put(
                NormalizationTextSupport.compactKey(alias),
                canonical
        );
    }

    private static List<SkillMatcher> createProseMatchers() {
        List<SkillMatcher> matchers = new ArrayList<>();
        Set<String> registeredPatterns = new LinkedHashSet<>();

        for (SkillDefinition definition : SKILL_TAXONOMY) {
            List<String> aliases = new ArrayList<>();
            aliases.add(definition.canonical());
            aliases.addAll(definition.aliases());

            for (String alias : aliases) {
                String foldedAlias = NormalizationTextSupport.fold(alias);

                if (!isSafeForProseExtraction(foldedAlias)) {
                    continue;
                }

                String matcherKey = definition.canonical()
                        + "\u0000"
                        + foldedAlias;

                if (!registeredPatterns.add(matcherKey)) {
                    continue;
                }

                Pattern pattern = Pattern.compile(
                        "(?<![a-z0-9])"
                                + Pattern.quote(foldedAlias)
                                + "(?![a-z0-9])"
                );

                matchers.add(new SkillMatcher(
                        definition.canonical(),
                        pattern
                ));
            }
        }

        return List.copyOf(matchers);
    }

    private static boolean isSafeForProseExtraction(String foldedAlias) {
        if (foldedAlias.isBlank()
                || AMBIGUOUS_PROSE_ALIASES.contains(foldedAlias)) {
            return false;
        }

        if (SAFE_SHORT_PROSE_ALIASES.contains(foldedAlias)) {
            return true;
        }

        String compact = foldedAlias.replaceAll("[^a-z0-9]+", "");

        return compact.length() >= 3;
    }

    private static List<SkillDefinition> createSkillTaxonomy() {
        List<SkillDefinition> definitions = new ArrayList<>();

        // Software / data / cloud
        add(definitions, "Java", "java");
        add(definitions, "Python", "python");
        add(
                definitions,
                "Spring Boot",
                "spring boot",
                "springboot",
                "spring-boot"
        );
        add(
                definitions,
                "JavaScript",
                "javascript",
                "java script",
                "js"
        );
        add(
                definitions,
                "TypeScript",
                "typescript",
                "type script",
                "ts"
        );
        add(
                definitions,
                "Node.js",
                "nodejs",
                "node.js",
                "node js"
        );
        add(
                definitions,
                "React",
                "react",
                "reactjs",
                "react.js",
                "react js"
        );
        add(definitions, "SQL", "sql");
        add(
                definitions,
                "PostgreSQL",
                "postgres",
                "postgresql",
                "postgre sql"
        );
        add(definitions, "MongoDB", "mongodb", "mongo db");
        add(definitions, "Docker", "docker");
        add(definitions, "Kubernetes", "k8s", "kubernetes");
        add(definitions, "AWS", "aws", "amazon web services");
        add(definitions, "Git", "git");
        add(definitions, "Go", "go", "golang");
        add(
                definitions,
                "Artificial Intelligence",
                "ai",
                "artificial intelligence"
        );

        // Accounting / finance
        add(definitions, "MISA", "misa");
        add(
                definitions,
                "Kế toán tổng hợp",
                "ke toan tong hop",
                "kế toán tổng hợp"
        );
        add(
                definitions,
                "Kế toán thuế",
                "ke toan thue",
                "kế toán thuế"
        );
        add(
                definitions,
                "Lập báo cáo tài chính",
                "lap bao cao tai chinh",
                "lập báo cáo tài chính"
        );
        add(
                definitions,
                "VAS",
                "vas",
                "vietnamese accounting standards"
        );
        add(
                definitions,
                "IFRS",
                "ifrs",
                "international financial reporting standards"
        );
        add(
                definitions,
                "Financial Analysis",
                "financial analysis",
                "phan tich tai chinh",
                "phân tích tài chính"
        );

        // Sales / customer-facing
        add(
                definitions,
                "B2B Sales",
                "b2b sales",
                "sales b2b",
                "ban hang b2b",
                "bán hàng b2b"
        );
        add(definitions, "Telesales", "telesales", "tele sales");
        add(
                definitions,
                "Negotiation",
                "negotiation",
                "dam phan",
                "đàm phán",
                "thuong luong",
                "thương lượng"
        );
        add(
                definitions,
                "Customer Relationship Management",
                "crm",
                "customer relationship management"
        );
        add(
                definitions,
                "Lead Generation",
                "lead generation",
                "tao lead",
                "tạo lead"
        );
        add(
                definitions,
                "Customer Service",
                "customer service",
                "cham soc khach hang",
                "chăm sóc khách hàng"
        );

        // Marketing / content
        add(
                definitions,
                "Search Engine Optimization",
                "seo",
                "search engine optimization"
        );
        add(
                definitions,
                "Google Ads",
                "google ads",
                "google adwords",
                "adwords"
        );
        add(
                definitions,
                "Facebook Ads",
                "facebook ads",
                "meta ads"
        );
        add(definitions, "Content Marketing", "content marketing");
        add(definitions, "Branding", "branding", "brand management");
        add(
                definitions,
                "Market Research",
                "market research",
                "nghien cuu thi truong",
                "nghiên cứu thị trường"
        );

        // Human resources
        add(
                definitions,
                "Recruitment",
                "recruitment",
                "tuyen dung",
                "tuyển dụng"
        );
        add(
                definitions,
                "C&B",
                "c&b",
                "compensation and benefits",
                "compensation & benefits"
        );
        add(
                definitions,
                "Payroll",
                "payroll",
                "tinh luong",
                "tính lương"
        );
        add(
                definitions,
                "Labor Law",
                "labor law",
                "labour law",
                "luat lao dong",
                "luật lao động"
        );
        add(
                definitions,
                "HRIS",
                "hris",
                "human resources information system"
        );

        // Healthcare / pharmacy
        add(
                definitions,
                "Điều dưỡng",
                "dieu duong",
                "điều dưỡng",
                "nursing"
        );
        add(
                definitions,
                "Chăm sóc bệnh nhân",
                "cham soc benh nhan",
                "chăm sóc bệnh nhân",
                "patient care"
        );
        add(
                definitions,
                "Dược lâm sàng",
                "duoc lam sang",
                "dược lâm sàng",
                "clinical pharmacy"
        );
        add(
                definitions,
                "GPP",
                "gpp",
                "good pharmacy practice"
        );

        // Engineering / manufacturing / quality
        add(
                definitions,
                "CNC",
                "cnc",
                "computer numerical control"
        );
        add(definitions, "AutoCAD", "autocad", "auto cad");
        add(
                definitions,
                "SolidWorks",
                "solidworks",
                "solid works"
        );
        add(
                definitions,
                "Lean Manufacturing",
                "lean manufacturing",
                "san xuat tinh gon",
                "sản xuất tinh gọn"
        );
        add(definitions, "5S", "5s");
        add(definitions, "Kaizen", "kaizen");
        add(
                definitions,
                "Đọc bản vẽ kỹ thuật",
                "doc ban ve ky thuat",
                "đọc bản vẽ kỹ thuật"
        );
        add(
                definitions,
                "Quality Control",
                "quality control",
                "kiem soat chat luong",
                "kiểm soát chất lượng",
                "qc"
        );

        // Logistics / supply chain
        add(
                definitions,
                "Import/Export",
                "import export",
                "import/export",
                "xuat nhap khau",
                "xuất nhập khẩu"
        );
        add(definitions, "Incoterms", "incoterms");
        add(
                definitions,
                "Customs Declaration",
                "customs declaration",
                "khai bao hai quan",
                "khai báo hải quan"
        );
        add(
                definitions,
                "Warehouse Management",
                "warehouse management",
                "quan ly kho",
                "quản lý kho"
        );
        add(
                definitions,
                "Supply Chain",
                "supply chain",
                "chuoi cung ung",
                "chuỗi cung ứng"
        );

        // Education
        add(
                definitions,
                "Teaching",
                "teaching",
                "giang day",
                "giảng dạy"
        );
        add(definitions, "IELTS", "ielts");
        add(
                definitions,
                "Lesson Planning",
                "lesson planning",
                "soan giao an",
                "soạn giáo án"
        );
        add(
                definitions,
                "Classroom Management",
                "classroom management",
                "quan ly lop hoc",
                "quản lý lớp học"
        );

        // Hospitality / food / operations
        add(
                definitions,
                "Food Safety",
                "food safety",
                "an toan thuc pham",
                "an toàn thực phẩm"
        );
        add(definitions, "HACCP", "haccp");
        add(
                definitions,
                "Front Office",
                "front office",
                "le tan",
                "lễ tân"
        );
        add(
                definitions,
                "Housekeeping",
                "housekeeping",
                "buong phong",
                "buồng phòng"
        );
        add(
                definitions,
                "Operations Management",
                "operations management",
                "quan ly van hanh",
                "quản lý vận hành"
        );

        // General office / design tools
        add(
                definitions,
                "Microsoft Excel",
                "excel",
                "ms excel",
                "microsoft excel"
        );
        add(
                definitions,
                "Microsoft Word",
                "word",
                "ms word",
                "microsoft word"
        );
        add(
                definitions,
                "Microsoft PowerPoint",
                "powerpoint",
                "power point",
                "ms powerpoint",
                "microsoft powerpoint"
        );
        add(
                definitions,
                "Adobe Photoshop",
                "photoshop",
                "adobe photoshop"
        );
        add(
                definitions,
                "Adobe Illustrator",
                "illustrator",
                "adobe illustrator"
        );

        return List.copyOf(definitions);
    }

    private static void add(
            List<SkillDefinition> definitions,
            String canonical,
            String... aliases
    ) {
        definitions.add(new SkillDefinition(
                canonical,
                List.of(aliases)
        ));
    }

    private record SkillDefinition(
            String canonical,
            List<String> aliases
    ) {
    }

    private record SkillMatcher(
            String canonical,
            Pattern pattern
    ) {
    }
}