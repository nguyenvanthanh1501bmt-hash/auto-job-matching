from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import yaml

from app.taxonomy.taxonomy_loader import (
    SKILL_CATEGORIES,
    TaxonomyValidationError,
    normalize_for_matching,
)


TAXONOMY_ID_PATTERN = re.compile(
    r"^[a-z0-9]+(?:-[a-z0-9]+)*$"
)

LOCATION_KINDS = {
    "CITY",
    "REGION",
    "COUNTRY",
}


@dataclass(
    frozen=True,
    slots=True,
)
class SharedSkillTaxonomyItem:
    skill_id: str
    canonical: str
    category: str
    aliases: tuple[str, ...]


@dataclass(
    frozen=True,
    slots=True,
)
class SharedSkillTaxonomy:
    version: str
    rich_raw_skill_count: int
    ambiguous_prose_aliases: tuple[str, ...]
    safe_short_prose_aliases: tuple[str, ...]
    items: tuple[
        SharedSkillTaxonomyItem,
        ...
    ]


@dataclass(
    frozen=True,
    slots=True,
)
class SharedLocationTaxonomyItem:
    location_id: str
    canonical: str
    kind: str
    aliases: tuple[str, ...]


@dataclass(
    frozen=True,
    slots=True,
)
class SharedLocationTaxonomy:
    version: str
    ignored_values: tuple[str, ...]
    ambiguous_aliases: tuple[str, ...]
    items: tuple[
        SharedLocationTaxonomyItem,
        ...
    ]


@dataclass(
    frozen=True,
    slots=True,
)
class SharedSeniorityExperience:
    entry_level_under: float
    junior_under: float
    mid_under: float


@dataclass(
    frozen=True,
    slots=True,
)
class SharedSeniorityLevel:
    level: str
    rank: int
    patterns: tuple[str, ...]
    exclude_patterns: tuple[str, ...]
    allow_patterns: tuple[str, ...]


@dataclass(
    frozen=True,
    slots=True,
)
class SharedSeniorityTaxonomy:
    version: str
    experience: SharedSeniorityExperience
    levels: tuple[
        SharedSeniorityLevel,
        ...
    ]


class SharedTaxonomyLoader:
    """
    Loader chung cho taxonomy được share giữa
    nhiều pipeline.

    Hiện hỗ trợ:

        shared/skills.yml
        shared/locations.yml

    Mỗi taxonomy có version độc lập:

        skill-v1
        location-v1

    nên không bị buộc phải dùng CV_PARSER_VERSION.
    """

    def __init__(
            self,
            directory: str | Path,
            expected_skill_version: str = "skill-v1",
            expected_location_version: str = "location-v1",
            expected_seniority_version: str = "seniority-v1",
    ) -> None:
        self._directory = Path(
            directory
        )

        self._expected_skill_version = (
            expected_skill_version
        )

        self._expected_location_version = (
            expected_location_version
        )

        self._expected_seniority_version = (
            expected_seniority_version
        )    # ============================================================
    # Skills
    # ============================================================

    def load_skills(
            self,
    ) -> SharedSkillTaxonomy:
        document = self._read_yaml(
            "skills.yml"
        )

        version = self._require_non_blank_string(
            document.get(
                "version"
            ),
            "skills.yml.version",
        )

        if (
                version
                != self._expected_skill_version
        ):
            raise TaxonomyValidationError(
                "Shared skill taxonomy version mismatch: "
                f"expected="
                f"{self._expected_skill_version}, "
                f"actual={version}"
            )

        root = self._require_mapping(
            self._nested(
                document,
                (
                    "autojob",
                    "taxonomy",
                    "shared",
                    "skills",
                ),
                "skills.yml",
            ),
            "skills.yml."
            "autojob.taxonomy.shared.skills",
        )

        rich_raw_skill_count = (
            self._require_non_negative_int(
                root.get(
                    "rich-raw-skill-count"
                ),
                "skills.yml."
                "rich-raw-skill-count",
            )
        )

        ambiguous_prose_aliases = (
            self._parse_string_list(
                root.get(
                    "ambiguous-prose-aliases",
                    [],
                ),
                "skills.yml."
                "ambiguous-prose-aliases",
            )
        )

        safe_short_prose_aliases = (
            self._parse_string_list(
                root.get(
                    "safe-short-prose-aliases",
                    [],
                ),
                "skills.yml."
                "safe-short-prose-aliases",
            )
        )

        raw_items = self._require_list(
            root.get(
                "items"
            ),
            "skills.yml.items",
        )

        items = self._parse_skill_items(
            raw_items
        )

        return SharedSkillTaxonomy(
            version=version,
            rich_raw_skill_count=(
                rich_raw_skill_count
            ),
            ambiguous_prose_aliases=(
                ambiguous_prose_aliases
            ),
            safe_short_prose_aliases=(
                safe_short_prose_aliases
            ),
            items=items,
        )

    def _parse_skill_items(
            self,
            raw_items: list[Any],
    ) -> tuple[
        SharedSkillTaxonomyItem,
        ...
    ]:
        result: list[
            SharedSkillTaxonomyItem
        ] = []

        seen_ids: set[str] = set()

        seen_canonicals: dict[
            str,
            str,
        ] = {}

        alias_owners: dict[
            str,
            str,
        ] = {}

        for index, raw_item in enumerate(
                raw_items
        ):
            path = (
                f"skills.yml.items[{index}]"
            )

            item = self._require_mapping(
                raw_item,
                path,
            )

            skill_id = (
                self._require_taxonomy_id(
                    item.get(
                        "id"
                    ),
                    f"{path}.id",
                )
            )

            if skill_id in seen_ids:
                raise TaxonomyValidationError(
                    "Duplicate shared skill id: "
                    f"{skill_id}"
                )

            seen_ids.add(
                skill_id
            )

            canonical = (
                self._require_non_blank_string(
                    item.get(
                        "canonical"
                    ),
                    f"{path}.canonical",
                )
            )

            canonical_key = (
                normalize_for_matching(
                    canonical
                )
            )

            existing_canonical = (
                seen_canonicals.get(
                    canonical_key
                )
            )

            if existing_canonical is not None:
                raise TaxonomyValidationError(
                    "Duplicate shared skill canonical: "
                    f"{canonical!r}; "
                    "already owned by "
                    f"{existing_canonical!r}"
                )

            seen_canonicals[
                canonical_key
            ] = canonical

            category = (
                self._require_non_blank_string(
                    item.get(
                        "category"
                    ),
                    f"{path}.category",
                )
                .upper()
            )

            if category not in SKILL_CATEGORIES:
                raise TaxonomyValidationError(
                    "Unsupported shared skill category: "
                    f"{category}"
                )

            configured_aliases = (
                self._parse_string_list(
                    item.get(
                        "aliases",
                        [],
                    ),
                    f"{path}.aliases",
                )
            )

            aliases = (
                self._with_canonical_alias(
                    canonical=canonical,
                    aliases=configured_aliases,
                )
            )

            self._register_alias_owner(
                alias_owners=alias_owners,
                value=canonical,
                owner=canonical,
                taxonomy_name="skill",
            )

            for alias in aliases:
                self._register_alias_owner(
                    alias_owners=alias_owners,
                    value=alias,
                    owner=canonical,
                    taxonomy_name="skill",
                )

            result.append(
                SharedSkillTaxonomyItem(
                    skill_id=skill_id,
                    canonical=canonical,
                    category=category,
                    aliases=aliases,
                )
            )

        result.sort(
            key=lambda item: (
                item.canonical.casefold(),
                item.skill_id,
            )
        )

        return tuple(
            result
        )

    # ============================================================
    # Locations
    # ============================================================

    def load_locations(
            self,
    ) -> SharedLocationTaxonomy:
        document = self._read_yaml(
            "locations.yml"
        )

        version = self._require_non_blank_string(
            document.get(
                "version"
            ),
            "locations.yml.version",
        )

        if (
                version
                != self._expected_location_version
        ):
            raise TaxonomyValidationError(
                "Shared location taxonomy version mismatch: "
                f"expected="
                f"{self._expected_location_version}, "
                f"actual={version}"
            )

        root = self._require_mapping(
            self._nested(
                document,
                (
                    "autojob",
                    "taxonomy",
                    "shared",
                    "locations",
                ),
                "locations.yml",
            ),
            "locations.yml."
            "autojob.taxonomy.shared.locations",
        )

        ignored_values = (
            self._parse_string_list(
                root.get(
                    "ignored-values",
                    [],
                ),
                "locations.yml."
                "ignored-values",
            )
        )

        ambiguous_aliases = (
            self._parse_string_list(
                root.get(
                    "ambiguous-aliases",
                    [],
                ),
                "locations.yml."
                "ambiguous-aliases",
            )
        )

        raw_items = self._require_list(
            root.get(
                "items"
            ),
            "locations.yml.items",
        )

        items = self._parse_location_items(
            raw_items=raw_items,
            ambiguous_aliases=(
                ambiguous_aliases
            ),
        )

        return SharedLocationTaxonomy(
            version=version,
            ignored_values=ignored_values,
            ambiguous_aliases=(
                ambiguous_aliases
            ),
            items=items,
        )

    def _parse_location_items(
            self,
            raw_items: list[Any],
            ambiguous_aliases: tuple[
                str,
                ...
            ],
    ) -> tuple[
        SharedLocationTaxonomyItem,
        ...
    ]:
        result: list[
            SharedLocationTaxonomyItem
        ] = []

        seen_ids: set[str] = set()

        seen_canonicals: dict[
            str,
            str,
        ] = {}

        alias_owners: dict[
            str,
            str,
        ] = {}

        ambiguous_keys = {
            normalize_for_matching(
                alias
            )
            for alias in ambiguous_aliases
        }

        for index, raw_item in enumerate(
                raw_items
        ):
            path = (
                f"locations.yml.items[{index}]"
            )

            item = self._require_mapping(
                raw_item,
                path,
            )

            location_id = (
                self._require_taxonomy_id(
                    item.get(
                        "id"
                    ),
                    f"{path}.id",
                )
            )

            if location_id in seen_ids:
                raise TaxonomyValidationError(
                    "Duplicate shared location id: "
                    f"{location_id}"
                )

            seen_ids.add(
                location_id
            )

            canonical = (
                self._require_non_blank_string(
                    item.get(
                        "canonical"
                    ),
                    f"{path}.canonical",
                )
            )

            canonical_key = (
                normalize_for_matching(
                    canonical
                )
            )

            existing_canonical = (
                seen_canonicals.get(
                    canonical_key
                )
            )

            if existing_canonical is not None:
                raise TaxonomyValidationError(
                    "Duplicate shared location canonical: "
                    f"{canonical!r}; "
                    "already owned by "
                    f"{existing_canonical!r}"
                )

            seen_canonicals[
                canonical_key
            ] = canonical

            kind = (
                self._require_non_blank_string(
                    item.get(
                        "kind"
                    ),
                    f"{path}.kind",
                )
                .upper()
            )

            if kind not in LOCATION_KINDS:
                raise TaxonomyValidationError(
                    "Unsupported shared location kind: "
                    f"{kind}"
                )

            configured_aliases = (
                self._parse_string_list(
                    item.get(
                        "aliases",
                        [],
                    ),
                    f"{path}.aliases",
                )
            )

            aliases = (
                self._with_canonical_alias(
                    canonical=canonical,
                    aliases=configured_aliases,
                )
            )

            canonical_match_key = (
                normalize_for_matching(
                    canonical
                )
            )

            if (
                    canonical_match_key
                    in ambiguous_keys
            ):
                raise TaxonomyValidationError(
                    "Ambiguous location alias "
                    "cannot be used as canonical: "
                    f"{canonical!r}"
                )

            self._register_alias_owner(
                alias_owners=alias_owners,
                value=canonical,
                owner=canonical,
                taxonomy_name="location",
            )

            for alias in aliases:
                alias_key = (
                    normalize_for_matching(
                        alias
                    )
                )

                if alias_key in ambiguous_keys:
                    raise TaxonomyValidationError(
                        "Ambiguous location alias "
                        "must not appear inside an item: "
                        f"{alias!r}"
                    )

                self._register_alias_owner(
                    alias_owners=alias_owners,
                    value=alias,
                    owner=canonical,
                    taxonomy_name="location",
                )

            result.append(
                SharedLocationTaxonomyItem(
                    location_id=location_id,
                    canonical=canonical,
                    kind=kind,
                    aliases=aliases,
                )
            )

        # Preserve taxonomy declaration order.
        #
        # Location consumers expose ordered lists
        # such as preferred_locations.
        #
        # Sorting alphabetically here would introduce
        # an unrelated API behavior change during
        # canonical migration.
        return tuple(
            result
        )

    # ============================================================
    # Seniority
    # ============================================================

    def load_seniority(
        self,
    ) -> SharedSeniorityTaxonomy:
        document = self._read_yaml(
            "seniority.yml"
        )

        version = self._require_non_blank_string(
            document.get(
                "version"
            ),
            "seniority.yml.version",
        )

        if (
            version
            != self._expected_seniority_version
        ):
            raise TaxonomyValidationError(
                "Shared seniority taxonomy version mismatch: "
                f"expected={self._expected_seniority_version}, "
                f"actual={version}"
            )

        root = self._require_mapping(
            self._nested(
                document,
                (
                    "autojob",
                    "taxonomy",
                    "shared",
                    "seniority",
                ),
                "seniority.yml",
            ),
            "seniority.yml."
            "autojob.taxonomy.shared.seniority",
        )

        experience_root = self._require_mapping(
            root.get(
                "experience"
            ),
            "seniority.yml.experience",
        )

        experience = SharedSeniorityExperience(
            entry_level_under=(
                self._require_non_negative_number(
                    experience_root.get(
                        "entry-level-under"
                    ),
                    "seniority.yml.experience."
                    "entry-level-under",
                )
            ),
            junior_under=(
                self._require_non_negative_number(
                    experience_root.get(
                        "junior-under"
                    ),
                    "seniority.yml.experience."
                    "junior-under",
                )
            ),
            mid_under=(
                self._require_non_negative_number(
                    experience_root.get(
                        "mid-under"
                    ),
                    "seniority.yml.experience."
                    "mid-under",
                )
            ),
        )

        if not (
            experience.entry_level_under
            < experience.junior_under
            < experience.mid_under
        ):
            raise TaxonomyValidationError(
                "Shared seniority experience thresholds "
                "must satisfy "
                "entry-level-under < "
                "junior-under < "
                "mid-under"
            )

        raw_levels = self._require_list(
            root.get(
                "levels"
            ),
            "seniority.yml.levels",
        )

        levels = (
            self._parse_seniority_levels(
                raw_levels
            )
        )

        return SharedSeniorityTaxonomy(
            version=version,
            experience=experience,
            levels=levels,
        )

    def _parse_seniority_levels(
        self,
        raw_levels: list[Any],
    ) -> tuple[
        SharedSeniorityLevel,
        ...
    ]:
        allowed_levels = {
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
        }

        result: list[
            SharedSeniorityLevel
        ] = []

        seen_levels: set[str] = set()
        seen_ranks: set[int] = set()

        for index, raw_level in enumerate(
            raw_levels
        ):
            path = (
                f"seniority.yml.levels[{index}]"
            )

            item = self._require_mapping(
                raw_level,
                path,
            )

            level = (
                self._require_non_blank_string(
                    item.get(
                        "level"
                    ),
                    f"{path}.level",
                )
                .upper()
            )

            if level not in allowed_levels:
                raise TaxonomyValidationError(
                    "Unsupported shared seniority "
                    f"level: {level}"
                )

            if level in seen_levels:
                raise TaxonomyValidationError(
                    "Duplicate shared seniority "
                    f"level: {level}"
                )

            seen_levels.add(
                level
            )

            rank = item.get(
                "rank"
            )

            if (
                not isinstance(
                    rank,
                    int,
                )
                or isinstance(
                    rank,
                    bool,
                )
            ):
                raise TaxonomyValidationError(
                    f"{path}.rank must be an integer"
                )

            if rank in seen_ranks:
                raise TaxonomyValidationError(
                    "Duplicate shared seniority "
                    f"rank: {rank}"
                )

            seen_ranks.add(
                rank
            )

            patterns = (
                self._parse_string_list(
                    item.get(
                        "patterns",
                        [],
                    ),
                    f"{path}.patterns",
                )
            )

            exclude_patterns = (
                self._parse_string_list(
                    item.get(
                        "exclude-patterns",
                        [],
                    ),
                    f"{path}.exclude-patterns",
                )
            )

            allow_patterns = (
                self._parse_string_list(
                    item.get(
                        "allow-patterns",
                        [],
                    ),
                    f"{path}.allow-patterns",
                )
            )

            if (
                level != "UNKNOWN"
                and not patterns
            ):
                raise TaxonomyValidationError(
                    "Shared seniority level "
                    f"{level} must define patterns"
                )

            if (
                level == "UNKNOWN"
                and patterns
            ):
                raise TaxonomyValidationError(
                    "UNKNOWN seniority must not "
                    "define patterns"
                )

            for regex_value in (
                *patterns,
                *exclude_patterns,
                *allow_patterns,
            ):
                try:
                    re.compile(
                        regex_value
                    )
                except re.error as exception:
                    raise TaxonomyValidationError(
                        "Invalid shared seniority regex: "
                        f"{regex_value!r}"
                    ) from exception

            result.append(
                SharedSeniorityLevel(
                    level=level,
                    rank=rank,
                    patterns=patterns,
                    exclude_patterns=(
                        exclude_patterns
                    ),
                    allow_patterns=(
                        allow_patterns
                    ),
                )
            )

        if not result:
            raise TaxonomyValidationError(
                "Shared seniority levels "
                "must not be empty"
            )

        actual_levels = {
            item.level
            for item in result
        }

        if actual_levels != allowed_levels:
            missing = sorted(
                allowed_levels
                - actual_levels
            )

            extra = sorted(
                actual_levels
                - allowed_levels
            )

            raise TaxonomyValidationError(
                "Shared seniority vocabulary mismatch: "
                f"missing={missing}, "
                f"extra={extra}"
            )

        if (
            result[-1].level
            != "UNKNOWN"
        ):
            raise TaxonomyValidationError(
                "UNKNOWN seniority must be "
                "the final rule"
            )

        return tuple(
            result
        )

    # ============================================================
    # Shared validation helpers
    # ============================================================

    def _read_yaml(
            self,
            filename: str,
    ) -> dict[str, Any]:
        path = (
                self._directory
                / filename
        )

        if not path.is_file():
            raise TaxonomyValidationError(
                "Shared taxonomy file "
                "does not exist: "
                f"{path}"
            )

        try:
            with path.open(
                    "r",
                    encoding="utf-8",
            ) as stream:
                document = yaml.safe_load(
                    stream
                )
        except yaml.YAMLError as exception:
            raise TaxonomyValidationError(
                "Cannot parse shared taxonomy "
                f"file: {path}"
            ) from exception

        if not isinstance(
                document,
                dict,
        ):
            raise TaxonomyValidationError(
                "Shared taxonomy root "
                "must be an object: "
                f"{path}"
            )

        return document

    @staticmethod
    def _nested(
            document: dict[str, Any],
            keys: tuple[str, ...],
            filename: str,
    ) -> Any:
        current: Any = document

        traversed: list[str] = []

        for key in keys:
            traversed.append(
                key
            )

            if (
                    not isinstance(
                        current,
                        dict,
                    )
                    or key not in current
            ):
                raise TaxonomyValidationError(
                    "Missing shared taxonomy path: "
                    f"{filename}."
                    + ".".join(
                        traversed
                    )
                )

            current = current[
                key
            ]

        return current

    @staticmethod
    def _require_mapping(
            value: Any,
            path: str,
    ) -> dict[str, Any]:
        if not isinstance(
                value,
                dict,
        ):
            raise TaxonomyValidationError(
                f"{path} must be an object"
            )

        return value

    @staticmethod
    def _require_list(
            value: Any,
            path: str,
    ) -> list[Any]:
        if not isinstance(
                value,
                list,
        ):
            raise TaxonomyValidationError(
                f"{path} must be a list"
            )

        return value

    @staticmethod
    def _require_non_blank_string(
            value: Any,
            path: str,
    ) -> str:
        if not isinstance(
                value,
                str,
        ):
            raise TaxonomyValidationError(
                f"{path} must be a string"
            )

        normalized = (
            value.strip()
        )

        if not normalized:
            raise TaxonomyValidationError(
                f"{path} must not be blank"
            )

        return normalized

    @staticmethod
    def _require_non_negative_int(
            value: Any,
            path: str,
    ) -> int:
        if (
                not isinstance(
                    value,
                    int,
                )
                or isinstance(
            value,
            bool,
        )
                or value < 0
        ):
            raise TaxonomyValidationError(
                f"{path} must be a "
                "non-negative integer"
            )

        return value

    @staticmethod
    def _require_non_negative_number(
        value: Any,
        path: str,
    ) -> float:
        if (
            not isinstance(
                value,
                (int, float),
            )
            or isinstance(
                value,
                bool,
            )
        ):
            raise TaxonomyValidationError(
                f"{path} must be a number"
            )

        number = float(
            value
        )

        if number < 0:
            raise TaxonomyValidationError(
                f"{path} must be non-negative"
            )

        return number

    @staticmethod
    def _require_taxonomy_id(
            value: Any,
            path: str,
    ) -> str:
        if not isinstance(
                value,
                str,
        ):
            raise TaxonomyValidationError(
                f"{path} must be a string"
            )

        taxonomy_id = (
            value.strip()
        )

        if not TAXONOMY_ID_PATTERN.fullmatch(
                taxonomy_id
        ):
            raise TaxonomyValidationError(
                f"{path} must match "
                "^[a-z0-9]+(?:-[a-z0-9]+)*$"
            )

        return taxonomy_id

    def _parse_string_list(
            self,
            value: Any,
            path: str,
    ) -> tuple[str, ...]:
        raw_values = self._require_list(
            value,
            path,
        )

        result: list[str] = []

        seen: set[str] = set()

        for index, raw_value in enumerate(
                raw_values
        ):
            item = (
                self._require_non_blank_string(
                    raw_value,
                    f"{path}[{index}]",
                )
            )

            key = normalize_for_matching(
                item
            )

            if key in seen:
                continue

            seen.add(
                key
            )

            result.append(
                item
            )

        return tuple(
            result
        )

    @staticmethod
    def _with_canonical_alias(
        canonical: str,
        aliases: tuple[str, ...],
    ) -> tuple[str, ...]:
        """
        Preserve the historical taxonomy contract:

        canonical itself is always a valid alias.

        Deduplication uses the same matching normalization
        as the rest of the parser.
        """

        result: list[str] = []

        seen: set[str] = set()

        for value in (
            canonical,
            *aliases,
        ):
            key = normalize_for_matching(
                value
            )

            if key in seen:
                continue

            seen.add(
                key
            )

            result.append(
                value
            )

        return tuple(
            result
        )

    @staticmethod
    def _register_alias_owner(
            alias_owners: dict[
                str,
                str,
            ],
            value: str,
            owner: str,
            taxonomy_name: str,
    ) -> None:
        key = normalize_for_matching(
            value
        )

        if not key:
            raise TaxonomyValidationError(
                f"Blank normalized "
                f"{taxonomy_name} alias: "
                f"{value!r}"
            )

        existing_owner = (
            alias_owners.get(
                key
            )
        )

        if (
                existing_owner is not None
                and existing_owner != owner
        ):
            raise TaxonomyValidationError(
                f"Shared {taxonomy_name} "
                "alias collision: "
                f"{value!r} maps to both "
                f"{existing_owner!r} and "
                f"{owner!r}"
            )

        alias_owners[
            key
        ] = owner