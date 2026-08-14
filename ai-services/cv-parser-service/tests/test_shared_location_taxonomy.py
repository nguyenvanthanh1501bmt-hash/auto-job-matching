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
        expected_location_version=(
            "location-v1"
        ),
    ).load_locations()


def write_location_taxonomy(
        directory: Path,
        *,
        version: str = "location-v1",
        ambiguous_aliases: list[str] | None = None,
        items: list[dict] | None = None,
) -> None:
    document = {
        "version": version,
        "autojob": {
            "taxonomy": {
                "shared": {
                    "locations": {
                        "ignored-values": [
                            "Không xác định",
                        ],
                        "ambiguous-aliases": (
                            ambiguous_aliases
                            if ambiguous_aliases
                               is not None
                            else []
                        ),
                        "items": (
                            items
                            if items
                               is not None
                            else [
                                {
                                    "id": (
                                        "ho-chi-minh"
                                    ),
                                    "canonical": (
                                        "Hồ Chí Minh"
                                    ),
                                    "kind": "CITY",
                                    "aliases": [
                                        (
                                            "Ho Chi Minh City"
                                        ),
                                        "HCM",
                                    ],
                                }
                            ]
                        ),
                    }
                }
            }
        },
    }

    path = (
            directory
            / "locations.yml"
    )

    path.write_text(
        yaml.safe_dump(
            document,
            allow_unicode=True,
            sort_keys=False,
        ),
        encoding="utf-8",
    )


def test_real_shared_location_taxonomy_loads() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    assert (
            taxonomy.version
            == "location-v1"
    )

    assert len(
        taxonomy.items
    ) == 32


def test_location_ids_are_unique() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    ids = [
        item.location_id
        for item in taxonomy.items
    ]

    assert len(
        ids
    ) == len(
        set(ids)
    )


def test_ho_chi_minh_merges_job_and_cv_names() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    location = next(
        item
        for item in taxonomy.items
        if (
                item.canonical
                == "Hồ Chí Minh"
        )
    )

    assert (
            location.kind
            == "CITY"
    )

    assert (
            "Ho Chi Minh City"
            in location.aliases
    )


def test_hue_city_and_thua_thien_hue_region_are_separate() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    hue = next(
        item
        for item in taxonomy.items
        if item.canonical == "Huế"
    )

    region = next(
        item
        for item in taxonomy.items
        if (
                item.canonical
                == "Thua Thien Hue"
        )
    )

    assert hue.kind == "CITY"

    assert region.kind == "REGION"

    assert (
            "Thừa Thiên Huế"
            not in hue.aliases
    )


def test_remote_is_not_a_geographic_location() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    values = {
        item.canonical
        for item in taxonomy.items
    }

    assert "Remote" not in values


def test_dn_is_ambiguous_and_not_owned_by_any_location() -> None:
    taxonomy = (
        load_real_taxonomy()
    )

    assert (
            "DN"
            in taxonomy.ambiguous_aliases
    )

    all_aliases = {
        alias
        for item in taxonomy.items
        for alias in item.aliases
    }

    assert "DN" not in all_aliases


def test_wrong_location_version_is_rejected(
        tmp_path: Path,
) -> None:
    write_location_taxonomy(
        tmp_path,
        version="location-v999",
    )

    loader = SharedTaxonomyLoader(
        directory=tmp_path,
        expected_location_version=(
            "location-v1"
        ),
    )

    with pytest.raises(
            TaxonomyValidationError
    ):
        loader.load_locations()


def test_duplicate_location_id_is_rejected(
        tmp_path: Path,
) -> None:
    write_location_taxonomy(
        tmp_path,
        items=[
            {
                "id": "hanoi",
                "canonical": "Hà Nội",
                "kind": "CITY",
                "aliases": [
                    "Hanoi",
                ],
            },
            {
                "id": "hanoi",
                "canonical": "Hải Phòng",
                "kind": "CITY",
                "aliases": [
                    "Hai Phong",
                ],
            },
        ],
    )

    loader = SharedTaxonomyLoader(
        directory=tmp_path,
    )

    with pytest.raises(
            TaxonomyValidationError
    ):
        loader.load_locations()


def test_location_alias_collision_is_rejected(
        tmp_path: Path,
) -> None:
    write_location_taxonomy(
        tmp_path,
        items=[
            {
                "id": "hanoi",
                "canonical": "Hà Nội",
                "kind": "CITY",
                "aliases": [
                    "Capital City",
                ],
            },
            {
                "id": "hai-phong",
                "canonical": "Hải Phòng",
                "kind": "CITY",
                "aliases": [
                    "Capital City",
                ],
            },
        ],
    )

    loader = SharedTaxonomyLoader(
        directory=tmp_path,
    )

    with pytest.raises(
            TaxonomyValidationError
    ):
        loader.load_locations()


def test_ambiguous_alias_cannot_leak_into_item(
        tmp_path: Path,
) -> None:
    write_location_taxonomy(
        tmp_path,
        ambiguous_aliases=[
            "DN",
        ],
        items=[
            {
                "id": "da-nang",
                "canonical": "Đà Nẵng",
                "kind": "CITY",
                "aliases": [
                    "Da Nang",
                    "DN",
                ],
            }
        ],
    )

    loader = SharedTaxonomyLoader(
        directory=tmp_path,
    )

    with pytest.raises(
            TaxonomyValidationError
    ):
        loader.load_locations()