package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class JobTypeNormalizer {

    private static final Pattern INTERNSHIP_PATTERN = Pattern.compile(
            "\\bintern\\b"
                    + "|\\binternship\\b"
                    + "|\\btrainee\\b"
                    + "|\\bthuc tap\\b"
                    + "|\\bthuc tap sinh\\b"
    );

    private static final Pattern PART_TIME_PATTERN = Pattern.compile(
            "\\bpart time\\b"
                    + "|\\bpart-time\\b"
                    + "|\\bban thoi gian\\b"
    );

    private static final Pattern FREELANCE_PATTERN = Pattern.compile(
            "\\bfreelance\\b"
                    + "|\\bfreelancer\\b"
                    + "|\\bcong tac vien\\b"
                    + "|\\bctv\\b"
    );

    private static final Pattern TEMPORARY_PATTERN = Pattern.compile(
            "\\btemporary\\b"
                    + "|\\bseasonal\\b"
                    + "|\\bthoi vu\\b"
                    + "|\\bngan han\\b"
    );

    private static final Pattern CONTRACT_PATTERN = Pattern.compile(
            "\\bcontract\\b"
                    + "|\\bcontractor\\b"
                    + "|\\bfixed term\\b"
                    + "|\\bfixed-term\\b"
                    + "|\\bhop dong\\b"
    );

    private static final Pattern FULL_TIME_PATTERN = Pattern.compile(
            "\\bfull time\\b"
                    + "|\\bfull-time\\b"
                    + "|\\btoan thoi gian\\b"
                    + "|\\bpermanent\\b"
    );

    /**
     * Ưu tiên jobTypeText từ crawler.
     * Chỉ dùng title làm fallback cho các trường hợp rõ ràng.
     */
    public NormalizedJobType normalize(
            String jobTypeText,
            String title
    ) {
        NormalizedJobType fromJobTypeText =
                detect(jobTypeText);

        if (fromJobTypeText != NormalizedJobType.UNKNOWN) {
            return fromJobTypeText;
        }

        return detect(title);
    }

    private NormalizedJobType detect(String value) {
        String folded = NormalizationTextSupport.fold(value);

        if (folded.isBlank()) {
            return NormalizedJobType.UNKNOWN;
        }

        /*
         * Dữ liệu Schema.org trong repo hiện có dạng:
         *
         * FULL_TIME
         * PART_TIME
         * CONTRACTOR
         * TEMPORARY
         * INTERN
         * OTHER
         *
         * fold() giữ underscore nên đổi chúng thành khoảng trắng.
         */
        folded = folded
                .replace('_', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        if (INTERNSHIP_PATTERN.matcher(folded).find()) {
            return NormalizedJobType.INTERNSHIP;
        }

        if (PART_TIME_PATTERN.matcher(folded).find()) {
            return NormalizedJobType.PART_TIME;
        }

        if (FREELANCE_PATTERN.matcher(folded).find()) {
            return NormalizedJobType.FREELANCE;
        }

        if (TEMPORARY_PATTERN.matcher(folded).find()) {
            return NormalizedJobType.TEMPORARY;
        }

        if (CONTRACT_PATTERN.matcher(folded).find()) {
            return NormalizedJobType.CONTRACT;
        }

        if (FULL_TIME_PATTERN.matcher(folded).find()) {
            return NormalizedJobType.FULL_TIME;
        }

        /*
         * OTHER không đủ thông tin để suy luận.
         */
        return NormalizedJobType.UNKNOWN;
    }
}