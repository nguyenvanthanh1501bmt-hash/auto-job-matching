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
        expected_skill_version="skill-v1",
    ).load_skills()


def write_skill_taxonomy(
        directory: Path,
        *,
        version: str = "skill-v1",
        items: list[dict] | None = None,
) -> None:
    document = {
        "version": version,
        "autojob": {
            "taxonomy": {
                "shared": {
                    "skills": {
                        "rich-raw-skill-count": 2,
                        "ambiguous-prose-aliases": [],
                        "safe-short-prose-aliases": [],
                        "items": (
                            items
                            if items is not None
                            else [
                                {
                                    "id": "java",
                                    "canonical": "Java",
                                    "category": "TECHNICAL",
                                    "aliases": [],
                                }
                            ]
                        ),
                    }
                }
            }
        },
    }

    (
            directory
            / "skills.yml"
    ).write_text(
        yaml.safe_dump(
            document,
            allow_unicode=True,
            sort_keys=False,
        ),
        encoding="utf-8",
    )


def normalized_aliases(
        aliases: tuple[str, ...],
) -> set[str]:
    return {
        alias.casefold()
        for alias in aliases
    }


def test_real_shared_skill_taxonomy_loads() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    assert (
            taxonomy.version
            == "skill-v1"
    )

    assert (
            taxonomy.rich_raw_skill_count
            == 2
    )

    assert len(
        taxonomy.items
    ) == 173


def test_skill_ids_are_unique() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    ids = [
        item.skill_id
        for item in taxonomy.items
    ]

    assert len(
        ids
    ) == len(
        set(ids)
    )


def test_aws_is_one_shared_concept() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    aws = next(
        item
        for item in taxonomy.items
        if item.skill_id == "aws"
    )

    assert (
            aws.canonical
            == "AWS"
    )

    aliases = normalized_aliases(
        aws.aliases
    )

    # Canonical luôn là một alias hợp lệ.
    assert "aws" in aliases

    assert (
            "amazon web services"
            in aliases
    )


def test_tax_accounting_uses_shared_canonical() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    item = next(
        item
        for item in taxonomy.items
        if (
                item.skill_id
                == "tax-accounting"
        )
    )

    assert (
            item.canonical
            == "Kế toán thuế"
    )

    aliases = normalized_aliases(
        item.aliases
    )

    assert (
            "tax accounting"
            in aliases
    )


def test_financial_reporting_uses_shared_canonical() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    item = next(
        item
        for item in taxonomy.items
        if (
                item.skill_id
                == "financial-reporting"
        )
    )

    assert (
            item.canonical
            == "Lập báo cáo tài chính"
    )

    aliases = normalized_aliases(
        item.aliases
    )

    assert (
            "financial reporting"
            in aliases
    )


def test_patient_care_uses_shared_canonical() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    item = next(
        item
        for item in taxonomy.items
        if (
                item.skill_id
                == "patient-care"
        )
    )

    assert (
            item.canonical
            == "Chăm sóc bệnh nhân"
    )

    aliases = normalized_aliases(
        item.aliases
    )

    assert (
            "patient care"
            in aliases
    )

    assert (
            "chăm sóc người bệnh"
            in aliases
    )


def test_rejects_wrong_skill_taxonomy_version() -> None:
    with pytest.raises(
            TaxonomyValidationError,
            match="version mismatch",
    ):
        SharedTaxonomyLoader(
            directory=SHARED_DIRECTORY,
            expected_skill_version=(
                "skill-v999"
            ),
        ).load_skills()


def test_rejects_duplicate_skill_ids(
        tmp_path: Path,
) -> None:
    write_skill_taxonomy(
        tmp_path,
        items=[
            {
                "id": "java",
                "canonical": "Java",
                "category": "TECHNICAL",
                "aliases": [],
            },
            {
                "id": "java",
                "canonical": "Python",
                "category": "TECHNICAL",
                "aliases": [],
            },
        ],
    )

    with pytest.raises(
            TaxonomyValidationError
    ):
        SharedTaxonomyLoader(
            directory=tmp_path
        ).load_skills()


def test_rejects_skill_alias_collision(
        tmp_path: Path,
) -> None:
    write_skill_taxonomy(
        tmp_path,
        items=[
            {
                "id": "first",
                "canonical": "First Skill",
                "category": "TECHNICAL",
                "aliases": [
                    "shared alias",
                ],
            },
            {
                "id": "second",
                "canonical": "Second Skill",
                "category": "TECHNICAL",
                "aliases": [
                    "shared alias",
                ],
            },
        ],
    )

    with pytest.raises(
            TaxonomyValidationError
    ):
        SharedTaxonomyLoader(
            directory=tmp_path
        ).load_skills()