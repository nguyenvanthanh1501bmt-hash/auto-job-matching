from __future__ import annotations

from pathlib import Path

import pytest
import yaml

from app.taxonomy.shared_taxonomy_loader import (
    SharedTaxonomyLoader,
)
from app.taxonomy.taxonomy_loader import (
    TaxonomyValidationError,
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


def load_real_taxonomy():
    return SharedTaxonomyLoader(
        directory=SHARED_DIRECTORY,
        expected_seniority_version=(
            "seniority-v1"
        ),
    ).load_seniority()


def default_levels() -> list[dict]:
    return [
        {
            "level": "EXECUTIVE",
            "rank": 12,
            "patterns": [
                "executive",
            ],
        },
        {
            "level": "DIRECTOR",
            "rank": 11,
            "patterns": [
                "director",
            ],
        },
        {
            "level": "HEAD",
            "rank": 10,
            "patterns": [
                "head",
            ],
        },
        {
            "level": "MANAGER",
            "rank": 9,
            "patterns": [
                "manager",
            ],
        },
        {
            "level": "SUPERVISOR",
            "rank": 8,
            "patterns": [
                "supervisor",
            ],
        },
        {
            "level": "LEAD",
            "rank": 7,
            "patterns": [
                "lead",
            ],
        },
        {
            "level": "SENIOR",
            "rank": 6,
            "patterns": [
                "senior",
            ],
        },
        {
            "level": "MID",
            "rank": 5,
            "patterns": [
                "mid",
            ],
        },
        {
            "level": "JUNIOR",
            "rank": 4,
            "patterns": [
                "junior",
            ],
        },
        {
            "level": "ENTRY_LEVEL",
            "rank": 3,
            "patterns": [
                "entry",
            ],
        },
        {
            "level": "FRESHER",
            "rank": 2,
            "patterns": [
                "fresher",
            ],
        },
        {
            "level": "TRAINEE",
            "rank": 1,
            "patterns": [
                "trainee",
            ],
        },
        {
            "level": "INTERN",
            "rank": 0,
            "patterns": [
                "intern",
            ],
        },
        {
            "level": "UNKNOWN",
            "rank": -1,
            "patterns": [],
        },
    ]


def write_seniority_taxonomy(
        directory: Path,
        *,
        version: str = "seniority-v1",
        experience: dict | None = None,
        levels: list[dict] | None = None,
) -> None:
    document = {
        "version": version,
        "autojob": {
            "taxonomy": {
                "shared": {
                    "seniority": {
                        "experience": (
                            experience
                            if experience is not None
                            else {
                                "entry-level-under": 0.5,
                                "junior-under": 2.0,
                                "mid-under": 5.0,
                            }
                        ),
                        "levels": (
                            levels
                            if levels is not None
                            else default_levels()
                        ),
                    }
                }
            }
        },
    }

    path = (
            directory
            / "seniority.yml"
    )

    path.write_text(
        yaml.safe_dump(
            document,
            allow_unicode=True,
            sort_keys=False,
        ),
        encoding="utf-8",
    )


def test_real_shared_seniority_taxonomy_loads() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    assert (
            taxonomy.version
            == "seniority-v1"
    )

    assert len(
        taxonomy.levels
    ) == 14


def test_shared_seniority_vocabulary_is_complete() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    assert [
               item.level
               for item in taxonomy.levels
           ] == [
               "EXECUTIVE",
               "DIRECTOR",
               "HEAD",
               "MANAGER",
               "SUPERVISOR",
               "LEAD",
               "SENIOR",
               "MID",
               "JUNIOR",
               "ENTRY_LEVEL",
               "FRESHER",
               "TRAINEE",
               "INTERN",
               "UNKNOWN",
           ]


def test_shared_seniority_ranks_are_unique() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    ranks = [
        item.rank
        for item in taxonomy.levels
    ]

    assert len(
        ranks
    ) == len(
        set(ranks)
    )


def test_experience_fallback_thresholds_are_shared() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    assert (
            taxonomy
            .experience
            .entry_level_under
            == 0.5
    )

    assert (
            taxonomy
            .experience
            .junior_under
            == 2.0
    )

    assert (
            taxonomy
            .experience
            .mid_under
            == 5.0
    )


def test_lead_generation_is_explicitly_excluded() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    lead = next(
        item
        for item in taxonomy.levels
        if item.level == "LEAD"
    )

    assert (
            r"^lead generation"
            in lead.exclude_patterns
    )


def test_unknown_is_final_and_has_no_patterns() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    unknown = (
        taxonomy.levels[-1]
    )

    assert (
            unknown.level
            == "UNKNOWN"
    )

    assert (
            unknown.patterns
            == ()
    )


def test_wrong_seniority_version_is_rejected() -> None:
    with pytest.raises(
            TaxonomyValidationError,
            match="version mismatch",
    ):
        SharedTaxonomyLoader(
            directory=SHARED_DIRECTORY,
            expected_seniority_version=(
                "seniority-v999"
            ),
        ).load_seniority()


def test_invalid_experience_threshold_order_is_rejected(
        tmp_path: Path,
) -> None:
    write_seniority_taxonomy(
        tmp_path,
        experience={
            "entry-level-under": 2.0,
            "junior-under": 1.0,
            "mid-under": 5.0,
        },
    )

    with pytest.raises(
            TaxonomyValidationError,
            match="thresholds",
    ):
        SharedTaxonomyLoader(
            directory=tmp_path
        ).load_seniority()


def test_duplicate_seniority_rank_is_rejected(
        tmp_path: Path,
) -> None:
    levels = (
        default_levels()
    )

    levels[1]["rank"] = (
        levels[0]["rank"]
    )

    write_seniority_taxonomy(
        tmp_path,
        levels=levels,
    )

    with pytest.raises(
            TaxonomyValidationError,
            match="Duplicate shared seniority rank",
    ):
        SharedTaxonomyLoader(
            directory=tmp_path
        ).load_seniority()


def test_invalid_seniority_regex_is_rejected(
        tmp_path: Path,
) -> None:
    levels = (
        default_levels()
    )

    levels[0]["patterns"] = [
        "[invalid",
    ]

    write_seniority_taxonomy(
        tmp_path,
        levels=levels,
    )

    with pytest.raises(
            TaxonomyValidationError,
            match="Invalid shared seniority regex",
    ):
        SharedTaxonomyLoader(
            directory=tmp_path
        ).load_seniority()