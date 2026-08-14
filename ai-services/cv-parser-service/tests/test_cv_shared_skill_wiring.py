from __future__ import annotations

from app.config import Settings
from app.parsing.skill_parser import (
    SkillParser,
)
from app.taxonomy.taxonomy_loader import (
    TaxonomyBundle,
)


def test_cv_parser_uses_shared_skill_canonicals(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings=settings,
        taxonomy=taxonomy,
    )

    text = """
    Amazon Web Services
    Tax Accounting
    Financial Reporting
    Brand Management
    Patient Care
    Computer Numerical Control
    Reading Technical Drawings
    Supply Chain Management
    Training Delivery
    Front Office Operations
    """

    result = parser.parse(
        raw_text=text,
        section_texts={
            "SKILLS": (
                text,
            ),
        },
    )

    names = {
        skill.normalized_name
        for skill in result.skills
    }

    assert "AWS" in names

    assert (
            "Kế toán thuế"
            in names
    )

    assert (
            "Lập báo cáo tài chính"
            in names
    )

    assert (
            "Branding"
            in names
    )

    assert (
            "Chăm sóc bệnh nhân"
            in names
    )

    assert (
            "CNC"
            in names
    )

    assert (
            "Đọc bản vẽ kỹ thuật"
            in names
    )

    assert (
            "Supply Chain"
            in names
    )

    assert (
            "Teaching"
            in names
    )

    assert (
            "Front Office"
            in names
    )


def test_cv_parser_no_longer_uses_old_cv_canonicals(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = SkillParser(
        settings=settings,
        taxonomy=taxonomy,
    )

    text = """
    Amazon Web Services
    Tax Accounting
    Financial Reporting
    Patient Care
    """

    result = parser.parse(
        raw_text=text,
        section_texts={
            "SKILLS": (
                text,
            ),
        },
    )

    names = {
        skill.normalized_name
        for skill in result.skills
    }

    assert (
            "Amazon Web Services"
            not in names
    )

    assert (
            "Tax Accounting"
            not in names
    )

    assert (
            "Financial Reporting"
            not in names
    )

    assert (
            "Patient Care"
            not in names
    )