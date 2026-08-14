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
                    "Lập báo cáo tài chính",
                    "Kế toán thuế",
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
                    "Chăm sóc bệnh nhân",
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


def test_strips_english_skill_group_prefixes_and_classifies_cv_stack(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings,
        taxonomy,
    )

    text = """
    Languages: JavaScript, TypeScript, Java, HTML, CSS
    Frameworks: React, Next.js, Express, Tailwind CSS
    Databases: MongoDB, SQL Server, PostgreSQL, Supabase
    Tools: Git, GitHub, Postman, Notion, Draw.io, Figma
    Soft Skills: Problem Solving, Critical Thinking, Communication, Teamwork
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

    expected_names = {
        "JavaScript",
        "TypeScript",
        "Java",
        "HTML",
        "CSS",
        "React",
        "Next.js",
        "Express",
        "Tailwind CSS",
        "MongoDB",
        "SQL Server",
        "PostgreSQL",
        "Supabase",
        "Git",
        "GitHub",
        "Postman",
        "Notion",
        "Draw.io",
        "Figma",
        "Problem Solving",
        "Critical Thinking",
        "Communication",
        "Teamwork",
    }

    assert set(skills_by_name) == expected_names

    assert not any(
        ":" in skill.name
        for skill in result.skills
    )

    assert (
            skills_by_name["TypeScript"].category
            == "TECHNICAL"
    )

    assert (
            skills_by_name["Postman"].category
            == "TOOL"
    )

    assert (
            skills_by_name["Teamwork"].category
            == "COMMUNICATION"
    )

    assert (
            "SKILL_TAXONOMY_MATCH_LOW"
            not in result.warnings
    )


def test_strips_vietnamese_and_unaccented_skill_group_prefixes(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings,
        taxonomy,
    )

    text = """
    Ngôn ngữ lập trình: JavaScript, TypeScript
    Thư viện và framework: React, Next.js
    Cơ sở dữ liệu: MongoDB, PostgreSQL
    Cong cu phat trien: Git, Postman
    Kỹ năng mềm: Giao tiếp, Làm việc nhóm
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

    assert {
               "JavaScript",
               "TypeScript",
               "React",
               "Next.js",
               "MongoDB",
               "PostgreSQL",
               "Git",
               "Postman",
               "Communication",
               "Teamwork",
           } <= names

    assert not any(
        ":" in skill.name
        for skill in result.skills
    )


def test_does_not_strip_non_label_text_starting_with_languages(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings,
        taxonomy,
    )

    text = "Languages for distributed systems"

    result = parser.parse(
        text,
        {
            "SKILLS": (text,),
        },
    )

    assert any(
        skill.name == text
        for skill in result.skills
    )


def test_prefers_specific_taxonomy_skill_over_contained_generic_skill(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings,
        taxonomy,
    )

    text = "SQL Server"

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

    assert "SQL Server" in names
    assert "SQL" not in names


def test_keeps_standalone_generic_skill_after_specific_skill(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings,
        taxonomy,
    )

    text = "SQL Server, SQL"

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

    assert {
               "SQL Server",
               "SQL",
           } <= names
