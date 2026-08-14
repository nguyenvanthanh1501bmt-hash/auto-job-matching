from __future__ import annotations

from pathlib import Path

from app.parsing.seniority_parser import (
    SeniorityParser,
)
from app.taxonomy.shared_taxonomy_loader import (
    SharedTaxonomyLoader,
)


REPOSITORY_ROOT = (
    Path(__file__)
    .resolve()
    .parents[3]
)

SHARED_DIRECTORY = (
        REPOSITORY_ROOT
        / "configs"
        / "taxonomy"
        / "shared"
)


def parser() -> SeniorityParser:
    taxonomy = (
        SharedTaxonomyLoader(
            directory=SHARED_DIRECTORY
        )
        .load_seniority()
    )

    return SeniorityParser(
        taxonomy
    )


def classify(
        title: str,
        *,
        experience_years: float | None = None,
) -> str:
    result = parser().parse(
        headline=title,
        target_job_titles=(),
        work_experiences=(),
        experience_years=(
            experience_years
        ),
    )

    return result.seniority


def test_explicit_fresher_remains_fresher() -> None:
    assert (
            classify(
                "Fresher Java Developer"
            )
            == "FRESHER"
    )


def test_explicit_intern_vietnamese_is_intern() -> None:
    assert (
            classify(
                "Thực tập sinh Marketing"
            )
            == "INTERN"
    )


def test_vietnamese_manager_uses_folded_rule() -> None:
    assert (
            classify(
                "Trưởng phòng Kinh doanh"
            )
            == "MANAGER"
    )


def test_vietnamese_executive_uses_folded_rule() -> None:
    assert (
            classify(
                "Tổng Giám Đốc"
            )
            == "EXECUTIVE"
    )


def test_manager_has_priority_over_senior() -> None:
    assert (
            classify(
                "Senior Sales Manager"
            )
            == "MANAGER"
    )


def test_lead_generation_is_not_seniority_lead() -> None:
    assert (
            classify(
                "Lead Generation Specialist"
            )
            == "UNKNOWN"
    )


def test_lead_generation_leader_is_allowed() -> None:
    assert (
            classify(
                "Lead Generation Leader"
            )
            == "LEAD"
    )


def test_low_experience_is_entry_level() -> None:
    assert (
            classify(
                "Software Engineer",
                experience_years=0.2,
            )
            == "ENTRY_LEVEL"
    )


def test_one_and_half_years_is_junior() -> None:
    assert (
            classify(
                "Software Engineer",
                experience_years=1.5,
            )
            == "JUNIOR"
    )


def test_three_years_is_mid() -> None:
    assert (
            classify(
                "Software Engineer",
                experience_years=3.0,
            )
            == "MID"
    )


def test_five_years_is_senior() -> None:
    assert (
            classify(
                "Software Engineer",
                experience_years=5.0,
            )
            == "SENIOR"
    )


def test_explicit_title_wins_over_experience() -> None:
    result = parser().parse(
        headline="Junior Developer",
        target_job_titles=(),
        work_experiences=(),
        experience_years=8.0,
    )

    assert (
            result.seniority
            == "JUNIOR"
    )

    assert (
            "SENIORITY_SIGNALS_CONFLICT"
            in result.warnings
    )