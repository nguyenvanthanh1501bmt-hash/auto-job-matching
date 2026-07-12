package com.autojob.modules.jobnormalizer.normalization;

import com.autojob.modules.jobcrawler.domain.RawJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RawJobContentHasher {

    private final TextNormalizer textNormalizer;

    public String hash(RawJob rawJob) {
        StringBuilder canonicalContent = new StringBuilder();

        appendInline(
                canonicalContent,
                "sourceCode",
                rawJob.getSourceCode()
        );

        appendInline(
                canonicalContent,
                "sourceJobId",
                rawJob.getSourceJobId()
        );

        appendInline(
                canonicalContent,
                "title",
                rawJob.getTitle()
        );

        appendInline(
                canonicalContent,
                "companyName",
                rawJob.getCompanyName()
        );

        appendInline(
                canonicalContent,
                "salaryText",
                rawJob.getSalaryText()
        );

        appendInline(
                canonicalContent,
                "locationText",
                rawJob.getLocationText()
        );

        appendInline(
                canonicalContent,
                "experienceText",
                rawJob.getExperienceText()
        );

        appendInline(
                canonicalContent,
                "seniorityText",
                rawJob.getSeniorityText()
        );

        appendInline(
                canonicalContent,
                "jobTypeText",
                rawJob.getJobTypeText()
        );

        appendInline(
                canonicalContent,
                "deadlineText",
                rawJob.getDeadlineText()
        );

        appendInline(
                canonicalContent,
                "postedText",
                rawJob.getPostedText()
        );

        appendSkills(
                canonicalContent,
                rawJob.getSkills()
        );

        appendMultiline(
                canonicalContent,
                "descriptionText",
                rawJob.getDescriptionText()
        );

        appendMultiline(
                canonicalContent,
                "requirementsText",
                rawJob.getRequirementsText()
        );

        appendMultiline(
                canonicalContent,
                "benefitsText",
                rawJob.getBenefitsText()
        );

        appendInline(
                canonicalContent,
                "detailUrl",
                rawJob.getDetailUrl()
        );

        appendInline(
                canonicalContent,
                "applyUrl",
                rawJob.getApplyUrl()
        );

        appendValue(
                canonicalContent,
                "applyType",
                rawJob.getApplyType() == null
                        ? null
                        : rawJob.getApplyType().name()
        );

        return sha256(canonicalContent.toString());
    }

    private void appendSkills(
            StringBuilder builder,
            List<String> skills
    ) {
        if (skills == null) {
            appendValue(builder, "skills.size", null);
            return;
        }

        appendValue(
                builder,
                "skills.size",
                String.valueOf(skills.size())
        );

        for (int index = 0; index < skills.size(); index++) {
            String cleanedSkill =
                    textNormalizer.normalizeInline(skills.get(index));

            appendValue(
                    builder,
                    "skills." + index,
                    cleanedSkill
            );
        }
    }

    private void appendInline(
            StringBuilder builder,
            String fieldName,
            String value
    ) {
        appendValue(
                builder,
                fieldName,
                textNormalizer.normalizeInline(value)
        );
    }

    private void appendMultiline(
            StringBuilder builder,
            String fieldName,
            String value
    ) {
        appendValue(
                builder,
                fieldName,
                textNormalizer.normalizeMultiline(value)
        );
    }

    private void appendValue(
            StringBuilder builder,
            String fieldName,
            String value
    ) {
        builder.append(fieldName.length())
                .append(':')
                .append(fieldName)
                .append('=');

        if (value == null) {
            builder.append("-1:");
        } else {
            builder.append(value.length())
                    .append(':')
                    .append(value);
        }

        builder.append('\n');
    }

    private String sha256(String value) {
        try {
            MessageDigest messageDigest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = messageDigest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }
}