package com.autojob.modules.cv.service;

import com.autojob.modules.cv.client.CvParserResponseValidationException;
import com.autojob.modules.cv.client.dto.CvParseRequest;
import com.autojob.modules.cv.client.dto.CvParseResponse;
import com.autojob.modules.cv.config.CvParserProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

@Component
public class CvParserResponseValidator {

    private static final Pattern CANONICAL_DATE = Pattern.compile(
            "^\\d{4}(?:-(?:0[1-9]|1[0-2]))?$"
    );

    private static final int MAX_RAW_TEXT = 2_000_000;
    private static final int MAX_TEXT = 5_000;
    private static final int MAX_URL = 2_000;
    private static final int MAX_SECTION_TEXT = 50_000;
    private static final int MAX_WARNING_TEXT = 500;
    private static final int MAX_WARNINGS = 200;
    private static final int MAX_GENERIC_ITEMS = 1_000;
    private static final int MAX_DURATION_MONTHS = 1_200;
    private static final double MAX_EXPERIENCE_YEARS = 100.0;

    private final CvParserProperties properties;

    public CvParserResponseValidator(
            CvParserProperties properties
    ) {
        this.properties = Objects.requireNonNull(
                properties,
                "properties"
        );
    }

    public CvParseResponse validate(
            CvParseRequest request,
            CvParseResponse response
    ) {
        if (request == null) {
            throw invalid("request", "must not be null");
        }
        requiredText(request.rawCvId(), "request.rawCvId", 100);

        if (response == null) {
            throw invalid("response", "must not be null");
        }

        requiredText(response.rawCvId(), "rawCvId", 100);
        if (!request.rawCvId().equals(response.rawCvId())) {
            throw invalid("rawCvId", "does not match the request");
        }

        requiredText(response.parserVersion(), "parserVersion", 100);
        String expectedVersion = properties.getExpectedVersion().trim();
        if (!expectedVersion.equals(response.parserVersion())) {
            throw invalid(
                    "parserVersion",
                    "expected " + expectedVersion
                            + " but received "
                            + response.parserVersion()
            );
        }

        Integer extractedLength = response.extractedTextLength();
        if (extractedLength == null
                || extractedLength <= 0
                || extractedLength > MAX_RAW_TEXT) {
            throw invalid(
                    "extractedTextLength",
                    "must be between 1 and " + MAX_RAW_TEXT
            );
        }

        if (response.detectedLanguage() == null) {
            throw invalid(
                    "detectedLanguage",
                    "must not be null"
            );
        }

        if (response.profile() == null) {
            throw invalid("profile", "must not be null");
        }

        strings(
                response.warnings(),
                "warnings",
                MAX_WARNINGS,
                MAX_WARNING_TEXT
        );

        profile(response.profile(), extractedLength);
        return response;
    }

    private void profile(
            CvParseResponse.CandidateProfilePayload value,
            int extractedLength
    ) {
        text(value.fullName(), "profile.fullName");
        text(value.headline(), "profile.headline");
        text(
                value.professionalSummary(),
                "profile.professionalSummary"
        );
        text(
                value.careerObjective(),
                "profile.careerObjective"
        );
        text(
                value.expectedSalaryText(),
                "profile.expectedSalaryText"
        );
        text(
                value.availabilityText(),
                "profile.availabilityText"
        );

        if (value.contact() == null) {
            throw invalid(
                    "profile.contact",
                    "must not be null"
            );
        }
        contact(value.contact());

        objects(
                value.links(),
                "profile.links",
                200,
                this::link
        );
        strings(
                value.targetJobTitles(),
                "profile.targetJobTitles",
                200,
                MAX_TEXT
        );
        strings(
                value.targetIndustries(),
                "profile.targetIndustries",
                200,
                MAX_TEXT
        );
        strings(
                value.preferredLocations(),
                "profile.preferredLocations",
                200,
                MAX_TEXT
        );
        enums(
                value.preferredWorkModes(),
                "profile.preferredWorkModes",
                20
        );
        enums(
                value.preferredEmploymentTypes(),
                "profile.preferredEmploymentTypes",
                20
        );

        objects(
                value.skills(),
                "profile.skills",
                1_000,
                this::skill
        );
        objects(
                value.workExperiences(),
                "profile.workExperiences",
                200,
                this::work
        );
        objects(
                value.projects(),
                "profile.projects",
                200,
                this::project
        );
        objects(
                value.educations(),
                "profile.educations",
                100,
                this::education
        );
        objects(
                value.certifications(),
                "profile.certifications",
                200,
                this::certification
        );
        objects(
                value.licenses(),
                "profile.licenses",
                100,
                this::license
        );
        objects(
                value.languages(),
                "profile.languages",
                200,
                this::language
        );
        objects(
                value.awards(),
                "profile.awards",
                200,
                this::award
        );
        objects(
                value.publications(),
                "profile.publications",
                200,
                this::publication
        );
        objects(
                value.volunteerExperiences(),
                "profile.volunteerExperiences",
                200,
                this::volunteer
        );
        objects(
                value.activities(),
                "profile.activities",
                200,
                this::activity
        );
        objects(
                value.trainingCourses(),
                "profile.trainingCourses",
                200,
                this::training
        );
        strings(
                value.interests(),
                "profile.interests",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );

        experienceYears(
                value.experienceYears(),
                "profile.experienceYears"
        );

        if (value.seniority() == null) {
            throw invalid(
                    "profile.seniority",
                    "must not be null"
            );
        }

        strings(
                value.recentJobTitles(),
                "profile.recentJobTitles",
                200,
                MAX_TEXT
        );
        strings(
                value.recentCompanies(),
                "profile.recentCompanies",
                200,
                MAX_TEXT
        );

        requiredText(
                value.rawText(),
                "profile.rawText",
                MAX_RAW_TEXT
        );

        int rawTextLength = value.rawText()
                .codePointCount(
                        0,
                        value.rawText().length()
                );

        if (rawTextLength != extractedLength) {
            throw invalid(
                    "extractedTextLength",
                    "does not match profile.rawText length"
            );
        }

        objects(
                value.sections(),
                "profile.sections",
                MAX_GENERIC_ITEMS,
                (section, path) -> section(
                        section,
                        path,
                        extractedLength
                )
        );

        strings(
                value.parserWarnings(),
                "profile.parserWarnings",
                MAX_WARNINGS,
                MAX_WARNING_TEXT
        );

        if (value.parseQuality() == null) {
            throw invalid(
                    "profile.parseQuality",
                    "must not be null"
            );
        }

        quality(value.parseQuality());
    }

    private void contact(
            CvParseResponse.ContactInformation value
    ) {
        text(value.email(), "profile.contact.email");
        text(value.phone(), "profile.contact.phone");
        text(
                value.addressText(),
                "profile.contact.addressText"
        );
        text(value.city(), "profile.contact.city");
        text(
                value.provinceOrState(),
                "profile.contact.provinceOrState"
        );
        text(value.country(), "profile.contact.country");
        text(
                value.postalCode(),
                "profile.contact.postalCode"
        );
    }

    private void link(
            CvParseResponse.LinkEntry value,
            String path
    ) {
        if (value.type() == null) {
            throw invalid(
                    path + ".type",
                    "must not be null"
            );
        }

        requiredText(
                value.url(),
                path + ".url",
                MAX_URL
        );
        text(value.label(), path + ".label");
    }

    private void skill(
            CvParseResponse.Skill value,
            String path
    ) {
        requiredText(
                value.name(),
                path + ".name",
                300
        );
        requiredText(
                value.normalizedName(),
                path + ".normalizedName",
                300
        );

        if (value.category() == null) {
            throw invalid(
                    path + ".category",
                    "must not be null"
            );
        }

        text(
                value.proficiencyText(),
                path + ".proficiencyText"
        );
        experienceYears(
                value.yearsOfExperience(),
                path + ".yearsOfExperience"
        );
        date(
                value.lastUsedDate(),
                path + ".lastUsedDate"
        );
        strings(
                value.evidenceSources(),
                path + ".evidenceSources",
                20,
                MAX_TEXT
        );
    }

    private void work(
            CvParseResponse.WorkExperience value,
            String path
    ) {
        text(value.companyName(), path + ".companyName");
        text(
                value.companyIndustry(),
                path + ".companyIndustry"
        );
        text(value.jobTitle(), path + ".jobTitle");
        text(
                value.normalizedJobTitle(),
                path + ".normalizedJobTitle"
        );
        text(value.location(), path + ".location");

        dateRange(
                value.startDate(),
                value.endDate(),
                value.current(),
                path
        );

        duration(
                value.durationMonths(),
                path + ".durationMonths"
        );
        text(value.description(), path + ".description");

        strings(
                value.responsibilities(),
                path + ".responsibilities",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
        strings(
                value.achievements(),
                path + ".achievements",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
        strings(
                value.skills(),
                path + ".skills",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
        strings(
                value.tools(),
                path + ".tools",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
        strings(
                value.equipment(),
                path + ".equipment",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
    }

    private void project(
            CvParseResponse.ProjectExperience value,
            String path
    ) {
        text(value.name(), path + ".name");
        text(value.role(), path + ".role");
        text(value.domain(), path + ".domain");

        dateRange(
                value.startDate(),
                value.endDate(),
                value.current(),
                path
        );

        text(value.description(), path + ".description");
        strings(
                value.responsibilities(),
                path + ".responsibilities",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
        strings(
                value.achievements(),
                path + ".achievements",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
        strings(
                value.skills(),
                path + ".skills",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
        strings(
                value.tools(),
                path + ".tools",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
        strings(
                value.equipment(),
                path + ".equipment",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );

        text(
                value.teamSizeText(),
                path + ".teamSizeText"
        );
        text(
                value.projectUrl(),
                path + ".projectUrl",
                MAX_URL
        );
        text(
                value.repositoryUrl(),
                path + ".repositoryUrl",
                MAX_URL
        );
    }

    private void education(
            CvParseResponse.Education value,
            String path
    ) {
        text(
                value.institutionName(),
                path + ".institutionName"
        );
        text(value.degree(), path + ".degree");
        text(
                value.fieldOfStudy(),
                path + ".fieldOfStudy"
        );
        text(
                value.specialization(),
                path + ".specialization"
        );

        dateRange(
                value.startDate(),
                value.endDate(),
                value.current(),
                path
        );

        text(value.grade(), path + ".grade");
        strings(
                value.achievements(),
                path + ".achievements",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
        text(value.description(), path + ".description");
    }

    private void certification(
            CvParseResponse.Certification value,
            String path
    ) {
        text(value.name(), path + ".name");
        text(value.issuer(), path + ".issuer");
        date(value.issuedDate(), path + ".issuedDate");
        date(
                value.expirationDate(),
                path + ".expirationDate"
        );
        text(
                value.credentialId(),
                path + ".credentialId"
        );
        text(
                value.credentialUrl(),
                path + ".credentialUrl",
                MAX_URL
        );
        strings(
                value.relatedSkills(),
                path + ".relatedSkills",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
    }

    private void license(
            CvParseResponse.LicenseEntry value,
            String path
    ) {
        text(value.name(), path + ".name");
        text(
                value.issuingAuthority(),
                path + ".issuingAuthority"
        );
        text(
                value.licenseNumber(),
                path + ".licenseNumber"
        );
        date(value.issuedDate(), path + ".issuedDate");
        date(
                value.expirationDate(),
                path + ".expirationDate"
        );
        text(
                value.jurisdiction(),
                path + ".jurisdiction"
        );
    }

    private void training(
            CvParseResponse.TrainingCourse value,
            String path
    ) {
        text(value.name(), path + ".name");
        text(value.provider(), path + ".provider");
        date(
                value.completionDate(),
                path + ".completionDate"
        );
        text(
                value.durationText(),
                path + ".durationText"
        );
        text(value.description(), path + ".description");
        strings(
                value.relatedSkills(),
                path + ".relatedSkills",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
    }

    private void language(
            CvParseResponse.LanguageSkill value,
            String path
    ) {
        text(value.language(), path + ".language");
        text(
                value.proficiencyText(),
                path + ".proficiencyText"
        );
        text(value.framework(), path + ".framework");
        text(value.score(), path + ".score");
    }

    private void award(
            CvParseResponse.Award value,
            String path
    ) {
        text(value.name(), path + ".name");
        text(value.issuer(), path + ".issuer");
        date(
                value.awardedDate(),
                path + ".awardedDate"
        );
        text(value.description(), path + ".description");
    }

    private void publication(
            CvParseResponse.Publication value,
            String path
    ) {
        text(value.title(), path + ".title");
        strings(
                value.authors(),
                path + ".authors",
                200,
                MAX_TEXT
        );
        text(value.publisher(), path + ".publisher");
        date(
                value.publishedDate(),
                path + ".publishedDate"
        );
        text(value.url(), path + ".url", MAX_URL);
        text(value.description(), path + ".description");
    }

    private void volunteer(
            CvParseResponse.VolunteerExperience value,
            String path
    ) {
        text(
                value.organizationName(),
                path + ".organizationName"
        );
        text(value.role(), path + ".role");
        date(value.startDate(), path + ".startDate");
        date(value.endDate(), path + ".endDate");
        text(value.description(), path + ".description");
        strings(
                value.responsibilities(),
                path + ".responsibilities",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
        strings(
                value.skills(),
                path + ".skills",
                MAX_GENERIC_ITEMS,
                MAX_TEXT
        );
    }

    private void activity(
            CvParseResponse.ProfessionalActivity value,
            String path
    ) {
        text(value.name(), path + ".name");
        text(
                value.organization(),
                path + ".organization"
        );
        text(value.role(), path + ".role");
        date(value.startDate(), path + ".startDate");
        date(value.endDate(), path + ".endDate");
        text(value.description(), path + ".description");
    }

    private void section(
            CvParseResponse.ParsedSection value,
            String path,
            int rawTextLength
    ) {
        if (value.sectionType() == null) {
            throw invalid(
                    path + ".sectionType",
                    "must not be null"
            );
        }

        text(value.heading(), path + ".heading");

        Integer start = value.startOffset();
        Integer end = value.endOffset();

        if (start == null || start < 0) {
            throw invalid(
                    path + ".startOffset",
                    "must be non-negative"
            );
        }

        if (end == null || end < 0) {
            throw invalid(
                    path + ".endOffset",
                    "must be non-negative"
            );
        }

        if (start > end) {
            throw invalid(
                    path + ".startOffset",
                    "must not exceed endOffset"
            );
        }

        if (end > rawTextLength) {
            throw invalid(
                    path + ".endOffset",
                    "exceeds profile.rawText length"
            );
        }

        text(
                value.text(),
                path + ".text",
                MAX_SECTION_TEXT
        );
    }

    private void quality(
            CvParseResponse.ParseQuality value
    ) {
        score(
                value.overallScore(),
                "profile.parseQuality.overallScore"
        );
        score(
                value.textExtractionScore(),
                "profile.parseQuality.textExtractionScore"
        );
        score(
                value.sectionDetectionScore(),
                "profile.parseQuality.sectionDetectionScore"
        );
        score(
                value.workExperienceScore(),
                "profile.parseQuality.workExperienceScore"
        );

        strings(
                value.missingImportantFields(),
                "profile.parseQuality.missingImportantFields",
                200,
                MAX_TEXT
        );
        strings(
                value.ambiguousFields(),
                "profile.parseQuality.ambiguousFields",
                200,
                MAX_TEXT
        );
    }

    private void dateRange(
            String start,
            String end,
            Boolean current,
            String path
    ) {
        date(start, path + ".startDate");
        date(end, path + ".endDate");

        if (Boolean.TRUE.equals(current)
                && end != null) {
            throw invalid(
                    path + ".endDate",
                    "must be null when current is true"
            );
        }
    }

    private void date(
            String value,
            String field
    ) {
        if (value != null
                && !CANONICAL_DATE.matcher(value).matches()) {
            throw invalid(
                    field,
                    "must use YYYY or YYYY-MM"
            );
        }
    }

    private void duration(
            Integer value,
            String field
    ) {
        if (value != null
                && (value < 0
                || value > MAX_DURATION_MONTHS)) {
            throw invalid(
                    field,
                    "must be between 0 and "
                            + MAX_DURATION_MONTHS
            );
        }
    }

    private void experienceYears(
            Double value,
            String field
    ) {
        if (value != null
                && (!Double.isFinite(value)
                || value < 0.0
                || value > MAX_EXPERIENCE_YEARS)) {
            throw invalid(
                    field,
                    "must be finite and between 0 and "
                            + MAX_EXPERIENCE_YEARS
            );
        }
    }

    private void score(
            Double value,
            String field
    ) {
        if (value == null
                || !Double.isFinite(value)
                || value < 0.0
                || value > 1.0) {
            throw invalid(
                    field,
                    "must be finite and between 0.0 and 1.0"
            );
        }
    }

    private void text(
            String value,
            String field
    ) {
        text(value, field, MAX_TEXT);
    }

    private void text(
            String value,
            String field,
            int maxLength
    ) {
        if (value != null
                && value.codePointCount(
                0,
                value.length()
        ) > maxLength) {
            throw invalid(
                    field,
                    "exceeds maximum length " + maxLength
            );
        }
    }

    private void requiredText(
            String value,
            String field,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw invalid(field, "must not be blank");
        }

        text(value, field, maxLength);
    }

    private void strings(
            List<String> values,
            String field,
            int maxItems,
            int maxLength
    ) {
        list(values, field, maxItems);

        for (int index = 0;
             index < values.size();
             index++) {
            String item = values.get(index);
            String itemField =
                    field + "[" + index + "]";

            if (item == null) {
                throw invalid(
                        itemField,
                        "must not be null"
                );
            }

            if (item.isBlank()) {
                throw invalid(
                        itemField,
                        "must not be blank"
                );
            }

            text(item, itemField, maxLength);
        }
    }

    private void enums(
            List<?> values,
            String field,
            int maxItems
    ) {
        list(values, field, maxItems);

        for (int index = 0;
             index < values.size();
             index++) {
            if (values.get(index) == null) {
                throw invalid(
                        field + "[" + index + "]",
                        "must not be null"
                );
            }
        }
    }

    private <T> void objects(
            List<T> values,
            String field,
            int maxItems,
            BiConsumer<T, String> validator
    ) {
        list(values, field, maxItems);

        for (int index = 0;
             index < values.size();
             index++) {
            T item = values.get(index);
            String itemField =
                    field + "[" + index + "]";

            if (item == null) {
                throw invalid(
                        itemField,
                        "must not be null"
                );
            }

            validator.accept(item, itemField);
        }
    }

    private void list(
            List<?> values,
            String field,
            int maxItems
    ) {
        if (values == null) {
            throw invalid(field, "must not be null");
        }

        if (values.size() > maxItems) {
            throw invalid(
                    field,
                    "contains more than "
                            + maxItems
                            + " items"
            );
        }
    }

    private CvParserResponseValidationException invalid(
            String field,
            String reason
    ) {
        return new CvParserResponseValidationException(
                field,
                reason
        );
    }
}