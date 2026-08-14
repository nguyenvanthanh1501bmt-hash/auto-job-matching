from __future__ import annotations

import pytest

from app.config import Settings
from app.exceptions import CvTextNotExtractableError
from app.parsing.parse_quality_calculator import (
    ParseQualityCalculator,
)
from app.parsing.profile_parser import ProfileParser
from app.schemas import (
    ContactInformation,
    Education,
    ParsedSection,
    Skill,
    WorkExperience,
)
from app.taxonomy.taxonomy_loader import TaxonomyBundle


ACCOUNTANT_CV = """
ANNA MORGAN
Senior Accountant
anna.morgan@example.com | +65 8123 4567
https://www.linkedin.com/in/anna-morgan
Target Role: Senior Accountant
Target Industries: Accounting, Finance
Preferred Location: Ho Chi Minh City
Preferred Work Mode: Hybrid
Preferred Employment Type: Full-time
Expected Salary: 30-35 million VND
Availability: Available after 30 days

PROFESSIONAL SUMMARY
Accounting professional experienced in financial reporting,
tax accounting, auditing, budgeting and month-end closing.

CAREER OBJECTIVE
Seeking a senior accounting role in a manufacturing
or financial services organization.

SKILLS
Financial Reporting, Tax Accounting, Auditing,
Budgeting, Microsoft Excel, SAP

WORK EXPERIENCE
Senior Accountant | ABC Manufacturing | Ho Chi Minh City
01/2020 - 06/2024
- Prepared monthly and annual financial statements.
- Managed tax declarations and accounts payable.
- Reviewed accounts receivable and supported external audits.
- Reduced monthly closing time by 25 percent.
- Used Microsoft Excel and SAP for reconciliation.

EDUCATION
Bachelor of Accounting
University of Economics
2015 - 2019

CERTIFICATIONS
CPA
Issuer: Accounting Professionals Association
Issued: 2023-06
Expires: 2030-06

LANGUAGES
English - Fluent
""".strip()


def test_profile_parser_builds_complete_candidate_profile(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ProfileParser(
        settings=settings,
        taxonomy=taxonomy,
    )

    response = parser.parse(
        raw_cv_id="raw-profile-1",
        raw_text=ACCOUNTANT_CV,
        extraction_warnings=(
            "TEXT_LAYOUT_MAY_BE_LOST",
        ),
    )

    assert response.raw_cv_id == "raw-profile-1"

    assert (
            response.parser_version
            == settings.parser_version
    )

    assert (
            response.extracted_text_length
            == len(ACCOUNTANT_CV)
    )

    assert response.detected_language == "EN"

    profile = response.profile

    assert profile.full_name == "ANNA MORGAN"
    assert profile.headline == "Senior Accountant"

    assert profile.professional_summary is not None
    assert "financial reporting" in (
        profile.professional_summary.casefold()
    )

    assert profile.career_objective is not None

    assert profile.contact.email == (
        "anna.morgan@example.com"
    )

    assert profile.contact.phone == (
        "+6581234567"
    )

    assert profile.target_job_titles == [
        "Senior Accountant",
    ]

    assert profile.target_industries == [
        "Accounting",
        "Finance",
    ]

    assert profile.preferred_locations == [
        "Hồ Chí Minh",
    ]

    assert profile.preferred_work_modes == [
        "HYBRID",
    ]

    assert profile.preferred_employment_types == [
        "FULL_TIME",
    ]

    assert profile.expected_salary_text == (
        "30-35 million VND"
    )

    assert profile.availability_text == (
        "Available after 30 days"
    )

    skill_names = {
        skill.normalized_name
        for skill in profile.skills
    }

    assert {
               "Lập báo cáo tài chính",
               "Kế toán thuế",
               "Auditing",
               "Budgeting",
               "Microsoft Excel",
               "SAP",
           } <= skill_names

    assert len(profile.work_experiences) == 1

    work_experience = (
        profile.work_experiences[0]
    )

    assert (
            work_experience.job_title
            == "Senior Accountant"
    )

    assert (
            work_experience.normalized_job_title
            == "SENIOR_ACCOUNTANT"
    )

    assert (
            work_experience.company_name
            == "ABC Manufacturing"
    )

    assert (
            work_experience.start_date
            == "2020-01"
    )

    assert (
            work_experience.end_date
            == "2024-06"
    )

    assert work_experience.current is False

    assert any(
        "Reduced monthly closing"
        in achievement
        for achievement
        in work_experience.achievements
    )

    assert (
            "Microsoft Excel"
            in work_experience.tools
    )

    assert len(profile.educations) == 1

    assert (
            profile.educations[
                0
            ].normalized_degree_level
            == "BACHELOR"
    )

    assert (
            profile.highest_education_level
            == "BACHELOR"
    )

    assert len(profile.certifications) == 1
    assert profile.certifications[0].name == "CPA"

    assert profile.experience_years == 4.5
    assert profile.seniority == "SENIOR"

    assert profile.recent_job_titles == [
        "Senior Accountant",
    ]

    assert profile.recent_companies == [
        "ABC Manufacturing",
    ]

    assert profile.raw_text == ACCOUNTANT_CV

    section_types = {
        section.section_type
        for section in profile.sections
    }

    assert {
               "HEADER",
               "SUMMARY",
               "OBJECTIVE",
               "SKILLS",
               "WORK_EXPERIENCE",
               "EDUCATION",
               "CERTIFICATIONS",
               "LANGUAGES",
           } <= section_types

    assert all(
        section.text is None
        for section in profile.sections
    )

    assert all(
        0
        <= section.start_offset
        <= section.end_offset
        <= len(ACCOUNTANT_CV)
        for section in profile.sections
    )

    assert (
            "TEXT_LAYOUT_MAY_BE_LOST"
            in profile.parser_warnings
    )

    assert (
            response.warnings
            == profile.parser_warnings
    )

    assert (
            0.0
            <= profile.parse_quality.overall_score
            <= 1.0
    )

    assert (
            profile.parse_quality.overall_score
            >= 0.6
    )


def test_profile_parser_is_deterministic(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ProfileParser(
        settings=settings,
        taxonomy=taxonomy,
    )

    first = parser.parse(
        raw_cv_id="raw-profile-2",
        raw_text=ACCOUNTANT_CV,
        extraction_warnings=(),
    )

    second = parser.parse(
        raw_cv_id="raw-profile-2",
        raw_text=ACCOUNTANT_CV,
        extraction_warnings=(),
    )

    assert first.model_dump(
        by_alias=True
    ) == second.model_dump(
        by_alias=True
    )


def test_profile_response_serializes_with_camel_case_aliases(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ProfileParser(
        settings=settings,
        taxonomy=taxonomy,
    )

    response = parser.parse(
        raw_cv_id="raw-profile-3",
        raw_text=ACCOUNTANT_CV,
        extraction_warnings=(),
    )

    payload = response.model_dump(
        by_alias=True,
    )

    assert payload["rawCvId"] == "raw-profile-3"
    assert payload["parserVersion"] == "rule-v1"

    assert payload["extractedTextLength"] == (
        len(ACCOUNTANT_CV)
    )

    assert payload["detectedLanguage"] == "EN"

    profile = payload["profile"]

    assert profile["fullName"] == "ANNA MORGAN"

    assert (
            profile["professionalSummary"]
            is not None
    )

    assert profile["targetJobTitles"] == [
        "Senior Accountant",
    ]

    assert profile["preferredWorkModes"] == [
        "HYBRID",
    ]

    assert (
            profile["workExperiences"][0][
                "normalizedJobTitle"
            ]
            == "SENIOR_ACCOUNTANT"
    )

    assert (
            profile["educations"][0][
                "normalizedDegreeLevel"
            ]
            == "BACHELOR"
    )

    assert (
            profile["parseQuality"][
                "overallScore"
            ]
            >= 0.6
    )

    assert "raw_text" not in profile
    assert "rawText" in profile

    assert "work_experiences" not in profile
    assert "workExperiences" in profile


def test_profile_parser_rejects_blank_text(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ProfileParser(
        settings=settings,
        taxonomy=taxonomy,
    )

    with pytest.raises(
            CvTextNotExtractableError
    ) as captured:
        parser.parse(
            raw_cv_id="raw-empty-1",
            raw_text=" \n\t ",
            extraction_warnings=(),
        )

    assert (
            captured.value.code
            == "CV_TEXT_NOT_EXTRACTABLE"
    )

    assert (
            captured.value.raw_cv_id
            == "raw-empty-1"
    )


def test_parse_quality_is_high_for_well_structured_profile() -> None:
    calculator = ParseQualityCalculator()

    quality = calculator.calculate(
        raw_text=(
                "Accounting professional experience. "
                * 150
        ),
        sections=[
            ParsedSection(
                sectionType="SUMMARY",
                heading="SUMMARY",
                startOffset=0,
                endOffset=500,
                text=None,
            ),
            ParsedSection(
                sectionType="WORK_EXPERIENCE",
                heading="WORK EXPERIENCE",
                startOffset=500,
                endOffset=2_000,
                text=None,
            ),
            ParsedSection(
                sectionType="EDUCATION",
                heading="EDUCATION",
                startOffset=2_000,
                endOffset=3_000,
                text=None,
            ),
        ],
        full_name="Anna Morgan",
        headline="Senior Accountant",
        contact=ContactInformation(
            email="anna@example.com",
        ),
        skills=[
            Skill(
                name="Financial Reporting",
                normalizedName=(
                    "Financial Reporting"
                ),
                category="ACCOUNTING",
                evidenceSources=[
                    "SKILLS_SECTION",
                ],
            ),
        ],
        work_experiences=[
            WorkExperience(
                companyName="ABC Manufacturing",
                jobTitle="Senior Accountant",
                startDate="2020-01",
                endDate="2024-06",
                current=False,
                durationMonths=54,
                responsibilities=[
                    "Prepared financial statements",
                ],
                achievements=[
                    "Reduced closing time",
                ],
                skills=[
                    "Financial Reporting",
                ],
            ),
        ],
        educations=[
            Education(
                institutionName=(
                    "University of Economics"
                ),
                degree="Bachelor of Accounting",
                normalizedDegreeLevel="BACHELOR",
                fieldOfStudy="Accounting",
                startDate="2015",
                endDate="2019",
                current=False,
            ),
        ],
        parser_warnings=(),
    )

    assert quality.overall_score >= 0.75

    assert (
            quality.text_extraction_score
            >= 0.9
    )

    assert (
            quality.work_experience_score
            >= 0.8
    )

    assert (
            quality.missing_important_fields
            == []
    )

    assert quality.ambiguous_fields == []


def test_parse_quality_is_low_for_unstructured_profile() -> None:
    calculator = ParseQualityCalculator()

    quality = calculator.calculate(
        raw_text="x",
        sections=[],
        full_name=None,
        headline=None,
        contact=ContactInformation(),
        skills=[],
        work_experiences=[],
        educations=[],
        parser_warnings=(
            "CV_LANGUAGE_UNKNOWN",
            "TEXT_LAYOUT_MAY_BE_LOST",
        ),
    )

    assert quality.overall_score < 0.15

    assert set(
        quality.missing_important_fields
    ) == {
               "fullName",
               "headline",
               "contact",
               "skills",
               "workExperiences",
               "educations",
           }

    assert set(
        quality.ambiguous_fields
    ) == {
               "detectedLanguage",
               "rawText.layout",
           }