package com.autojob.modules.cv.api;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.service.CvParsingException;
import com.autojob.modules.cv.service.CvParsingService;
import com.autojob.modules.cv.service.CvUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication
        .UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

class CvControllerTest {

    private static final String RAW_CV_ID =
            "raw-cv-001";

    private static final String PUBLIC_OWNER_USER_ID =
            CvController.DEFAULT_PUBLIC_OWNER_USER_ID;

    private CvUploadService cvUploadService;
    private CvParsingService cvParsingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cvUploadService =
                mock(CvUploadService.class);

        cvParsingService =
                mock(CvParsingService.class);

        CvController controller =
                new CvController(
                        cvUploadService,
                        cvParsingService,
                        true,
                        PUBLIC_OWNER_USER_ID
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(
                        new CvExceptionHandler()
                )
                .build();
    }

    @Test
    void shouldParseWarehouseProfileInPublicMode()
            throws Exception {
        CandidateProfile profile =
                warehouseProfile();

        when(
                cvParsingService.parse(
                        RAW_CV_ID,
                        PUBLIC_OWNER_USER_ID
                )
        ).thenReturn(profile);

        mockMvc.perform(
                        post(
                                "/api/cvs/{rawCvId}/parse",
                                RAW_CV_ID
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.rawCvId")
                                .value(RAW_CV_ID)
                )
                .andExpect(
                        jsonPath("$.fullName")
                                .value("Trần Quốc Huy")
                )
                .andExpect(
                        jsonPath("$.headline")
                                .value(
                                        "Warehouse Supervisor"
                                )
                )
                .andExpect(
                        jsonPath("$.contact.email")
                                .value(
                                        "quoc.huy@example.com"
                                )
                )
                .andExpect(
                        jsonPath("$.contact.phone")
                                .value("+84901234567")
                )
                .andExpect(
                        jsonPath("$.skills[0].name")
                                .value(
                                        "Forklift Operation"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.skills[0].category"
                        ).value("EQUIPMENT")
                )
                .andExpect(
                        jsonPath(
                                "$.workExperiences[0]"
                                        + ".companyName"
                        ).value(
                                "An Phat Logistics"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.workExperiences[0]"
                                        + ".jobTitle"
                        ).value(
                                "Warehouse Supervisor"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.workExperiences[0]"
                                        + ".equipment[0]"
                        ).value("Reach Truck")
                )
                .andExpect(
                        jsonPath(
                                "$.educations[0].degree"
                        ).value(
                                "Diploma in Logistics"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.certifications[0].name"
                        ).value(
                                "Occupational Safety Training"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.licenses[0].name"
                        ).value(
                                "Forklift Operator License"
                        )
                )
                .andExpect(
                        jsonPath("$.experienceYears")
                                .value(8.5)
                )
                .andExpect(
                        jsonPath("$.seniority")
                                .value("SUPERVISOR")
                )
                .andExpect(
                        jsonPath(
                                "$.parseQuality.overallScore"
                        ).value(0.91)
                )
                .andExpect(
                        jsonPath("$.parserWarnings[0]")
                                .value(
                                        "MULTI_COLUMN_LAYOUT_SUSPECTED"
                                )
                )
                .andExpect(
                        jsonPath("$.parserVersion")
                                .value("rule-v1")
                )
                .andExpect(
                        jsonPath("$.rawText")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.sourceObjectKey")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.sourceSha256")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$.ownerUserId")
                                .doesNotExist()
                );

        verify(cvParsingService).parse(
                RAW_CV_ID,
                PUBLIC_OWNER_USER_ID
        );
    }

    @Test
    void shouldGetRegisteredNurseProfileInPublicMode()
            throws Exception {
        CandidateProfile profile =
                nurseProfile();

        when(
                cvParsingService.getProfile(
                        RAW_CV_ID,
                        PUBLIC_OWNER_USER_ID
                )
        ).thenReturn(profile);

        mockMvc.perform(
                        get(
                                "/api/cvs/{rawCvId}/profile",
                                RAW_CV_ID
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.fullName")
                                .value("Lê Thu Hà")
                )
                .andExpect(
                        jsonPath("$.headline")
                                .value(
                                        "Registered Nurse"
                                )
                )
                .andExpect(
                        jsonPath("$.contact.email")
                                .value(
                                        "thu.ha@example.com"
                                )
                )
                .andExpect(
                        jsonPath("$.skills[0].name")
                                .value(
                                        "Patient Care"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.skills[0].category"
                        ).value("HEALTHCARE")
                )
                .andExpect(
                        jsonPath(
                                "$.workExperiences[0]"
                                        + ".companyName"
                        ).value(
                                "City General Hospital"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.workExperiences[0]"
                                        + ".employmentType"
                        ).value("SHIFT_WORK")
                )
                .andExpect(
                        jsonPath(
                                "$.educations[0]"
                                        + ".normalizedDegreeLevel"
                        ).value("BACHELOR")
                )
                .andExpect(
                        jsonPath(
                                "$.certifications[0].name"
                        ).value(
                                "Basic Life Support"
                        )
                )
                .andExpect(
                        jsonPath(
                                "$.licenses[0].name"
                        ).value(
                                "Registered Nurse License"
                        )
                )
                .andExpect(
                        jsonPath("$.experienceYears")
                                .value(6.0)
                )
                .andExpect(
                        jsonPath("$.seniority")
                                .value("SENIOR")
                )
                .andExpect(
                        jsonPath(
                                "$.parseQuality.overallScore"
                        ).value(0.94)
                )
                .andExpect(
                        jsonPath("$.parserVersion")
                                .value("rule-v1")
                );

        verify(cvParsingService).getProfile(
                RAW_CV_ID,
                PUBLIC_OWNER_USER_ID
        );
    }

    @Test
    void shouldUseAuthenticatedOwnerWhenPublicModeIsDisabled()
            throws Exception {
        CvUploadService protectedUploadService =
                mock(CvUploadService.class);

        CvParsingService protectedParsingService =
                mock(CvParsingService.class);

        CvController protectedController =
                new CvController(
                        protectedUploadService,
                        protectedParsingService,
                        false,
                        PUBLIC_OWNER_USER_ID
                );

        MockMvc protectedMockMvc =
                MockMvcBuilders
                        .standaloneSetup(
                                protectedController
                        )
                        .setControllerAdvice(
                                new CvExceptionHandler()
                        )
                        .build();

        CandidateProfile profile =
                accountantProfile();

        when(
                protectedParsingService.parse(
                        RAW_CV_ID,
                        "accountant-user"
                )
        ).thenReturn(profile);

        UsernamePasswordAuthenticationToken
                authentication =
                new UsernamePasswordAuthenticationToken(
                        "accountant-user",
                        "not-used",
                        List.of()
                );

        protectedMockMvc.perform(
                        post(
                                "/api/cvs/{rawCvId}/parse",
                                RAW_CV_ID
                        ).principal(authentication)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.fullName")
                                .value(
                                        "Nguyễn Minh Anh"
                                )
                )
                .andExpect(
                        jsonPath("$.headline")
                                .value(
                                        "Senior Accountant"
                                )
                )
                .andExpect(
                        jsonPath(
                                "$.skills[0].category"
                        ).value("ACCOUNTING")
                )
                .andExpect(
                        jsonPath("$.seniority")
                                .value("SENIOR")
                );

        verify(protectedParsingService).parse(
                RAW_CV_ID,
                "accountant-user"
        );
    }

    @ParameterizedTest
    @MethodSource("parsingErrors")
    void shouldMapControlledParsingErrors(
            HttpStatus httpStatus,
            String code
    ) throws Exception {
        when(
                cvParsingService.parse(
                        RAW_CV_ID,
                        PUBLIC_OWNER_USER_ID
                )
        ).thenThrow(
                new CvParsingException(
                        httpStatus,
                        code,
                        publicMessage(code),
                        RAW_CV_ID
                )
        );

        mockMvc.perform(
                        post(
                                "/api/cvs/{rawCvId}/parse",
                                RAW_CV_ID
                        )
                )
                .andExpect(
                        status().is(httpStatus.value())
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(httpStatus.value())
                )
                .andExpect(
                        jsonPath("$.error")
                                .value(code)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(publicMessage(code))
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/api/cvs/"
                                                + RAW_CV_ID
                                                + "/parse"
                                )
                )
                .andExpect(
                        jsonPath("$.timestamp")
                                .exists()
                );
    }

    private static Stream<Arguments> parsingErrors() {
        return Stream.of(
                Arguments.of(
                        HttpStatus.BAD_REQUEST,
                        "RAW_CV_ID_REQUIRED"
                ),
                Arguments.of(
                        HttpStatus.FORBIDDEN,
                        "CV_ACCESS_DENIED"
                ),
                Arguments.of(
                        HttpStatus.NOT_FOUND,
                        "RAW_CV_NOT_FOUND"
                ),
                Arguments.of(
                        HttpStatus.CONFLICT,
                        "CV_PARSE_IN_PROGRESS"
                ),
                Arguments.of(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "CV_FILE_TOO_LARGE"
                ),
                Arguments.of(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "CV_TEXT_NOT_EXTRACTABLE"
                ),
                Arguments.of(
                        HttpStatus.BAD_GATEWAY,
                        "CV_PARSER_UNAVAILABLE"
                ),
                Arguments.of(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "CV_PARSER_TIMEOUT"
                )
        );
    }

    private String publicMessage(
            String code
    ) {
        return switch (code) {
            case "RAW_CV_ID_REQUIRED" ->
                    "Raw CV id is required";

            case "CV_ACCESS_DENIED" ->
                    "You do not have access to this CV";

            case "RAW_CV_NOT_FOUND" ->
                    "Raw CV was not found";

            case "CV_PARSE_IN_PROGRESS" ->
                    "CV parsing is already in progress";

            case "CV_FILE_TOO_LARGE" ->
                    "CV exceeds the parser size limit";

            case "CV_TEXT_NOT_EXTRACTABLE" ->
                    "CV does not contain extractable text";

            case "CV_PARSER_UNAVAILABLE" ->
                    "CV parser service is unavailable";

            case "CV_PARSER_TIMEOUT" ->
                    "CV parser service timed out";

            default ->
                    "CV processing failed";
        };
    }

    private CandidateProfile warehouseProfile() {
        return buildProfile(
                "profile-warehouse",
                "Trần Quốc Huy",
                "Warehouse Supervisor",
                "quoc.huy@example.com",
                "Warehouse operations supervisor.",
                "Lead a safe distribution center.",
                "Forklift Operation",
                CandidateProfile.SkillCategory.EQUIPMENT,
                "An Phat Logistics",
                "Warehouse Supervisor",
                CandidateProfile.EmploymentType.FULL_TIME,
                List.of(
                        "Reach Truck",
                        "Handheld Scanner"
                ),
                "Diploma in Logistics",
                CandidateProfile.EducationLevel.DIPLOMA,
                "Occupational Safety Training",
                "Forklift Operator License",
                8.5,
                CandidateProfile.Seniority.SUPERVISOR,
                CandidateProfile.DetectedLanguage.EN,
                0.91,
                List.of(
                        "MULTI_COLUMN_LAYOUT_SUSPECTED",
                        "TEXT_LAYOUT_MAY_BE_LOST"
                )
        );
    }

    private CandidateProfile nurseProfile() {
        return buildProfile(
                "profile-nurse",
                "Lê Thu Hà",
                "Registered Nurse",
                "thu.ha@example.com",
                "Registered nurse with acute care experience.",
                "Provide safe patient-centered care.",
                "Patient Care",
                CandidateProfile.SkillCategory.HEALTHCARE,
                "City General Hospital",
                "Registered Nurse",
                CandidateProfile.EmploymentType.SHIFT_WORK,
                List.of(
                        "Infusion Pump",
                        "Patient Monitor"
                ),
                "Bachelor of Nursing",
                CandidateProfile.EducationLevel.BACHELOR,
                "Basic Life Support",
                "Registered Nurse License",
                6.0,
                CandidateProfile.Seniority.SENIOR,
                CandidateProfile.DetectedLanguage.EN,
                0.94,
                List.of()
        );
    }

    private CandidateProfile accountantProfile() {
        return buildProfile(
                "profile-accountant",
                "Nguyễn Minh Anh",
                "Senior Accountant",
                "minh.anh@example.com",
                "Senior accountant with manufacturing experience.",
                "Lead financial reporting and control.",
                "Financial Reporting",
                CandidateProfile.SkillCategory.ACCOUNTING,
                "An Phat Manufacturing",
                "Senior Accountant",
                CandidateProfile.EmploymentType.FULL_TIME,
                List.of(),
                "Bachelor of Accounting",
                CandidateProfile.EducationLevel.BACHELOR,
                "Chief Accountant Certificate",
                "Accounting Practice License",
                9.5,
                CandidateProfile.Seniority.SENIOR,
                CandidateProfile.DetectedLanguage.VI,
                0.93,
                List.of()
        );
    }

    private CandidateProfile buildProfile(
            String profileId,
            String fullName,
            String headline,
            String email,
            String summary,
            String objective,
            String skillName,
            CandidateProfile.SkillCategory skillCategory,
            String companyName,
            String jobTitle,
            CandidateProfile.EmploymentType
                    employmentType,
            List<String> equipment,
            String degree,
            CandidateProfile.EducationLevel
                    educationLevel,
            String certificationName,
            String licenseName,
            double experienceYears,
            CandidateProfile.Seniority seniority,
            CandidateProfile.DetectedLanguage language,
            double overallScore,
            List<String> parserWarnings
    ) {
        Instant createdAt = Instant.parse(
                "2026-08-04T03:00:00Z"
        );

        CandidateProfile.ContactInformation contact =
                new CandidateProfile.ContactInformation(
                        email,
                        "+84901234567",
                        "Ho Chi Minh City, Vietnam",
                        "Ho Chi Minh City",
                        "Ho Chi Minh City",
                        "Vietnam",
                        "700000"
                );

        CandidateProfile.Skill skill =
                new CandidateProfile.Skill(
                        skillName,
                        skillName,
                        skillCategory,
                        "Advanced",
                        CandidateProfile
                                .ProficiencyLevel
                                .ADVANCED,
                        experienceYears,
                        "2026-07",
                        List.of(
                                "SKILLS",
                                "WORK_EXPERIENCE"
                        )
                );

        CandidateProfile.WorkExperience
                workExperience =
                new CandidateProfile.WorkExperience(
                        companyName,
                        industryFor(skillCategory),
                        jobTitle,
                        jobTitle,
                        employmentType,
                        "Ho Chi Minh City",
                        CandidateProfile.WorkMode.ONSITE,
                        "2021-01",
                        "2026-07",
                        false,
                        66,
                        summary,
                        List.of(
                                "Managed daily professional duties"
                        ),
                        List.of(
                                "Improved service quality"
                        ),
                        List.of(skillName),
                        List.of(
                                toolFor(skillCategory)
                        ),
                        equipment
                );

        CandidateProfile.ProjectExperience project =
                new CandidateProfile.ProjectExperience(
                        headline + " Improvement Project",
                        "Project Lead",
                        industryFor(skillCategory),
                        "2025-01",
                        "2025-06",
                        false,
                        "Improved operational quality.",
                        List.of(
                                "Analyzed current processes"
                        ),
                        List.of(
                                "Delivered measurable improvement"
                        ),
                        List.of(skillName),
                        List.of(
                                toolFor(skillCategory)
                        ),
                        equipment,
                        "8 people",
                        null,
                        null
                );

        CandidateProfile.Education education =
                new CandidateProfile.Education(
                        "National Professional University",
                        degree,
                        educationLevel,
                        fieldFor(skillCategory),
                        null,
                        "2012",
                        "2016",
                        false,
                        "Good",
                        List.of(),
                        degree
                );

        CandidateProfile.Certification certification =
                new CandidateProfile.Certification(
                        certificationName,
                        "Professional Authority",
                        "2024-01",
                        "2027-01",
                        false,
                        "CERTIFICATE-001",
                        null,
                        List.of(skillName)
                );

        CandidateProfile.LicenseEntry license =
                new CandidateProfile.LicenseEntry(
                        licenseName,
                        "Professional Authority",
                        "LICENSE-001",
                        "2024-01",
                        "2027-01",
                        false,
                        "Vietnam"
                );

        CandidateProfile.LanguageSkill
                languageSkill =
                new CandidateProfile.LanguageSkill(
                        "English",
                        "Intermediate",
                        CandidateProfile
                                .ProficiencyLevel
                                .INTERMEDIATE,
                        "CEFR",
                        "B1"
                );

        CandidateProfile.ParseQuality parseQuality =
                new CandidateProfile.ParseQuality(
                        overallScore,
                        1.0,
                        0.9,
                        0.92,
                        List.of(),
                        List.of()
                );

        return CandidateProfile.builder()
                .id(profileId)
                .rawCvId(RAW_CV_ID)
                .ownerUserId(PUBLIC_OWNER_USER_ID)
                .fullName(fullName)
                .headline(headline)
                .professionalSummary(summary)
                .careerObjective(objective)
                .contact(contact)
                .links(
                        List.of(
                                new CandidateProfile
                                        .LinkEntry(
                                        CandidateProfile
                                                .LinkType
                                                .LINKEDIN,
                                        "https://linkedin.com/in/candidate",
                                        "LinkedIn"
                                )
                        )
                )
                .targetJobTitles(
                        List.of(headline)
                )
                .targetIndustries(
                        List.of(
                                industryFor(skillCategory)
                        )
                )
                .preferredLocations(
                        List.of(
                                "Ho Chi Minh City"
                        )
                )
                .preferredWorkModes(
                        List.of(
                                CandidateProfile
                                        .WorkMode
                                        .ONSITE
                        )
                )
                .preferredEmploymentTypes(
                        List.of(employmentType)
                )
                .expectedSalaryText(
                        "Negotiable"
                )
                .availabilityText(
                        "Available in 30 days"
                )
                .skills(List.of(skill))
                .workExperiences(
                        List.of(workExperience)
                )
                .projects(List.of(project))
                .educations(List.of(education))
                .certifications(
                        List.of(certification)
                )
                .licenses(List.of(license))
                .languages(
                        List.of(languageSkill)
                )
                .awards(
                        List.of(
                                new CandidateProfile.Award(
                                        "Professional Excellence Award",
                                        companyName,
                                        "2025",
                                        "Recognized for performance"
                                )
                        )
                )
                .publications(
                        List.of(
                                new CandidateProfile.Publication(
                                        headline
                                                + " Best Practices",
                                        List.of(fullName),
                                        "Professional Journal",
                                        "2025-06",
                                        null,
                                        "Industry publication"
                                )
                        )
                )
                .volunteerExperiences(
                        List.of(
                                new CandidateProfile
                                        .VolunteerExperience(
                                        "Community Organization",
                                        "Volunteer",
                                        "2023",
                                        "2024",
                                        "Supported community activities",
                                        List.of(
                                                "Coordinated activities"
                                        ),
                                        List.of(skillName)
                                )
                        )
                )
                .activities(
                        List.of(
                                new CandidateProfile
                                        .ProfessionalActivity(
                                        "Professional Forum",
                                        "Industry Association",
                                        "Member",
                                        "2024",
                                        null,
                                        "Professional membership"
                                )
                        )
                )
                .trainingCourses(
                        List.of(
                                new CandidateProfile
                                        .TrainingCourse(
                                        skillName
                                                + " Training",
                                        "Professional Provider",
                                        "2025-11",
                                        "16 hours",
                                        "Professional training",
                                        List.of(skillName)
                                )
                        )
                )
                .interests(
                        List.of(
                                "Professional development"
                        )
                )
                .experienceYears(experienceYears)
                .seniority(seniority)
                .highestEducationLevel(
                        educationLevel
                )
                .recentJobTitles(
                        List.of(jobTitle)
                )
                .recentCompanies(
                        List.of(companyName)
                )
                .detectedLanguage(language)
                .rawText(
                        headline + " profile"
                )
                .sections(
                        List.of(
                                new CandidateProfile
                                        .ParsedSection(
                                        CandidateProfile
                                                .SectionType
                                                .WORK_EXPERIENCE,
                                        "Work Experience",
                                        0,
                                        10,
                                        "Experience"
                                )
                        )
                )
                .parserVersion("rule-v1")
                .parserWarnings(parserWarnings)
                .parseQuality(parseQuality)
                .sourceBucket("autojob-cvs")
                .sourceObjectKey(
                        "raw/2026/08/04/"
                                + RAW_CV_ID
                                + "/candidate.pdf"
                )
                .sourceOriginalFilename(
                        "candidate.pdf"
                )
                .sourceContentType(
                        "application/pdf"
                )
                .sourceSizeBytes(125_000L)
                .sourceSha256("sha256-value")
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }

    private String industryFor(
            CandidateProfile.SkillCategory category
    ) {
        return switch (category) {
            case ACCOUNTING ->
                    "Accounting";

            case HEALTHCARE ->
                    "Healthcare";

            case EQUIPMENT ->
                    "Logistics";

            default ->
                    "Professional Services";
        };
    }

    private String fieldFor(
            CandidateProfile.SkillCategory category
    ) {
        return switch (category) {
            case ACCOUNTING ->
                    "Accounting";

            case HEALTHCARE ->
                    "Nursing";

            case EQUIPMENT ->
                    "Logistics";

            default ->
                    "Management";
        };
    }

    private String toolFor(
            CandidateProfile.SkillCategory category
    ) {
        return switch (category) {
            case ACCOUNTING ->
                    "SAP";

            case HEALTHCARE ->
                    "Hospital Information System";

            case EQUIPMENT ->
                    "Warehouse Management System";

            default ->
                    "Microsoft Office";
        };
    }
}