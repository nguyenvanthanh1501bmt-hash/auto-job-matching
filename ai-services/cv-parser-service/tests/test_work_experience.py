from __future__ import annotations

import pytest

from app.config import Settings
from app.parsing.experience_calculator import ExperienceCalculator
from app.parsing.skill_parser import SkillParser
from app.parsing.work_experience_parser import WorkExperienceParser
from app.schemas import WorkExperience
from app.taxonomy.taxonomy_loader import TaxonomyBundle


def _create_parser(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> WorkExperienceParser:
    skill_parser = SkillParser(
        settings,
        taxonomy,
    )

    return WorkExperienceParser(
        settings,
        taxonomy,
        skill_parser,
    )


def test_parses_multiple_work_experiences(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = _create_parser(
        settings,
        taxonomy,
    )

    text = """
    Senior Accountant | ABC Manufacturing | Ho Chi Minh City
    01/2021 - Present
    - Prepared monthly and annual financial statements.
    - Managed tax declarations and accounts payable.
    - Reduced monthly closing time by 25 percent.

    General Accountant | XYZ Trading
    06/2018 - 12/2020
    - Maintained the general ledger and reconciled accounts.
    - Supported external auditing activities.
    """

    result = parser.parse(
        {
            "WORK_EXPERIENCE": (text,),
        }
    )

    assert len(
        result.work_experiences
    ) == 2

    first = result.work_experiences[0]
    second = result.work_experiences[1]

    assert first.job_title == "Senior Accountant"
    assert first.normalized_job_title == "SENIOR_ACCOUNTANT"
    assert first.company_name == "ABC Manufacturing"
    assert first.location == "Ho Chi Minh City"
    assert first.start_date == "2021-01"
    assert first.end_date is None
    assert first.current is True
    assert first.duration_months is not None

    assert any(
        "Prepared monthly"
        in responsibility
        for responsibility in first.responsibilities
    )

    assert any(
        "Reduced monthly closing"
        in achievement
        for achievement in first.achievements
    )

    assert second.job_title == "General Accountant"
    assert second.normalized_job_title == "ACCOUNTANT"
    assert second.company_name == "XYZ Trading"
    assert second.start_date == "2018-06"
    assert second.end_date == "2020-12"
    assert second.current is False


@pytest.mark.parametrize(
    (
            "job_title",
            "company_name",
            "expected_normalized_title",
    ),
    [
        (
                "Registered Nurse",
                "City General Hospital",
                "REGISTERED_NURSE",
        ),
        (
                "English Teacher",
                "Sunrise Primary School",
                "ENGLISH_TEACHER",
        ),
        (
                "Mechanical Technician",
                "ABC Factory",
                "MECHANICAL_TECHNICIAN",
        ),
        (
                "Warehouse Supervisor",
                "Metro Logistics",
                "WAREHOUSE_SUPERVISOR",
        ),
    ],
)
def test_normalizes_job_titles_across_industries(
        settings: Settings,
        taxonomy: TaxonomyBundle,
        job_title: str,
        company_name: str,
        expected_normalized_title: str,
) -> None:
    parser = _create_parser(
        settings,
        taxonomy,
    )

    text = f"""
    {job_title} | {company_name}
    January 2021 - June 2024
    - Performed professional duties for the assigned role.
    """

    result = parser.parse(
        {
            "WORK_EXPERIENCE": (text,),
        }
    )

    assert len(
        result.work_experiences
    ) == 1

    experience = (
        result.work_experiences[0]
    )

    assert experience.job_title == job_title

    assert (
            experience.normalized_job_title
            == expected_normalized_title
    )

    assert (
            experience.company_name
            == company_name
    )

    assert experience.start_date == "2021-01"
    assert experience.end_date == "2024-06"


def test_parses_employment_type_and_work_mode(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = _create_parser(
        settings,
        taxonomy,
    )

    text = """
    Customer Service Representative | ABC Services
    01/2022 - 12/2024
    Employment Type: Full-time
    Work Mode: Hybrid
    - Responded to customer enquiries.
    - Resolved customer complaints.
    """

    result = parser.parse(
        {
            "WORK_EXPERIENCE": (text,),
        }
    )

    experience = (
        result.work_experiences[0]
    )

    assert (
            experience.employment_type
            == "FULL_TIME"
    )

    assert experience.work_mode == "HYBRID"


def test_scopes_skills_tools_and_equipment_to_entry(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = _create_parser(
        settings,
        taxonomy,
    )

    text = """
    Warehouse Supervisor | Global Distribution Center
    03/2021 - Present
    - Managed inventory management and order fulfillment.
    - Prepared reports using Microsoft Excel.
    - Operated forklifts and barcode scanners safely.
    """

    result = parser.parse(
        {
            "WORK_EXPERIENCE": (text,),
        }
    )

    experience = (
        result.work_experiences[0]
    )

    assert (
            "Inventory Management"
            in experience.skills
    )

    assert (
            "Order Fulfillment"
            in experience.skills
    )

    assert (
            "Microsoft Excel"
            in experience.tools
    )

    assert (
            "Forklift Operation"
            in experience.equipment
    )

    assert (
            "Barcode Scanner"
            in experience.equipment
    )


def test_does_not_invent_missing_company_or_title(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = _create_parser(
        settings,
        taxonomy,
    )

    text = """
    2022 - 2023
    - Supported daily business operations.
    - Prepared weekly reports.
    """

    result = parser.parse(
        {
            "WORK_EXPERIENCE": (text,),
        }
    )

    assert len(
        result.work_experiences
    ) == 1

    experience = (
        result.work_experiences[0]
    )

    assert experience.company_name is None
    assert experience.job_title is None
    assert experience.normalized_job_title is None

    assert (
            "WORK_EXPERIENCE_PARTIALLY_PARSED"
            in result.warnings
    )


def test_merges_overlapping_experience_periods() -> None:
    calculator = ExperienceCalculator()

    result = calculator.calculate(
        [
            WorkExperience(
                companyName="Company A",
                jobTitle="Accountant",
                startDate="2020-01",
                endDate="2022-12",
                current=False,
            ),
            WorkExperience(
                companyName="Company B",
                jobTitle="Senior Accountant",
                startDate="2022-06",
                endDate="2024-06",
                current=False,
            ),
        ],
        raw_text="",
    )

    assert result.experience_years == 4.5
    assert result.warnings == ()


def test_falls_back_to_explicit_experience_statement() -> None:
    calculator = ExperienceCalculator()

    result = calculator.calculate(
        [],
        raw_text=(
            "Accounting professional with more than "
            "5 years of experience in financial reporting."
        ),
    )

    assert result.experience_years == 5.0

    assert result.warnings == (
        "EXPERIENCE_YEARS_INFERRED_WITHOUT_STRUCTURED_HISTORY",
    )


def test_warns_for_reverse_or_ambiguous_date_range(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = _create_parser(
        settings,
        taxonomy,
    )

    text = """
    Sales Representative | ABC Trading
    01/2024 - 01/2022
    - Managed customer accounts.
    """

    result = parser.parse(
        {
            "WORK_EXPERIENCE": (text,),
        }
    )

    assert len(
        result.work_experiences
    ) == 1

    assert (
            result.work_experiences[
                0
            ].duration_months
            is None
    )

    assert (
            "AMBIGUOUS_WORK_EXPERIENCE_DATE"
            in result.warnings
    )


def test_separates_responsibilities_from_achievements(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = _create_parser(
        settings,
        taxonomy,
    )

    text = """
    Sales Representative | Northstar Distribution
    January 2020 - December 2023
    - Managed B2B customer accounts.
    - Prepared weekly sales reports.
    - Increased regional sales by 30 percent.
    - Exceeded the annual sales target.
    """

    result = parser.parse(
        {
            "WORK_EXPERIENCE": (text,),
        }
    )

    experience = (
        result.work_experiences[0]
    )

    assert any(
        "Managed B2B"
        in value
        for value in experience.responsibilities
    )

    assert any(
        "Prepared weekly"
        in value
        for value in experience.responsibilities
    )

    assert any(
        "Increased regional sales"
        in value
        for value in experience.achievements
    )

    assert any(
        "Exceeded the annual"
        in value
        for value in experience.achievements
    )

@pytest.mark.parametrize(
    (
            "job_title",
            "expected_normalized_title",
    ),
    [
        (
                "Trợ lý giám đốc",
                "EXECUTIVE_ASSISTANT",
        ),
        (
                "Tech Lead",
                "TECHNICAL_LEAD",
        ),
        (
                "Chief Technology Officer",
                "CHIEF_TECHNOLOGY_OFFICER",
        ),
        (
                "DevOps Engineer",
                "DEVOPS_ENGINEER",
        ),
        (
                "Product Owner",
                "PRODUCT_OWNER",
        ),
        (
                "Business Analyst",
                "BUSINESS_ANALYST",
        ),
        (
                "Customer Service Specialist",
                "CUSTOMER_SERVICE_SPECIALIST",
        ),
        (
                "Customer Service Team Leader",
                "CUSTOMER_SERVICE_SUPERVISOR",
        ),
        (
                "Customer Success Manager",
                "CUSTOMER_SUCCESS_MANAGER",
        ),
        (
                "Call Center Agent",
                "CALL_CENTER_AGENT",
        ),
        (
                "Key Account Manager",
                "KEY_ACCOUNT_MANAGER",
        ),
        (
                "Giám đốc kinh doanh",
                "SALES_DIRECTOR",
        ),
    ],
)
def test_normalizes_expanded_job_title_taxonomy(
        settings: Settings,
        taxonomy: TaxonomyBundle,
        job_title: str,
        expected_normalized_title: str,
) -> None:
    parser = _create_parser(
        settings,
        taxonomy,
    )

    text = f"""
    {job_title} | Example Company
    January 2023 - December 2024
    - Performed the responsibilities assigned to the role.
    """

    result = parser.parse(
        {
            "WORK_EXPERIENCE": (
                text,
            ),
        }
    )

    assert len(
        result.work_experiences
    ) == 1

    experience = (
        result.work_experiences[0]
    )

    assert experience.job_title == job_title

    assert (
            experience.normalized_job_title
            == expected_normalized_title
    )

    assert (
            experience.company_name
            == "Example Company"
    )