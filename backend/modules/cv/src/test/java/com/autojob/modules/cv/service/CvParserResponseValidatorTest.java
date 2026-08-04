package com.autojob.modules.cv.service;

import com.autojob.modules.cv.client.CvParserResponseValidationException;
import com.autojob.modules.cv.client.dto.CvParseRequest;
import com.autojob.modules.cv.client.dto.CvParseResponse;
import com.autojob.modules.cv.config.CvParserProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CvParserResponseValidatorTest {

    private ObjectMapper objectMapper;
    private CvParserResponseValidator validator;
    private CvParseRequest request;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        CvParserProperties properties =
                new CvParserProperties();
        properties.setExpectedVersion("rule-v1");

        validator = new CvParserResponseValidator(
                properties
        );

        request = new CvParseRequest(
                "raw-cv-001",
                "autojob-cvs",
                "raw/2026/08/04/raw-cv-001/cv.pdf",
                "cv.pdf",
                "application/pdf"
        );
    }

    @Test
    void shouldAcceptValidResponse() throws Exception {
        CvParseResponse response = response(
                validResponseNode()
        );

        assertThat(
                validator.validate(request, response)
        ).isSameAs(response);
    }

    @Test
    void shouldRejectRawCvIdMismatch()
            throws Exception {
        ObjectNode root = validResponseNode();
        root.put("rawCvId", "another-raw-cv");

        assertInvalid(
                root,
                "rawCvId",
                "does not match"
        );
    }

    @Test
    void shouldRejectParserVersionMismatch()
            throws Exception {
        ObjectNode root = validResponseNode();
        root.put("parserVersion", "rule-v2");

        assertInvalid(
                root,
                "parserVersion",
                "expected rule-v1"
        );
    }

    @Test
    void shouldRejectNullProfile() throws Exception {
        ObjectNode root = validResponseNode();
        root.putNull("profile");

        assertInvalid(
                root,
                "profile",
                "must not be null"
        );
    }

    @Test
    void shouldRejectBlankRawText()
            throws Exception {
        ObjectNode root = validResponseNode();
        profile(root).put("rawText", "   ");
        root.put("extractedTextLength", 3);

        assertInvalid(
                root,
                "profile.rawText",
                "must not be blank"
        );
    }

    @Test
    void shouldRejectNegativeExtractedTextLength()
            throws Exception {
        ObjectNode root = validResponseNode();
        root.put("extractedTextLength", -1);

        assertInvalid(
                root,
                "extractedTextLength",
                "between 1"
        );
    }

    @Test
    void shouldRejectExtractedTextLengthMismatch()
            throws Exception {
        ObjectNode root = validResponseNode();
        root.put("extractedTextLength", 1);

        assertInvalid(
                root,
                "extractedTextLength",
                "does not match"
        );
    }

    @Test
    void shouldRejectNegativeDurationMonths()
            throws Exception {
        ObjectNode root = validResponseNode();

        ArrayNode experiences =
                (ArrayNode) profile(root)
                        .get("workExperiences");

        ObjectNode experience =
                experiences.addObject();

        experience.put(
                "companyName",
                "An Phát Logistics"
        );
        experience.put(
                "jobTitle",
                "Warehouse Supervisor"
        );
        experience.put("startDate", "2024-01");
        experience.put("endDate", "2025-01");
        experience.put("current", false);
        experience.put("durationMonths", -1);
        experience.putArray("responsibilities");
        experience.putArray("achievements");
        experience.putArray("skills");
        experience.putArray("tools");
        experience.putArray("equipment");

        assertInvalid(
                root,
                "profile.workExperiences[0].durationMonths",
                "between 0"
        );
    }

    @Test
    void shouldRejectNegativeExperienceYears()
            throws Exception {
        ObjectNode root = validResponseNode();
        profile(root).put("experienceYears", -0.5);

        assertInvalid(
                root,
                "profile.experienceYears",
                "between 0"
        );
    }

    @Test
    void shouldRejectInvalidDate()
            throws Exception {
        ObjectNode root = validResponseNode();

        ArrayNode educations =
                (ArrayNode) profile(root)
                        .get("educations");

        ObjectNode education =
                educations.addObject();

        education.put(
                "institutionName",
                "Economic College"
        );
        education.put(
                "degree",
                "Diploma in Accounting"
        );
        education.put("startDate", "2021/09");
        education.put("endDate", "2023-06");
        education.put("current", false);
        education.putArray("achievements");

        assertInvalid(
                root,
                "profile.educations[0].startDate",
                "YYYY or YYYY-MM"
        );
    }

    @Test
    void shouldRejectCurrentEntryWithEndDate()
            throws Exception {
        ObjectNode root = validResponseNode();

        ArrayNode projects =
                (ArrayNode) profile(root)
                        .get("projects");

        ObjectNode project = projects.addObject();

        project.put(
                "name",
                "Inventory Accuracy Program"
        );
        project.put("startDate", "2025-01");
        project.put("endDate", "2026-01");
        project.put("current", true);
        project.putArray("responsibilities");
        project.putArray("achievements");
        project.putArray("skills");
        project.putArray("tools");
        project.putArray("equipment");

        assertInvalid(
                root,
                "profile.projects[0].endDate",
                "must be null when current is true"
        );
    }

    @Test
    void shouldRejectInvalidQualityScore()
            throws Exception {
        ObjectNode root = validResponseNode();

        ObjectNode parseQuality =
                (ObjectNode) profile(root)
                        .get("parseQuality");

        parseQuality.put("overallScore", 1.1);

        assertInvalid(
                root,
                "profile.parseQuality.overallScore",
                "between 0.0 and 1.0"
        );
    }

    @Test
    void shouldRejectInvalidSectionOffsets()
            throws Exception {
        ObjectNode root = validResponseNode();

        ArrayNode sections =
                (ArrayNode) profile(root)
                        .get("sections");

        ObjectNode section = sections.addObject();

        section.put(
                "sectionType",
                "WORK_EXPERIENCE"
        );
        section.put("heading", "Experience");
        section.put("startOffset", 10);
        section.put("endOffset", 5);
        section.put("text", "text");

        assertInvalid(
                root,
                "profile.sections[0].startOffset",
                "must not exceed"
        );
    }

    @Test
    void shouldRejectSectionEndOffsetBeyondRawText()
            throws Exception {
        ObjectNode root = validResponseNode();

        ArrayNode sections =
                (ArrayNode) profile(root)
                        .get("sections");

        ObjectNode section = sections.addObject();

        section.put("sectionType", "HEADER");
        section.put("startOffset", 0);
        section.put("endOffset", 10_000);
        section.put("text", "header");

        assertInvalid(
                root,
                "profile.sections[0].endOffset",
                "exceeds profile.rawText length"
        );
    }

    @Test
    void shouldRejectNullRequiredCollection()
            throws Exception {
        ObjectNode root = validResponseNode();
        profile(root).putNull("skills");

        assertInvalid(
                root,
                "profile.skills",
                "must not be null"
        );
    }

    @Test
    void shouldCountUnicodeCodePointsLikePython()
            throws Exception {
        ObjectNode root = validResponseNode();
        String rawText = "Nurse 👩‍⚕ profile";

        profile(root).put("rawText", rawText);
        root.put(
                "extractedTextLength",
                rawText.codePointCount(
                        0,
                        rawText.length()
                )
        );

        CvParseResponse response = response(root);

        assertThat(
                validator.validate(request, response)
        ).isSameAs(response);
    }

    private void assertInvalid(
            ObjectNode root,
            String expectedField,
            String expectedMessage
    ) throws Exception {
        CvParseResponse response = response(root);

        assertThatThrownBy(
                () -> validator.validate(
                        request,
                        response
                )
        )
                .isInstanceOf(
                        CvParserResponseValidationException.class
                )
                .satisfies(throwable ->
                        assertThat(
                                ((CvParserResponseValidationException)
                                        throwable)
                                        .getField()
                        ).isEqualTo(expectedField)
                )
                .hasMessageContaining(expectedMessage);
    }

    private CvParseResponse response(
            ObjectNode root
    ) throws Exception {
        return objectMapper.treeToValue(
                root,
                CvParseResponse.class
        );
    }

    private ObjectNode profile(ObjectNode root) {
        return (ObjectNode) root.get("profile");
    }

    private ObjectNode validResponseNode() {
        String rawText =
                "Senior Accountant profile";

        ObjectNode root =
                objectMapper.createObjectNode();

        root.put("rawCvId", "raw-cv-001");
        root.put("parserVersion", "rule-v1");
        root.put(
                "extractedTextLength",
                rawText.codePointCount(
                        0,
                        rawText.length()
                )
        );
        root.put("detectedLanguage", "EN");
        root.putArray("warnings");

        ObjectNode profile =
                root.putObject("profile");

        profile.put(
                "fullName",
                "Nguyễn Minh Anh"
        );
        profile.put(
                "headline",
                "Senior Accountant"
        );
        profile.putObject("contact");
        profile.putArray("links");
        profile.putArray("targetJobTitles");
        profile.putArray("targetIndustries");
        profile.putArray("preferredLocations");
        profile.putArray("preferredWorkModes");
        profile.putArray(
                "preferredEmploymentTypes"
        );
        profile.putArray("skills");
        profile.putArray("workExperiences");
        profile.putArray("projects");
        profile.putArray("educations");
        profile.putArray("certifications");
        profile.putArray("licenses");
        profile.putArray("languages");
        profile.putArray("awards");
        profile.putArray("publications");
        profile.putArray(
                "volunteerExperiences"
        );
        profile.putArray("activities");
        profile.putArray("trainingCourses");
        profile.putArray("interests");
        profile.put("experienceYears", 9.5);
        profile.put("seniority", "SENIOR");
        profile.put(
                "highestEducationLevel",
                "BACHELOR"
        );
        profile.putArray("recentJobTitles")
                .add("Senior Accountant");
        profile.putArray("recentCompanies")
                .add("An Phát Manufacturing");
        profile.put("rawText", rawText);
        profile.putArray("sections");
        profile.putArray("parserWarnings");

        ObjectNode quality =
                profile.putObject("parseQuality");

        quality.put("overallScore", 0.92);
        quality.put(
                "textExtractionScore",
                1.0
        );
        quality.put(
                "sectionDetectionScore",
                0.9
        );
        quality.put(
                "workExperienceScore",
                0.85
        );
        quality.putArray(
                "missingImportantFields"
        );
        quality.putArray("ambiguousFields");

        return root;
    }
}