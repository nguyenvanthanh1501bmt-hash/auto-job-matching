from __future__ import annotations

from app.parsing.secondary_section_parsers import (
    CareerPreferenceParser,
)
from app.taxonomy.taxonomy_loader import (
    TaxonomyBundle,
)


def test_cv_bundle_uses_shared_location_canonical(
        taxonomy: TaxonomyBundle,
) -> None:
    hcm = next(
        item
        for item in taxonomy.locations
        if (
                item.canonical
                == "Hồ Chí Minh"
        )
    )

    assert hcm.kind == "CITY"

    assert (
            "Hồ Chí Minh"
            in hcm.aliases
    )

    assert (
            "Ho Chi Minh City"
            in hcm.aliases
    )


def test_cv_preference_parser_maps_english_location_to_shared_canonical(
        taxonomy: TaxonomyBundle,
) -> None:
    parser = CareerPreferenceParser(
        taxonomy
    )

    result = parser.parse(
        {
            "OBJECTIVE": (
                (
                    "Preferred location: "
                    "Ho Chi Minh City"
                ),
            ),
        }
    )

    assert (
            result.preferred_locations
            == (
                "Hồ Chí Minh",
            )
    )


def test_remote_is_work_mode_not_geographic_location(
        taxonomy: TaxonomyBundle,
) -> None:
    parser = CareerPreferenceParser(
        taxonomy
    )

    result = parser.parse(
        {
            "OBJECTIVE": (
                (
                    "Preferred location: Remote\n"
                    "Preferred work mode: Remote"
                ),
            ),
        }
    )

    assert (
            result.preferred_locations
            == ()
    )

    assert (
            result.preferred_work_modes
            == (
                "REMOTE",
            )
    )