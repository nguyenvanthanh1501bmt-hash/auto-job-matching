from __future__ import annotations

from typing import Annotated, Literal

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StringConstraints,
    field_validator,
)


NonBlankString = Annotated[
    str,
    StringConstraints(strip_whitespace=True, min_length=1),
]

OptionalShortText = Annotated[
    str | None,
    StringConstraints(strip_whitespace=True, max_length=5_000),
]

CanonicalDate = Annotated[
    str | None,
    StringConstraints(
        strip_whitespace=True,
        pattern=r"^\d{4}(?:-(?:0[1-9]|1[0-2]))?$",
    ),
]


class StrictModel(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        populate_by_name=True,
        str_strip_whitespace=True,
    )


class ParseCvRequest(StrictModel):
    raw_cv_id: NonBlankString = Field(
        alias="rawCvId",
        max_length=100,
    )
    bucket: NonBlankString = Field(
        max_length=63,
    )
    object_key: NonBlankString = Field(
        alias="objectKey",
        max_length=1_024,
    )
    original_filename: NonBlankString = Field(
        alias="originalFilename",
        max_length=500,
    )
    content_type: NonBlankString = Field(
        alias="contentType",
        max_length=200,
    )

    @field_validator("object_key")
    @classmethod
    def reject_unsafe_object_key(cls, value: str) -> str:
        if value.startswith("/"):
            raise ValueError("objectKey must not start with '/'")

        if "\\" in value:
            raise ValueError("objectKey must not contain backslashes")

        segments = value.split("/")
        if any(segment in {"", ".", ".."} for segment in segments):
            raise ValueError(
                "objectKey contains an empty or unsafe path segment"
            )

        if any(ord(character) < 32 for character in value):
            raise ValueError("objectKey must not contain control characters")

        return value


class ContactInformation(StrictModel):
    email: OptionalShortText = None
    phone: OptionalShortText = None
    address_text: OptionalShortText = Field(
        default=None,
        alias="addressText",
    )
    city: OptionalShortText = None
    province_or_state: OptionalShortText = Field(
        default=None,
        alias="provinceOrState",
    )
    country: OptionalShortText = None
    postal_code: OptionalShortText = Field(
        default=None,
        alias="postalCode",
    )


class LinkEntry(StrictModel):
    type: Literal[
        "LINKEDIN",
        "GITHUB",
        "PORTFOLIO",
        "PERSONAL_WEBSITE",
        "BEHANCE",
        "DRIBBBLE",
        "STACK_OVERFLOW",
        "PUBLICATION",
        "SOCIAL_PROFILE",
        "OTHER",
    ]
    url: NonBlankString = Field(max_length=2_000)
    label: OptionalShortText = None


class Skill(StrictModel):
    name: NonBlankString = Field(max_length=300)
    normalized_name: NonBlankString = Field(
        alias="normalizedName",
        max_length=300,
    )
    category: Literal[
        "TECHNICAL",
        "SOFTWARE",
        "TOOL",
        "EQUIPMENT",
        "MACHINERY",
        "DOMAIN_KNOWLEDGE",
        "BUSINESS",
        "SALES",
        "MARKETING",
        "FINANCE",
        "ACCOUNTING",
        "HEALTHCARE",
        "EDUCATION",
        "ENGINEERING",
        "TRADE",
        "MANAGEMENT",
        "LEADERSHIP",
        "COMMUNICATION",
        "LANGUAGE",
        "SAFETY",
        "COMPLIANCE",
        "OTHER",
    ]
    proficiency_text: OptionalShortText = Field(
        default=None,
        alias="proficiencyText",
    )
    normalized_proficiency: Literal[
                                "BASIC",
                                "ELEMENTARY",
                                "INTERMEDIATE",
                                "UPPER_INTERMEDIATE",
                                "ADVANCED",
                                "FLUENT",
                                "NATIVE",
                                "UNKNOWN",
                            ] | None = Field(
        default=None,
        alias="normalizedProficiency",
    )
    years_of_experience: float | None = Field(
        default=None,
        alias="yearsOfExperience",
        ge=0,
        le=100,
    )
    last_used_date: CanonicalDate = Field(
        default=None,
        alias="lastUsedDate",
    )
    evidence_sources: list[str] = Field(
        default_factory=list,
        alias="evidenceSources",
        max_length=20,
    )


class WorkExperience(StrictModel):
    company_name: OptionalShortText = Field(
        default=None,
        alias="companyName",
    )
    company_industry: OptionalShortText = Field(
        default=None,
        alias="companyIndustry",
    )
    job_title: OptionalShortText = Field(
        default=None,
        alias="jobTitle",
    )
    normalized_job_title: OptionalShortText = Field(
        default=None,
        alias="normalizedJobTitle",
    )
    employment_type: Literal[
                         "FULL_TIME",
                         "PART_TIME",
                         "CONTRACT",
                         "TEMPORARY",
                         "INTERNSHIP",
                         "FREELANCE",
                         "SEASONAL",
                         "SHIFT_WORK",
                         "UNKNOWN",
                     ] | None = Field(
        default=None,
        alias="employmentType",
    )
    location: OptionalShortText = None
    work_mode: Literal[
                   "ONSITE",
                   "REMOTE",
                   "HYBRID",
                   "UNKNOWN",
               ] | None = Field(
        default=None,
        alias="workMode",
    )
    start_date: CanonicalDate = Field(
        default=None,
        alias="startDate",
    )
    end_date: CanonicalDate = Field(
        default=None,
        alias="endDate",
    )
    current: bool | None = None
    duration_months: int | None = Field(
        default=None,
        alias="durationMonths",
        ge=0,
        le=1_200,
    )
    description: OptionalShortText = None
    responsibilities: list[str] = Field(default_factory=list)
    achievements: list[str] = Field(default_factory=list)
    skills: list[str] = Field(default_factory=list)
    tools: list[str] = Field(default_factory=list)
    equipment: list[str] = Field(default_factory=list)


class ProjectExperience(StrictModel):
    name: OptionalShortText = None
    role: OptionalShortText = None
    domain: OptionalShortText = None
    start_date: CanonicalDate = Field(
        default=None,
        alias="startDate",
    )
    end_date: CanonicalDate = Field(
        default=None,
        alias="endDate",
    )
    current: bool | None = None
    description: OptionalShortText = None
    responsibilities: list[str] = Field(default_factory=list)
    achievements: list[str] = Field(default_factory=list)
    skills: list[str] = Field(default_factory=list)
    tools: list[str] = Field(default_factory=list)
    equipment: list[str] = Field(default_factory=list)
    team_size_text: OptionalShortText = Field(
        default=None,
        alias="teamSizeText",
    )
    project_url: OptionalShortText = Field(
        default=None,
        alias="projectUrl",
    )
    repository_url: OptionalShortText = Field(
        default=None,
        alias="repositoryUrl",
    )


class Education(StrictModel):
    institution_name: OptionalShortText = Field(
        default=None,
        alias="institutionName",
    )
    degree: OptionalShortText = None
    normalized_degree_level: Literal[
                                 "SECONDARY",
                                 "HIGH_SCHOOL",
                                 "VOCATIONAL",
                                 "CERTIFICATE",
                                 "DIPLOMA",
                                 "ASSOCIATE",
                                 "BACHELOR",
                                 "MASTER",
                                 "DOCTORATE",
                                 "PROFESSIONAL_DEGREE",
                                 "OTHER",
                                 "UNKNOWN",
                             ] | None = Field(
        default=None,
        alias="normalizedDegreeLevel",
    )
    field_of_study: OptionalShortText = Field(
        default=None,
        alias="fieldOfStudy",
    )
    specialization: OptionalShortText = None
    start_date: CanonicalDate = Field(
        default=None,
        alias="startDate",
    )
    end_date: CanonicalDate = Field(
        default=None,
        alias="endDate",
    )
    current: bool | None = None
    grade: OptionalShortText = None
    achievements: list[str] = Field(default_factory=list)
    description: OptionalShortText = None


class Certification(StrictModel):
    name: OptionalShortText = None
    issuer: OptionalShortText = None
    issued_date: CanonicalDate = Field(
        default=None,
        alias="issuedDate",
    )
    expiration_date: CanonicalDate = Field(
        default=None,
        alias="expirationDate",
    )
    expired: bool | None = None
    credential_id: OptionalShortText = Field(
        default=None,
        alias="credentialId",
    )
    credential_url: OptionalShortText = Field(
        default=None,
        alias="credentialUrl",
    )
    related_skills: list[str] = Field(
        default_factory=list,
        alias="relatedSkills",
    )


class LicenseEntry(StrictModel):
    name: OptionalShortText = None
    issuing_authority: OptionalShortText = Field(
        default=None,
        alias="issuingAuthority",
    )
    license_number: OptionalShortText = Field(
        default=None,
        alias="licenseNumber",
    )
    issued_date: CanonicalDate = Field(
        default=None,
        alias="issuedDate",
    )
    expiration_date: CanonicalDate = Field(
        default=None,
        alias="expirationDate",
    )
    expired: bool | None = None
    jurisdiction: OptionalShortText = None


class TrainingCourse(StrictModel):
    name: OptionalShortText = None
    provider: OptionalShortText = None
    completion_date: CanonicalDate = Field(
        default=None,
        alias="completionDate",
    )
    duration_text: OptionalShortText = Field(
        default=None,
        alias="durationText",
    )
    description: OptionalShortText = None
    related_skills: list[str] = Field(
        default_factory=list,
        alias="relatedSkills",
    )


class LanguageSkill(StrictModel):
    language: NonBlankString = Field(max_length=200)
    proficiency_text: OptionalShortText = Field(
        default=None,
        alias="proficiencyText",
    )
    normalized_proficiency: Literal[
        "BASIC",
        "ELEMENTARY",
        "INTERMEDIATE",
        "UPPER_INTERMEDIATE",
        "ADVANCED",
        "FLUENT",
        "NATIVE",
        "UNKNOWN",
    ] = Field(
        default="UNKNOWN",
        alias="normalizedProficiency",
    )
    framework: OptionalShortText = None
    score: OptionalShortText = None


class Award(StrictModel):
    name: OptionalShortText = None
    issuer: OptionalShortText = None
    awarded_date: CanonicalDate = Field(
        default=None,
        alias="awardedDate",
    )
    description: OptionalShortText = None


class Publication(StrictModel):
    title: OptionalShortText = None
    authors: list[str] = Field(default_factory=list)
    publisher: OptionalShortText = None
    published_date: CanonicalDate = Field(
        default=None,
        alias="publishedDate",
    )
    url: OptionalShortText = None
    description: OptionalShortText = None


class VolunteerExperience(StrictModel):
    organization_name: OptionalShortText = Field(
        default=None,
        alias="organizationName",
    )
    role: OptionalShortText = None
    start_date: CanonicalDate = Field(
        default=None,
        alias="startDate",
    )
    end_date: CanonicalDate = Field(
        default=None,
        alias="endDate",
    )
    description: OptionalShortText = None
    responsibilities: list[str] = Field(default_factory=list)
    skills: list[str] = Field(default_factory=list)


class ProfessionalActivity(StrictModel):
    name: OptionalShortText = None
    organization: OptionalShortText = None
    role: OptionalShortText = None
    start_date: CanonicalDate = Field(
        default=None,
        alias="startDate",
    )
    end_date: CanonicalDate = Field(
        default=None,
        alias="endDate",
    )
    description: OptionalShortText = None


class ParsedSection(StrictModel):
    section_type: Literal[
        "HEADER",
        "CONTACT",
        "SUMMARY",
        "OBJECTIVE",
        "SKILLS",
        "WORK_EXPERIENCE",
        "PROJECTS",
        "EDUCATION",
        "CERTIFICATIONS",
        "LICENSES",
        "TRAINING",
        "LANGUAGES",
        "AWARDS",
        "PUBLICATIONS",
        "VOLUNTEERING",
        "ACTIVITIES",
        "INTERESTS",
        "REFERENCES",
        "OTHER",
    ] = Field(alias="sectionType")
    heading: OptionalShortText = None
    start_offset: int = Field(
        alias="startOffset",
        ge=0,
    )
    end_offset: int = Field(
        alias="endOffset",
        ge=0,
    )
    text: str | None = Field(default=None, max_length=50_000)

    @field_validator("end_offset")
    @classmethod
    def validate_end_offset(
            cls,
            value: int,
            info,
    ) -> int:
        start_offset = info.data.get("start_offset")
        if start_offset is not None and value < start_offset:
            raise ValueError(
                "endOffset must be greater than or equal to startOffset"
            )
        return value


class ParseQuality(StrictModel):
    overall_score: float = Field(
        alias="overallScore",
        ge=0,
        le=1,
    )
    text_extraction_score: float = Field(
        alias="textExtractionScore",
        ge=0,
        le=1,
    )
    section_detection_score: float = Field(
        alias="sectionDetectionScore",
        ge=0,
        le=1,
    )
    work_experience_score: float = Field(
        alias="workExperienceScore",
        ge=0,
        le=1,
    )
    missing_important_fields: list[str] = Field(
        default_factory=list,
        alias="missingImportantFields",
    )
    ambiguous_fields: list[str] = Field(
        default_factory=list,
        alias="ambiguousFields",
    )


class CandidateProfilePayload(StrictModel):
    full_name: OptionalShortText = Field(
        default=None,
        alias="fullName",
    )
    headline: OptionalShortText = None
    professional_summary: OptionalShortText = Field(
        default=None,
        alias="professionalSummary",
    )
    career_objective: OptionalShortText = Field(
        default=None,
        alias="careerObjective",
    )

    contact: ContactInformation = Field(
        default_factory=ContactInformation,
    )
    links: list[LinkEntry] = Field(default_factory=list)

    target_job_titles: list[str] = Field(
        default_factory=list,
        alias="targetJobTitles",
    )
    target_industries: list[str] = Field(
        default_factory=list,
        alias="targetIndustries",
    )
    preferred_locations: list[str] = Field(
        default_factory=list,
        alias="preferredLocations",
    )
    preferred_work_modes: list[
        Literal["ONSITE", "REMOTE", "HYBRID", "UNKNOWN"]
    ] = Field(
        default_factory=list,
        alias="preferredWorkModes",
    )
    preferred_employment_types: list[
        Literal[
            "FULL_TIME",
            "PART_TIME",
            "CONTRACT",
            "TEMPORARY",
            "INTERNSHIP",
            "FREELANCE",
            "SEASONAL",
            "SHIFT_WORK",
            "UNKNOWN",
        ]
    ] = Field(
        default_factory=list,
        alias="preferredEmploymentTypes",
    )
    expected_salary_text: OptionalShortText = Field(
        default=None,
        alias="expectedSalaryText",
    )
    availability_text: OptionalShortText = Field(
        default=None,
        alias="availabilityText",
    )

    skills: list[Skill] = Field(default_factory=list)
    work_experiences: list[WorkExperience] = Field(
        default_factory=list,
        alias="workExperiences",
    )
    projects: list[ProjectExperience] = Field(default_factory=list)
    educations: list[Education] = Field(default_factory=list)
    certifications: list[Certification] = Field(default_factory=list)
    licenses: list[LicenseEntry] = Field(default_factory=list)
    languages: list[LanguageSkill] = Field(default_factory=list)
    awards: list[Award] = Field(default_factory=list)
    publications: list[Publication] = Field(default_factory=list)
    volunteer_experiences: list[VolunteerExperience] = Field(
        default_factory=list,
        alias="volunteerExperiences",
    )
    activities: list[ProfessionalActivity] = Field(default_factory=list)
    training_courses: list[TrainingCourse] = Field(
        default_factory=list,
        alias="trainingCourses",
    )
    interests: list[str] = Field(default_factory=list)

    experience_years: float | None = Field(
        default=None,
        alias="experienceYears",
        ge=0,
        le=100,
    )
    seniority: Literal[
        "INTERN",
        "TRAINEE",
        "FRESHER",
        "ENTRY_LEVEL",
        "JUNIOR",
        "MID",
        "SENIOR",
        "LEAD",
        "SUPERVISOR",
        "MANAGER",
        "HEAD",
        "DIRECTOR",
        "EXECUTIVE",
        "UNKNOWN",
    ] = "UNKNOWN"
    highest_education_level: Literal[
                                 "SECONDARY",
                                 "HIGH_SCHOOL",
                                 "VOCATIONAL",
                                 "CERTIFICATE",
                                 "DIPLOMA",
                                 "ASSOCIATE",
                                 "BACHELOR",
                                 "MASTER",
                                 "DOCTORATE",
                                 "PROFESSIONAL_DEGREE",
                                 "OTHER",
                                 "UNKNOWN",
                             ] | None = Field(
        default=None,
        alias="highestEducationLevel",
    )
    recent_job_titles: list[str] = Field(
        default_factory=list,
        alias="recentJobTitles",
    )
    recent_companies: list[str] = Field(
        default_factory=list,
        alias="recentCompanies",
    )

    raw_text: NonBlankString = Field(
        alias="rawText",
        max_length=2_000_000,
    )
    sections: list[ParsedSection] = Field(default_factory=list)
    parser_warnings: list[str] = Field(
        default_factory=list,
        alias="parserWarnings",
    )
    parse_quality: ParseQuality = Field(alias="parseQuality")


class ParseCvResponse(StrictModel):
    raw_cv_id: NonBlankString = Field(
        alias="rawCvId",
        max_length=100,
    )
    parser_version: NonBlankString = Field(
        alias="parserVersion",
        max_length=100,
    )
    extracted_text_length: int = Field(
        alias="extractedTextLength",
        ge=1,
        le=2_000_000,
    )
    detected_language: Literal[
        "VI",
        "EN",
        "MIXED",
        "UNKNOWN",
    ] = Field(alias="detectedLanguage")
    profile: CandidateProfilePayload
    warnings: list[str] = Field(default_factory=list)


class CvErrorResponse(StrictModel):
    code: NonBlankString
    message: NonBlankString
    raw_cv_id: str | None = Field(
        default=None,
        alias="rawCvId",
    )


class HealthResponse(StrictModel):
    status: Literal["UP"]


class ReadyResponse(StrictModel):
    status: Literal["UP", "DOWN"]
    parser_version: str = Field(alias="parserVersion")
    taxonomy_version: str | None = Field(
        default=None,
        alias="taxonomyVersion",
    )
    minio: Literal["UP", "DOWN"]
    doc_extractor: Literal["UP", "DOWN"]
    details: list[str] = Field(default_factory=list)