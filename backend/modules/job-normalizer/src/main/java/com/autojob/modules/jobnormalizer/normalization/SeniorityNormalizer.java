package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SeniorityNormalizer {

    private static final Pattern DIRECTOR_PATTERN = Pattern.compile(
            "\\b(director|chief|ceo|cto|cfo|coo)\\b"
                    + "|\\bhead of\\b"
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
    );

    private static final Pattern LEAD_PATTERN = Pattern.compile(
            "\\bteam lead\\b"
                    + "|\\btech lead\\b"
                    + "|\\blead developer\\b"
                    + "|\\blead engineer\\b"
                    + "|\\bleader\\b"
                    + "|\\btruong nhom\\b"
                    + "|\\bto truong\\b"
    );

    private static final Pattern SENIOR_PATTERN = Pattern.compile(
            "\\bsenior\\b"
                    + "|\\bsr\\.?\\b"
                    + "|\\bchuyen vien cao cap\\b"
                    + "|\\bnhan vien cao cap\\b"
    );

    private static final Pattern MID_PATTERN = Pattern.compile(
            "\\bmiddle\\b"
                    + "|\\bmid level\\b"
                    + "|\\bmid-level\\b"
                    + "|\\bintermediate\\b"
    );

    private static final Pattern JUNIOR_PATTERN = Pattern.compile(
            "\\bjunior\\b"
                    + "|\\bjr\\.?\\b"
                    + "|\\bentry level\\b"
                    + "|\\bentry-level\\b"
                    + "|\\bnhan vien moi\\b"
    );

    private static final Pattern FRESHER_PATTERN = Pattern.compile(
            "\\bfresher\\b"
                    + "|\\bfresh graduate\\b"
                    + "|\\bnew graduate\\b"
                    + "|\\bgraduate trainee\\b"
                    + "|\\bmoi tot nghiep\\b"
                    + "|\\bchua co kinh nghiem\\b"
    );

    private static final Pattern INTERN_PATTERN = Pattern.compile(
            "\\bintern\\b"
                    + "|\\binternship\\b"
                    + "|\\btrainee\\b"
                    + "|\\bthuc tap\\b"
                    + "|\\bthuc tap sinh\\b"
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
        SeniorityLevel fromSeniorityText =
                detectExplicitLevel(seniorityText);

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
         * Kiểm tra từ level cao xuống thấp.
         *
         * Ví dụ "Senior Engineering Manager" phải ra MANAGER,
         * không dừng ở SENIOR.
         */
        if (DIRECTOR_PATTERN.matcher(folded).find()) {
            return SeniorityLevel.DIRECTOR;
        }

        if (MANAGER_PATTERN.matcher(folded).find()) {
            return SeniorityLevel.MANAGER;
        }

        if (LEAD_PATTERN.matcher(folded).find()) {
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

        if (FRESHER_PATTERN.matcher(folded).find()) {
            return SeniorityLevel.FRESHER;
        }

        if (INTERN_PATTERN.matcher(folded).find()) {
            return SeniorityLevel.INTERN;
        }

        return SeniorityLevel.UNKNOWN;
    }

    private SeniorityLevel inferFromExperience(
            ExperienceNormalizationResult experience
    ) {
        if (experience == null || !experience.known()) {
            return SeniorityLevel.UNKNOWN;
        }

        Double min = experience.min();
        Double max = experience.max();

        /*
         * Không yêu cầu kinh nghiệm hoặc dưới một năm.
         */
        if (min != null && min == 0.0) {
            if (max == null || max <= 1.0) {
                return SeniorityLevel.FRESHER;
            }
        }

        double effectiveYears;

        if (min != null) {
            effectiveYears = min;
        } else if (max != null) {
            effectiveYears = max;
        } else {
            return SeniorityLevel.UNKNOWN;
        }

        if (effectiveYears < 1.0) {
            return SeniorityLevel.FRESHER;
        }

        if (effectiveYears < 2.0) {
            return SeniorityLevel.JUNIOR;
        }

        if (effectiveYears < 5.0) {
            return SeniorityLevel.MID;
        }

        return SeniorityLevel.SENIOR;
    }
}