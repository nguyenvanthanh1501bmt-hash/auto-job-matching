from __future__ import annotations

import pytest

from app.config import Settings
from app.parsing.skill_parser import SkillParser
from app.taxonomy.taxonomy_loader import TaxonomyBundle


@pytest.mark.parametrize(
    (
            "skills_text",
            "expected_names",
    ),
    [
        (
                "Financial Reporting, Tax Accounting, Auditing, Microsoft Excel",
                {
                    "Financial Reporting",
                    "Tax Accounting",
                    "Auditing",
                    "Microsoft Excel",
                },
        ),
        (
                "Lead Generation, B2B Sales, Account Management, Negotiation",
                {
                    "Lead Generation",
                    "B2B Sales",
                    "Account Management",
                    "Negotiation",
                },
        ),
        (
                "Inventory Management, Warehouse Management, Forklift, Barcode Scanner",
                {
                    "Inventory Management",
                    "Warehouse Management",
                    "Forklift Operation",
                    "Barcode Scanner",
                },
        ),
        (
                "Patient Care, Clinical Assessment, Infection Control, Medical Records",
                {
                    "Patient Care",
                    "Clinical Assessment",
                    "Infection Control",
                    "Medical Records",
                },
        ),
        (
                "Lesson Planning, Classroom Management, Student Assessment",
                {
                    "Lesson Planning",
                    "Classroom Management",
                    "Student Assessment",
                },
        ),
        (
                "Welding, Electrical Wiring, Equipment Repair, Occupational Safety",
                {
                    "Welding",
                    "Electrical Wiring",
                    "Equipment Repair",
                    "Occupational Safety",
                },
        ),
    ],
)
def test_extracts_skills_from_multiple_industries(
        settings: Settings,
        taxonomy: TaxonomyBundle,
        skills_text: str,
        expected_names: set[str],
) -> None:
    parser = SkillParser(
        settings,
        taxonomy,
    )

    result = parser.parse(
        skills_text,
        {
            "SKILLS": (skills_text,),
        },
    )

    actual_names = {
        skill.normalized_name
        for skill in result.skills
    }

    assert expected_names <= actual_names


def test_java_does_not_match_inside_javascript(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings,
        taxonomy,
    )

    text = """
    JavaScript
    React
    Frontend application development
    """

    result = parser.parse(
        text,
        {
            "SKILLS": (text,),
        },
    )

    names = {
        skill.normalized_name
        for skill in result.skills
    }

    assert "JavaScript" in names
    assert "Java" not in names


def test_short_alias_does_not_match_unrelated_word(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings,
        taxonomy,
    )

    text = """
    Sapphire reporting platform
    Customer communication
    Career planning
    """

    result = parser.parse(
        text,
        {
            "SKILLS": (text,),
        },
    )

    names = {
        skill.normalized_name
        for skill in result.skills
    }

    assert "SAP" not in names


def test_extracts_proficiency_without_inventing_experience_years(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings,
        taxonomy,
    )

    text = """
    Microsoft Excel - Advanced
    SAP
    """

    result = parser.parse(
        text,
        {
            "SKILLS": (text,),
        },
    )

    skills_by_name = {
        skill.normalized_name: skill
        for skill in result.skills
    }

    excel = skills_by_name[
        "Microsoft Excel"
    ]

    assert excel.proficiency_text is not None

    assert (
            excel.normalized_proficiency
            == "ADVANCED"
    )

    assert excel.years_of_experience is None
    assert excel.last_used_date is None


def test_preserves_unknown_skill_from_explicit_skills_section(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings,
        taxonomy,
    )

    text = """
    Revenue Operations Enablement
    Inventory Management
    """

    result = parser.parse(
        text,
        {
            "SKILLS": (text,),
        },
    )

    skills_by_name = {
        skill.name: skill
        for skill in result.skills
    }

    assert (
            "Revenue Operations Enablement"
            in skills_by_name
    )

    assert (
            skills_by_name[
                "Revenue Operations Enablement"
            ].category
            == "OTHER"
    )


def test_merges_evidence_sources_without_duplicate_skill(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings,
        taxonomy,
    )

    result = parser.parse(
        """
        SKILLS
        Inventory Management

        WORK EXPERIENCE
        Managed inventory management processes.
        """,
        {
            "SKILLS": (
                "Inventory Management",
            ),
            "WORK_EXPERIENCE": (
                "Managed inventory management processes.",
            ),
        },
    )

    inventory_skills = [
        skill
        for skill in result.skills
        if (
                skill.normalized_name
                == "Inventory Management"
        )
    ]

    assert len(inventory_skills) == 1

    assert set(
        inventory_skills[0].evidence_sources
    ) == {
               "SKILLS_SECTION",
               "WORK_EXPERIENCE",
           }


def test_skill_ordering_is_deterministic(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings,
        taxonomy,
    )

    text = """
    Patient Care, Microsoft Excel, Inventory Management,
    Welding, Financial Reporting, Customer Service
    """

    first = parser.parse(
        text,
        {
            "SKILLS": (text,),
        },
    )

    second = parser.parse(
        text,
        {
            "SKILLS": (text,),
        },
    )

    first_values = [
        (
            skill.normalized_name,
            skill.category,
        )
        for skill in first.skills
    ]

    second_values = [
        (
            skill.normalized_name,
            skill.category,
        )
        for skill in second.skills
    ]

    assert first_values == second_values