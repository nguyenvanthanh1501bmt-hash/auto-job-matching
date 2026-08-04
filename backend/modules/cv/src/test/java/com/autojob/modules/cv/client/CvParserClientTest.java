package com.autojob.modules.cv.client;

import com.autojob.modules.cv.client.dto.CvParseRequest;
import com.autojob.modules.cv.client.dto.CvParseResponse;
import com.autojob.modules.cv.config.CvParserProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvParserClientTest {

    private static final MediaType JSON =
            MediaType.get(
                    "application/json; charset=utf-8"
            );

    private ObjectMapper objectMapper;
    private CvParserProperties properties;
    private Call.Factory callFactory;
    private Call call;
    private HttpCvParserClient client;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();

        properties = new CvParserProperties();
        properties.setBaseUrl(
                "http://localhost:8003/"
        );
        properties.setConnectTimeout(
                Duration.ofSeconds(1)
        );
        properties.setResponseTimeout(
                Duration.ofSeconds(2)
        );
        properties.setExpectedVersion("rule-v1");
        properties.setMaxErrorLength(1_000);
        properties.setMaxResponseSizeBytes(
                16_777_216
        );

        callFactory = mock(Call.Factory.class);
        call = mock(Call.class);

        when(
                callFactory.newCall(
                        any(Request.class)
                )
        ).thenReturn(call);

        client = new HttpCvParserClient(
                callFactory,
                objectMapper,
                properties
        );
    }

    @Test
    void shouldSerializeRequestAndDeserializeFullNestedResponse()
            throws Exception {
        when(call.execute()).thenReturn(
                response(
                        200,
                        fullResponseJson()
                )
        );

        CvParseRequest request = request();
        CvParseResponse response =
                client.parse(request);

        assertThat(response.rawCvId())
                .isEqualTo("raw-cv-001");
        assertThat(response.parserVersion())
                .isEqualTo("rule-v1");
        assertThat(response.detectedLanguage())
                .isEqualTo(
                        CvParseResponse
                                .DetectedLanguage.VI
                );

        assertThat(
                response.profile().fullName()
        ).isEqualTo("Nguyễn Minh Anh");

        assertThat(
                response.profile().headline()
        ).isEqualTo("Senior Accountant");

        assertThat(
                response.profile()
                        .contact()
                        .email()
        ).isEqualTo("minh.anh@example.com");

        assertThat(response.profile().links())
                .singleElement()
                .satisfies(link -> {
                    assertThat(link.type())
                            .isEqualTo(
                                    CvParseResponse
                                            .LinkType
                                            .LINKEDIN
                            );
                    assertThat(link.url())
                            .contains("linkedin.com");
                });

        assertThat(response.profile().skills())
                .singleElement()
                .satisfies(skill -> {
                    assertThat(
                            skill.normalizedName()
                    ).isEqualTo(
                            "Financial Reporting"
                    );
                    assertThat(skill.category())
                            .isEqualTo(
                                    CvParseResponse
                                            .SkillCategory
                                            .ACCOUNTING
                            );
                    assertThat(
                            skill.yearsOfExperience()
                    ).isEqualTo(7.5);
                });

        assertThat(
                response.profile()
                        .workExperiences()
        )
                .singleElement()
                .satisfies(experience -> {
                    assertThat(
                            experience.companyName()
                    ).isEqualTo(
                            "An Phát Manufacturing"
                    );
                    assertThat(
                            experience.employmentType()
                    ).isEqualTo(
                            CvParseResponse
                                    .EmploymentType
                                    .FULL_TIME
                    );
                    assertThat(
                            experience.workMode()
                    ).isEqualTo(
                            CvParseResponse
                                    .WorkMode
                                    .ONSITE
                    );
                    assertThat(
                            experience.durationMonths()
                    ).isEqualTo(52);
                });

        assertThat(response.profile().projects())
                .singleElement()
                .satisfies(project ->
                        assertThat(project.domain())
                                .isEqualTo(
                                        "Finance Transformation"
                                )
                );

        assertThat(
                response.profile().educations()
        )
                .singleElement()
                .satisfies(education ->
                        assertThat(
                                education
                                        .normalizedDegreeLevel()
                        ).isEqualTo(
                                CvParseResponse
                                        .EducationLevel
                                        .BACHELOR
                        )
                );

        assertThat(
                response.profile()
                        .certifications()
        )
                .singleElement()
                .satisfies(certification ->
                        assertThat(
                                certification.name()
                        ).isEqualTo(
                                "Chief Accountant Certificate"
                        )
                );

        assertThat(response.profile().licenses())
                .singleElement()
                .satisfies(license ->
                        assertThat(
                                license.jurisdiction()
                        ).isEqualTo("Vietnam")
                );

        assertThat(
                response.profile()
                        .trainingCourses()
        )
                .singleElement()
                .satisfies(training ->
                        assertThat(
                                training.provider()
                        ).isEqualTo("VACPA")
                );

        assertThat(
                response.profile().languages()
        )
                .singleElement()
                .satisfies(language ->
                        assertThat(
                                language
                                        .normalizedProficiency()
                        ).isEqualTo(
                                CvParseResponse
                                        .ProficiencyLevel
                                        .UPPER_INTERMEDIATE
                        )
                );

        assertThat(
                response.profile().awards()
        ).hasSize(1);
        assertThat(
                response.profile().publications()
        ).hasSize(1);
        assertThat(
                response.profile()
                        .volunteerExperiences()
        ).hasSize(1);
        assertThat(
                response.profile().activities()
        ).hasSize(1);
        assertThat(
                response.profile().sections()
        ).hasSize(1);

        assertThat(
                response.profile()
                        .parseQuality()
                        .overallScore()
        ).isEqualTo(0.93);

        assertThat(response.warnings())
                .containsExactly(
                        "TEXT_LAYOUT_MAY_BE_LOST"
                );

        ArgumentCaptor<Request> requestCaptor =
                ArgumentCaptor.forClass(
                        Request.class
                );

        verify(callFactory).newCall(
                requestCaptor.capture()
        );

        Request httpRequest =
                requestCaptor.getValue();

        assertThat(httpRequest.method())
                .isEqualTo("POST");

        assertThat(
                httpRequest.url().toString()
        ).isEqualTo(
                "http://localhost:8003/api/v1/cv/parse"
        );

        assertThat(
                httpRequest.header("Accept")
        ).isEqualTo("application/json");

        Buffer requestBuffer = new Buffer();

        assertThat(httpRequest.body())
                .isNotNull();

        httpRequest.body().writeTo(
                requestBuffer
        );

        JsonNode requestJson =
                objectMapper.readTree(
                        requestBuffer.readUtf8()
                );

        assertThat(
                requestJson
                        .get("rawCvId")
                        .asText()
        ).isEqualTo("raw-cv-001");

        assertThat(
                requestJson
                        .get("bucket")
                        .asText()
        ).isEqualTo("autojob-cvs");

        assertThat(
                requestJson
                        .get("objectKey")
                        .asText()
        ).isEqualTo(
                "raw/2026/08/04/raw-cv-001/cv.pdf"
        );

        assertThat(
                requestJson
                        .get("originalFilename")
                        .asText()
        ).isEqualTo("cv.pdf");

        assertThat(
                requestJson
                        .get("contentType")
                        .asText()
        ).isEqualTo("application/pdf");
    }

    @Test
    void shouldParseControlledParserErrorResponse()
            throws Exception {
        when(call.execute()).thenReturn(
                response(
                        422,
                        """
                        {
                          "code": "CV_TEXT_NOT_EXTRACTABLE",
                          "message": "No extractable text was found",
                          "rawCvId": "raw-cv-001"
                        }
                        """
                )
        );

        assertThatThrownBy(
                () -> client.parse(request())
        )
                .isInstanceOf(
                        CvParserClientException.class
                )
                .satisfies(throwable -> {
                    CvParserClientException exception =
                            (CvParserClientException)
                                    throwable;

                    assertThat(
                            exception.getFailureType()
                    ).isEqualTo(
                            CvParserClientException
                                    .FailureType
                                    .HTTP_4XX
                    );

                    assertThat(
                            exception.getUpstreamStatus()
                    ).isEqualTo(422);

                    assertThat(
                            exception.getParserCode()
                    ).isEqualTo(
                            "CV_TEXT_NOT_EXTRACTABLE"
                    );

                    assertThat(
                            exception.getRawCvId()
                    ).isEqualTo("raw-cv-001");

                    assertThat(
                            exception.getMessage()
                    ).doesNotContain(
                            "No extractable text was found"
                    );
                });
    }

    @Test
    void shouldClassifyConnectionRefused()
            throws Exception {
        when(call.execute()).thenThrow(
                new ConnectException(
                        "Connection refused"
                )
        );

        assertFailureType(
                CvParserClientException
                        .FailureType
                        .CONNECTION_REFUSED
        );
    }

    @Test
    void shouldClassifyGenericConnectionFailure()
            throws Exception {
        when(call.execute()).thenThrow(
                new IOException(
                        "network unavailable"
                )
        );

        assertFailureType(
                CvParserClientException
                        .FailureType
                        .CONNECTION_FAILURE
        );
    }

    @Test
    void shouldClassifyConnectTimeout()
            throws Exception {
        when(call.execute()).thenThrow(
                new SocketTimeoutException(
                        "connect timed out"
                )
        );

        assertFailureType(
                CvParserClientException
                        .FailureType
                        .CONNECT_TIMEOUT
        );
    }

    @Test
    void shouldClassifyResponseTimeout()
            throws Exception {
        when(call.execute()).thenThrow(
                new SocketTimeoutException(
                        "timeout"
                )
        );

        assertFailureType(
                CvParserClientException
                        .FailureType
                        .RESPONSE_TIMEOUT
        );
    }

    @Test
    void shouldClassifyHttp4xxWithoutLeakingBody()
            throws Exception {
        when(call.execute()).thenReturn(
                response(
                        400,
                        "{\"code\":\"CV_INVALID_REQUEST\","
                                + "\"message\":\"private detail\","
                                + "\"rawCvId\":\"raw-cv-001\"}"
                )
        );

        assertThatThrownBy(
                () -> client.parse(request())
        )
                .isInstanceOf(
                        CvParserClientException.class
                )
                .satisfies(throwable -> {
                    CvParserClientException exception =
                            (CvParserClientException)
                                    throwable;

                    assertThat(
                            exception.getFailureType()
                    ).isEqualTo(
                            CvParserClientException
                                    .FailureType
                                    .HTTP_4XX
                    );

                    assertThat(
                            exception.getUpstreamStatus()
                    ).isEqualTo(400);

                    assertThat(
                            exception.getParserCode()
                    ).isEqualTo(
                            "CV_INVALID_REQUEST"
                    );

                    assertThat(
                            exception.getMessage()
                    ).doesNotContain(
                            "private detail"
                    );
                });
    }

    @Test
    void shouldClassifyHttp5xx()
            throws Exception {
        when(call.execute()).thenReturn(
                response(
                        500,
                        "{\"code\":\"CV_INTERNAL_ERROR\","
                                + "\"message\":\"failed\"}"
                )
        );

        assertThatThrownBy(
                () -> client.parse(request())
        )
                .isInstanceOf(
                        CvParserClientException.class
                )
                .satisfies(throwable -> {
                    CvParserClientException exception =
                            (CvParserClientException)
                                    throwable;

                    assertThat(
                            exception.getFailureType()
                    ).isEqualTo(
                            CvParserClientException
                                    .FailureType
                                    .HTTP_5XX
                    );

                    assertThat(
                            exception.getUpstreamStatus()
                    ).isEqualTo(500);

                    assertThat(
                            exception.getParserCode()
                    ).isEqualTo(
                            "CV_INTERNAL_ERROR"
                    );
                });
    }

    @Test
    void shouldRejectMalformedJson()
            throws Exception {
        when(call.execute()).thenReturn(
                response(200, "{not-json")
        );

        assertFailureType(
                CvParserClientException
                        .FailureType
                        .MALFORMED_JSON
        );
    }

    @Test
    void shouldRejectEmptyBody()
            throws Exception {
        when(call.execute()).thenReturn(
                response(200, "   ")
        );

        assertFailureType(
                CvParserClientException
                        .FailureType
                        .EMPTY_RESPONSE
        );
    }

    @Test
    void shouldRejectUnexpectedStatus()
            throws Exception {
        when(call.execute()).thenReturn(
                response(302, "redirect")
        );

        assertThatThrownBy(
                () -> client.parse(request())
        )
                .isInstanceOf(
                        CvParserClientException.class
                )
                .satisfies(throwable -> {
                    CvParserClientException exception =
                            (CvParserClientException)
                                    throwable;

                    assertThat(
                            exception.getFailureType()
                    ).isEqualTo(
                            CvParserClientException
                                    .FailureType
                                    .UNEXPECTED_STATUS
                    );

                    assertThat(
                            exception.getUpstreamStatus()
                    ).isEqualTo(302);
                });
    }

    private void assertFailureType(
            CvParserClientException.FailureType
                    expectedType
    ) {
        assertThatThrownBy(
                () -> client.parse(request())
        )
                .isInstanceOf(
                        CvParserClientException.class
                )
                .satisfies(throwable ->
                        assertThat(
                                ((CvParserClientException)
                                        throwable)
                                        .getFailureType()
                        ).isEqualTo(expectedType)
                );
    }

    private CvParseRequest request() {
        return new CvParseRequest(
                "raw-cv-001",
                "autojob-cvs",
                "raw/2026/08/04/raw-cv-001/cv.pdf",
                "cv.pdf",
                "application/pdf"
        );
    }

    private Response response(
            int status,
            String body
    ) {
        Request request = new Request.Builder()
                .url(
                        "http://localhost:8003"
                                + "/api/v1/cv/parse"
                )
                .build();

        return new Response.Builder()
                .request(request)
                .protocol(
                        okhttp3.Protocol.HTTP_1_1
                )
                .code(status)
                .message("test")
                .body(
                        ResponseBody.create(
                                body,
                                JSON
                        )
                )
                .build();
    }

    private String fullResponseJson() {
        return """
                {
                  "rawCvId": "raw-cv-001",
                  "parserVersion": "rule-v1",
                  "extractedTextLength": 25,
                  "detectedLanguage": "VI",
                  "profile": {
                    "fullName": "Nguyễn Minh Anh",
                    "headline": "Senior Accountant",
                    "professionalSummary": "Accountant with manufacturing experience.",
                    "careerObjective": "Lead financial control and reporting.",
                    "contact": {
                      "email": "minh.anh@example.com",
                      "phone": "+84901234567",
                      "addressText": "District 7, Ho Chi Minh City",
                      "city": "Ho Chi Minh City",
                      "provinceOrState": "Ho Chi Minh City",
                      "country": "Vietnam",
                      "postalCode": "700000"
                    },
                    "links": [{
                      "type": "LINKEDIN",
                      "url": "https://linkedin.com/in/minhanh",
                      "label": "LinkedIn"
                    }],
                    "targetJobTitles": ["Chief Accountant"],
                    "targetIndustries": ["Manufacturing"],
                    "preferredLocations": ["Ho Chi Minh City"],
                    "preferredWorkModes": ["ONSITE"],
                    "preferredEmploymentTypes": ["FULL_TIME"],
                    "expectedSalaryText": "35,000,000 VND",
                    "availabilityText": "30 days",
                    "skills": [{
                      "name": "Financial Reporting",
                      "normalizedName": "Financial Reporting",
                      "category": "ACCOUNTING",
                      "proficiencyText": "Advanced",
                      "normalizedProficiency": "ADVANCED",
                      "yearsOfExperience": 7.5,
                      "lastUsedDate": "2026-07",
                      "evidenceSources": ["SKILLS", "WORK_EXPERIENCE"]
                    }],
                    "workExperiences": [{
                      "companyName": "An Phát Manufacturing",
                      "companyIndustry": "Manufacturing",
                      "jobTitle": "Senior Accountant",
                      "normalizedJobTitle": "Senior Accountant",
                      "employmentType": "FULL_TIME",
                      "location": "Ho Chi Minh City",
                      "workMode": "ONSITE",
                      "startDate": "2022-03",
                      "endDate": "2026-07",
                      "current": false,
                      "durationMonths": 52,
                      "description": "Owned month-end close.",
                      "responsibilities": ["Prepared financial statements"],
                      "achievements": ["Reduced closing time by 30%"],
                      "skills": ["Financial Reporting"],
                      "tools": ["SAP"],
                      "equipment": []
                    }],
                    "projects": [{
                      "name": "ERP Finance Migration",
                      "role": "Finance Lead",
                      "domain": "Finance Transformation",
                      "startDate": "2024-01",
                      "endDate": "2024-09",
                      "current": false,
                      "description": "Migrated finance processes.",
                      "responsibilities": ["Mapped chart of accounts"],
                      "achievements": ["Completed cutover on schedule"],
                      "skills": ["Accounting"],
                      "tools": ["SAP S/4HANA"],
                      "equipment": [],
                      "teamSizeText": "12 people",
                      "projectUrl": "https://example.com/project",
                      "repositoryUrl": null
                    }],
                    "educations": [{
                      "institutionName": "University of Economics HCMC",
                      "degree": "Bachelor of Accounting",
                      "normalizedDegreeLevel": "BACHELOR",
                      "fieldOfStudy": "Accounting",
                      "specialization": "Auditing",
                      "startDate": "2012",
                      "endDate": "2016",
                      "current": false,
                      "grade": "Good",
                      "achievements": ["Scholarship"],
                      "description": "Accounting program"
                    }],
                    "certifications": [{
                      "name": "Chief Accountant Certificate",
                      "issuer": "Ministry of Finance",
                      "issuedDate": "2020-05",
                      "expirationDate": null,
                      "expired": false,
                      "credentialId": "CERT-001",
                      "credentialUrl": "https://example.com/cert",
                      "relatedSkills": ["Accounting"]
                    }],
                    "licenses": [{
                      "name": "Accounting Practice License",
                      "issuingAuthority": "Ministry of Finance",
                      "licenseNumber": "LICENSE-001",
                      "issuedDate": "2021",
                      "expirationDate": "2027",
                      "expired": false,
                      "jurisdiction": "Vietnam"
                    }],
                    "languages": [{
                      "language": "English",
                      "proficiencyText": "IELTS 6.5",
                      "normalizedProficiency": "UPPER_INTERMEDIATE",
                      "framework": "IELTS",
                      "score": "6.5"
                    }],
                    "awards": [{
                      "name": "Employee of the Year",
                      "issuer": "An Phát Manufacturing",
                      "awardedDate": "2025",
                      "description": "Finance excellence"
                    }],
                    "publications": [{
                      "title": "Manufacturing Cost Control",
                      "authors": ["Nguyễn Minh Anh"],
                      "publisher": "Finance Journal",
                      "publishedDate": "2024-06",
                      "url": "https://example.com/publication",
                      "description": "Cost control article"
                    }],
                    "volunteerExperiences": [{
                      "organizationName": "Community Tax Clinic",
                      "role": "Volunteer Accountant",
                      "startDate": "2023",
                      "endDate": "2024",
                      "description": "Supported personal tax filing.",
                      "responsibilities": ["Reviewed tax documents"],
                      "skills": ["Tax Accounting"]
                    }],
                    "activities": [{
                      "name": "VACPA Member",
                      "organization": "VACPA",
                      "role": "Member",
                      "startDate": "2021",
                      "endDate": null,
                      "description": "Professional membership"
                    }],
                    "trainingCourses": [{
                      "name": "IFRS Update",
                      "provider": "VACPA",
                      "completionDate": "2025-11",
                      "durationText": "16 hours",
                      "description": "IFRS updates",
                      "relatedSkills": ["IFRS"]
                    }],
                    "interests": ["Financial literacy volunteering"],
                    "experienceYears": 9.5,
                    "seniority": "SENIOR",
                    "highestEducationLevel": "BACHELOR",
                    "recentJobTitles": ["Senior Accountant"],
                    "recentCompanies": ["An Phát Manufacturing"],
                    "rawText": "Senior Accountant profile",
                    "sections": [{
                      "sectionType": "HEADER",
                      "heading": "Profile",
                      "startOffset": 0,
                      "endOffset": 25,
                      "text": "Senior Accountant profile"
                    }],
                    "parserWarnings": ["TEXT_LAYOUT_MAY_BE_LOST"],
                    "parseQuality": {
                      "overallScore": 0.93,
                      "textExtractionScore": 1.0,
                      "sectionDetectionScore": 0.9,
                      "workExperienceScore": 0.88,
                      "missingImportantFields": [],
                      "ambiguousFields": ["expectedSalaryText"]
                    }
                  },
                  "warnings": ["TEXT_LAYOUT_MAY_BE_LOST"]
                }
                """;
    }
}