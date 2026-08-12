package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class SeniorityNormalizer {

    private static final Pattern DIRECTOR_PATTERN = Pattern.compile(
            "\\bdirector\\b"
                    + "|\\bhead of\\b"
                    + "|\\bceo\\b"
                    + "|\\bcto\\b"
                    + "|\\bcfo\\b"
                    + "|\\bcoo\\b"
                    + "|\\bchief executive officer\\b"
                    + "|\\bchief technology officer\\b"
                    + "|\\bchief financial officer\\b"
                    + "|\\bchief operating officer\\b"
                    + "|\\bgiam doc\\b"
                    + "|\\bpho giam doc\\b"
                    + "|\\btruong khoi\\b"
    );

    private static final Pattern MANAGER_PATTERN = Pattern.compile(
            "\\bmanager\\b"
                    + "|\\bsupervisor\\b"
                    + "|\\bquan ly\\b"
                    + "|\\btruong phong\\b"
                    + "|\\btruong bo phan\\b"
                    + "|\\bke toan truong\\b"
                    + "|\\bchief accountant\\b"
    );

    private static final Pattern LEAD_PATTERN = Pattern.compile(
            "\\bteam lead\\b"
                    + "|\\btech lead\\b"
                    + "|\\btechnical lead\\b"
                    + "|\\bproject lead\\b"
                    + "|\\bsales lead\\b"
                    + "|\\bmarketing lead\\b"
                    + "|\\boperations lead\\b"
                    + "|\\brecruitment lead\\b"
                    + "|\\bleader\\b"
                    + "|\\btruong nhom\\b"
                    + "|\\bto truong\\b"
                    + "|^lead\\b"
                    + "|\\blead$"
    );

    private static final Pattern SENIOR_PATTERN = Pattern.compile(
            "\\bsenior\\b"
                    + "|\\bsr\\.?\\b"
                    + "|\\bchuyen vien cao cap\\b"
                    + "|\\bnhan vien cao cap\\b"
    );

    private static final Pattern MID_PATTERN = Pattern.compile(
            "\\bmiddle\\b"
                    + "|\\bmid[- ]?level\\b"
                    + "|\\bintermediate\\b"
    );

    private static final Pattern JUNIOR_PATTERN = Pattern.compile(
            "\\bjunior\\b"
                    + "|\\bjr\\.?\\b"
    );

    private static final Pattern INTERN_PATTERN = Pattern.compile(
            "\\bintern\\b"
                    + "|\\binternship\\b"
                    + "|\\btrainee\\b"
                    + "|\\bthuc tap\\b"
                    + "|\\bthuc tap sinh\\b"
    );

    private static final Pattern FRESHER_PATTERN = Pattern.compile(
            "\\bfresher\\b"
                    + "|\\bfresh graduate\\b"
                    + "|\\bnew graduate\\b"
                    + "|\\bgraduate trainee\\b"
                    + "|\\bgraduate\\b"
                    + "|\\bentry[- ]?level\\b"
                    + "|\\bmoi tot nghiep\\b"
                    + "|\\bkhong yeu cau kinh nghiem\\b"
                    + "|\\bkhong can kinh nghiem\\b"
                    + "|\\bchua co kinh nghiem\\b"
    );

    /**
     * Thứ tự ưu tiên:
     *
     * 1. seniorityText
     * 2. title
     * 3. experience
     * 4. UNKNOWN
     */
    public SeniorityLevel normalize(
            String seniorityText,
            String title,
            ExperienceNormalizationResult experience
    ) {
        SeniorityLevel fromSeniorityText = detectExplicitLevel(
                seniorityText
        );

        if (fromSeniorityText != SeniorityLevel.UNKNOWN) {
            return fromSeniorityText;
        }

        SeniorityLevel fromTitle = detectExplicitLevel(title);

        if (fromTitle != SeniorityLevel.UNKNOWN) {
            return fromTitle;
        }

        return inferFromExperience(experience);
    }

    private SeniorityLevel detectExplicitLevel(String value) {
        String folded = NormalizationTextSupport.fold(value);

        if (folded.isBlank()) {
            return SeniorityLevel.UNKNOWN;
        }

        /*
         * Kiểm tra level quản lý trước senior/lead để title như
         * "Senior Sales Manager" vẫn ra MANAGER.
         */
        if (DIRECTOR_PATTERN.matcher(folded).find()) {
            return SeniorityLevel.DIRECTOR;
        }

        if (MANAGER_PATTERN.matcher(folded).find()) {
            return SeniorityLevel.MANAGER;
        }

        if (LEAD_PATTERN.matcher(folded).find()
                && !isLeadGenerationRole(folded)) {
            return SeniorityLevel.LEAD;
        }

        if (SENIOR_PATTERN.matcher(folded).find()) {
            return SeniorityLevel.SENIOR;
        }

        if (MID_PATTERN.matcher(folded).find()) {
            return SeniorityLevel.MID;
        }

        if (JUNIOR_PATTERN.matcher(folded).find()) {
            return SeniorityLevel.JUNIOR;
        }

        if (INTERN_PATTERN.matcher(folded).find()) {
            return SeniorityLevel.INTERN;
        }

        if (FRESHER_PATTERN.matcher(folded).find()) {
            return SeniorityLevel.FRESHER;
        }

        return SeniorityLevel.UNKNOWN;
    }

    private boolean isLeadGenerationRole(String folded) {
        return folded.startsWith("lead generation")
                && !folded.contains("team lead")
                && !folded.contains("leader");
    }

    private SeniorityLevel inferFromExperience(
            ExperienceNormalizationResult experience
    ) {
        if (experience == null || !experience.known()) {
            return SeniorityLevel.UNKNOWN;
        }

        Double min = experience.min();
        Double max = experience.max();

        if (min != null && min < 0) {
            min = null;
        }

        if (max != null && max < 0) {
            max = null;
        }

        if (min == null && max == null) {
            return SeniorityLevel.UNKNOWN;
        }

        double effectiveYears = min != null
                ? min
                : max;

        if (effectiveYears < 1.0) {
            return SeniorityLevel.FRESHER;
        }

        if (effectiveYears < 2.0) {
            return SeniorityLevel.JUNIOR;
        }

        if (effectiveYears < 5.0) {
            return SeniorityLevel.MID;
        }

        /*
         * Experience fallback tuyệt đối không suy ra LEAD/MANAGER/DIRECTOR.
         */
        return SeniorityLevel.SENIOR;
    }
}