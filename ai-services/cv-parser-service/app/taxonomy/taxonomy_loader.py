from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from types import MappingProxyType
from typing import Any, Mapping

import yaml

from app.normalization.text_normalizer import normalize_for_matching


TAXONOMY_FILES = (
    "skills.yml",
    "job_titles.yml",
    "sections.yml",
    "degrees.yml",
    "locations.yml",
    "preferences.yml",
    "language_levels.yml",
)

SKILL_CATEGORIES = {
    "TECHNICAL",
    "SOFTWARE",
    "TOOL",
    "EQUIPMENT",
    "MACHINERY",
    "DOMAIN_KNOWLEDGE",
    "BUSINESS",
    "SALES",
    "MARKETING",
    "FINANCE",
    "ACCOUNTING",
    "HEALTHCARE",
    "EDUCATION",
    "ENGINEERING",
    "TRADE",
    "MANAGEMENT",
    "LEADERSHIP",
    "COMMUNICATION",
    "LANGUAGE",
    "SAFETY",
    "COMPLIANCE",
    "OTHER",
}

SECTION_TYPES = {
    "HEADER",
    "CONTACT",
    "SUMMARY",
    "OBJECTIVE",
    "SKILLS",
    "WORK_EXPERIENCE",
    "PROJECTS",
    "EDUCATION",
    "CERTIFICATIONS",
    "LICENSES",
    "TRAINING",
    "LANGUAGES",
    "AWARDS",
    "PUBLICATIONS",
    "VOLUNTEERING",
    "ACTIVITIES",
    "INTERESTS",
    "REFERENCES",
    "OTHER",
}

DEGREE_LEVELS = {
    "SECONDARY",
    "HIGH_SCHOOL",
    "VOCATIONAL",
    "CERTIFICATE",
    "DIPLOMA",
    "ASSOCIATE",
    "BACHELOR",
    "MASTER",
    "DOCTORATE",
    "PROFESSIONAL_DEGREE",
    "OTHER",
    "UNKNOWN",
}


class TaxonomyValidationError(RuntimeError):
    pass


@dataclass(frozen=True, slots=True)
class SkillTaxonomyItem:
    canonical: str
    category: str
    aliases: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class JobTitleTaxonomyItem:
    canonical: str
    aliases: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class SectionTaxonomyItem:
    section_type: str
    headings: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class DegreeTaxonomyItem:
    level: str
    aliases: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class LocationTaxonomyItem:
    canonical: str
    aliases: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class PreferenceTaxonomy:
    employment_types: Mapping[str, tuple[str, ...]]
    work_modes: Mapping[str, tuple[str, ...]]


@dataclass(frozen=True, slots=True)
class LanguageLevelTaxonomyItem:
    canonical: str
    aliases: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class TaxonomyBundle:
    version: str
    skills: tuple[SkillTaxonomyItem, ...]
    job_titles: tuple[JobTitleTaxonomyItem, ...]
    sections: tuple[SectionTaxonomyItem, ...]
    degrees: tuple[DegreeTaxonomyItem, ...]
    locations: tuple[LocationTaxonomyItem, ...]
    preferences: PreferenceTaxonomy
    language_levels: tuple[LanguageLevelTaxonomyItem, ...]


class TaxonomyLoader:
    def __init__(
            self,
            directory: str | Path,
            expected_version: str,
    ) -> None:
        self._directory = Path(directory)
        self._expected_version = expected_version

    def load(self) -> TaxonomyBundle:
        documents = {
            filename: self._read_yaml(filename)
            for filename in TAXONOMY_FILES
        }

        versions = {
            self._require_non_blank_string(
                document.get("version"),
                f"{filename}.version",
            )
            for filename, document in documents.items()
        }

        if len(versions) != 1:
            raise TaxonomyValidationError(
                "All taxonomy files must use the same version"
            )

        version = next(iter(versions))

        if version != self._expected_version:
            raise TaxonomyValidationError(
                "Taxonomy version does not match CV_PARSER_VERSION"
            )

        return TaxonomyBundle(
            version=version,
            skills=self._parse_skills(
                documents["skills.yml"]
            ),
            job_titles=self._parse_job_titles(
                documents["job_titles.yml"]
            ),
            sections=self._parse_sections(
                documents["sections.yml"]
            ),
            degrees=self._parse_degrees(
                documents["degrees.yml"]
            ),
            locations=self._parse_locations(
                documents["locations.yml"]
            ),
            preferences=self._parse_preferences(
                documents["preferences.yml"]
            ),
            language_levels=self._parse_language_levels(
                documents["language_levels.yml"]
            ),
        )

    def _read_yaml(
            self,
            filename: str,
    ) -> dict[str, Any]:
        path = self._directory / filename

        if not path.is_file():
            raise TaxonomyValidationError(
                f"Taxonomy file is missing: {filename}"
            )

        try:
            with path.open(
                    "r",
                    encoding="utf-8",
            ) as stream:
                value = yaml.safe_load(stream)
        except (OSError, yaml.YAMLError) as exception:
            raise TaxonomyValidationError(
                f"Taxonomy file could not be loaded: {filename}"
            ) from exception

        if not isinstance(value, dict):
            raise TaxonomyValidationError(
                f"Taxonomy file must contain an object: {filename}"
            )

        return value

    def _parse_skills(
            self,
            document: dict[str, Any],
    ) -> tuple[SkillTaxonomyItem, ...]:
        raw_items = self._require_list(
            document.get("items"),
            "skills.yml.items",
        )
        result: list[SkillTaxonomyItem] = []
        canonical_names: set[str] = set()
        aliases: set[str] = set()

        for index, raw_item in enumerate(raw_items):
            item = self._require_mapping(
                raw_item,
                f"skills.yml.items[{index}]",
            )
            canonical = self._require_non_blank_string(
                item.get("canonical"),
                f"skills.yml.items[{index}].canonical",
            )
            category = self._require_non_blank_string(
                item.get("category"),
                f"skills.yml.items[{index}].category",
            ).upper()

            if category not in SKILL_CATEGORIES:
                raise TaxonomyValidationError(
                    f"Unsupported skill category: {category}"
                )

            item_aliases = self._parse_aliases(
                item.get("aliases"),
                canonical,
                f"skills.yml.items[{index}].aliases",
            )

            canonical_key = normalize_for_matching(canonical)

            if canonical_key in canonical_names:
                raise TaxonomyValidationError(
                    f"Duplicate skill canonical name: {canonical}"
                )

            canonical_names.add(canonical_key)

            for alias in item_aliases:
                alias_key = normalize_for_matching(alias)

                if alias_key in aliases:
                    raise TaxonomyValidationError(
                        f"Duplicate skill alias: {alias}"
                    )

                aliases.add(alias_key)

            result.append(
                SkillTaxonomyItem(
                    canonical=canonical,
                    category=category,
                    aliases=item_aliases,
                )
            )

        return tuple(
            sorted(
                result,
                key=lambda item: (
                    item.category,
                    item.canonical.casefold(),
                ),
            )
        )

    def _parse_job_titles(
            self,
            document: dict[str, Any],
    ) -> tuple[JobTitleTaxonomyItem, ...]:
        raw_items = self._require_list(
            document.get("items"),
            "job_titles.yml.items",
        )
        result: list[JobTitleTaxonomyItem] = []

        for index, raw_item in enumerate(raw_items):
            item = self._require_mapping(
                raw_item,
                f"job_titles.yml.items[{index}]",
            )
            canonical = self._require_non_blank_string(
                item.get("canonical"),
                f"job_titles.yml.items[{index}].canonical",
            )
            aliases = self._parse_aliases(
                item.get("aliases"),
                canonical,
                f"job_titles.yml.items[{index}].aliases",
            )

            result.append(
                JobTitleTaxonomyItem(
                    canonical=canonical,
                    aliases=aliases,
                )
            )

        self._validate_unique_aliases(
            (
                alias
                for item in result
                for alias in item.aliases
            ),
            "job title",
        )

        return tuple(
            sorted(
                result,
                key=lambda item: item.canonical.casefold(),
            )
        )

    def _parse_sections(
            self,
            document: dict[str, Any],
    ) -> tuple[SectionTaxonomyItem, ...]:
        raw_items = self._require_list(
            document.get("items"),
            "sections.yml.items",
        )
        result: list[SectionTaxonomyItem] = []

        for index, raw_item in enumerate(raw_items):
            item = self._require_mapping(
                raw_item,
                f"sections.yml.items[{index}]",
            )
            section_type = self._require_non_blank_string(
                item.get("type"),
                f"sections.yml.items[{index}].type",
            ).upper()

            if section_type not in SECTION_TYPES:
                raise TaxonomyValidationError(
                    f"Unsupported section type: {section_type}"
                )

            headings = self._parse_string_list(
                item.get("headings"),
                f"sections.yml.items[{index}].headings",
            )

            result.append(
                SectionTaxonomyItem(
                    section_type=section_type,
                    headings=headings,
                )
            )

        self._validate_unique_aliases(
            (
                heading
                for item in result
                for heading in item.headings
            ),
            "section heading",
        )

        return tuple(result)

    def _parse_degrees(
            self,
            document: dict[str, Any],
    ) -> tuple[DegreeTaxonomyItem, ...]:
        raw_items = self._require_list(
            document.get("items"),
            "degrees.yml.items",
        )
        result: list[DegreeTaxonomyItem] = []

        for index, raw_item in enumerate(raw_items):
            item = self._require_mapping(
                raw_item,
                f"degrees.yml.items[{index}]",
            )
            level = self._require_non_blank_string(
                item.get("level"),
                f"degrees.yml.items[{index}].level",
            ).upper()

            if level not in DEGREE_LEVELS:
                raise TaxonomyValidationError(
                    f"Unsupported degree level: {level}"
                )

            aliases = self._parse_string_list(
                item.get("aliases"),
                f"degrees.yml.items[{index}].aliases",
            )

            result.append(
                DegreeTaxonomyItem(
                    level=level,
                    aliases=aliases,
                )
            )

        return tuple(result)

    def _parse_locations(
            self,
            document: dict[str, Any],
    ) -> tuple[LocationTaxonomyItem, ...]:
        raw_items = self._require_list(
            document.get("items"),
            "locations.yml.items",
        )
        result: list[LocationTaxonomyItem] = []

        for index, raw_item in enumerate(raw_items):
            item = self._require_mapping(
                raw_item,
                f"locations.yml.items[{index}]",
            )
            canonical = self._require_non_blank_string(
                item.get("canonical"),
                f"locations.yml.items[{index}].canonical",
            )
            aliases = self._parse_aliases(
                item.get("aliases"),
                canonical,
                f"locations.yml.items[{index}].aliases",
            )

            result.append(
                LocationTaxonomyItem(
                    canonical=canonical,
                    aliases=aliases,
                )
            )

        return tuple(result)

    def _parse_preferences(
            self,
            document: dict[str, Any],
    ) -> PreferenceTaxonomy:
        employment_types = self._parse_named_alias_mapping(
            document.get("employmentTypes"),
            "preferences.yml.employmentTypes",
        )
        work_modes = self._parse_named_alias_mapping(
            document.get("workModes"),
            "preferences.yml.workModes",
        )

        return PreferenceTaxonomy(
            employment_types=MappingProxyType(employment_types),
            work_modes=MappingProxyType(work_modes),
        )

    def _parse_language_levels(
            self,
            document: dict[str, Any],
    ) -> tuple[LanguageLevelTaxonomyItem, ...]:
        raw_items = self._require_list(
            document.get("items"),
            "language_levels.yml.items",
        )
        result: list[LanguageLevelTaxonomyItem] = []

        for index, raw_item in enumerate(raw_items):
            item = self._require_mapping(
                raw_item,
                f"language_levels.yml.items[{index}]",
            )
            canonical = self._require_non_blank_string(
                item.get("canonical"),
                (
                    "language_levels.yml."
                    f"items[{index}].canonical"
                ),
            )
            aliases = self._parse_aliases(
                item.get("aliases"),
                canonical,
                (
                    "language_levels.yml."
                    f"items[{index}].aliases"
                ),
            )

            result.append(
                LanguageLevelTaxonomyItem(
                    canonical=canonical,
                    aliases=aliases,
                )
            )

        return tuple(result)

    def _parse_named_alias_mapping(
            self,
            raw_value: Any,
            path: str,
    ) -> dict[str, tuple[str, ...]]:
        mapping = self._require_mapping(
            raw_value,
            path,
        )
        result: dict[str, tuple[str, ...]] = {}

        for raw_name, raw_aliases in mapping.items():
            name = self._require_non_blank_string(
                raw_name,
                f"{path}.key",
            ).upper()
            result[name] = self._parse_string_list(
                raw_aliases,
                f"{path}.{name}",
            )

        return result

    def _parse_aliases(
            self,
            raw_aliases: Any,
            canonical: str,
            path: str,
    ) -> tuple[str, ...]:
        aliases = list(
            self._parse_string_list(
                raw_aliases,
                path,
                allow_empty=True,
            )
        )

        if normalize_for_matching(canonical) not in {
            normalize_for_matching(alias)
            for alias in aliases
        }:
            aliases.append(canonical)

        aliases.sort(
            key=lambda value: (
                -len(value),
                value.casefold(),
            )
        )

        return tuple(aliases)

    def _parse_string_list(
            self,
            raw_value: Any,
            path: str,
            allow_empty: bool = False,
    ) -> tuple[str, ...]:
        values = self._require_list(
            raw_value,
            path,
            allow_empty=allow_empty,
        )
        result: list[str] = []
        seen: set[str] = set()

        for index, raw_item in enumerate(values):
            value = self._require_non_blank_string(
                raw_item,
                f"{path}[{index}]",
            )
            normalized = normalize_for_matching(value)

            if normalized in seen:
                raise TaxonomyValidationError(
                    f"Duplicate value in {path}: {value}"
                )

            seen.add(normalized)
            result.append(value)

        return tuple(result)

    @staticmethod
    def _validate_unique_aliases(
            aliases: Any,
            label: str,
    ) -> None:
        seen: set[str] = set()

        for alias in aliases:
            normalized = normalize_for_matching(alias)

            if normalized in seen:
                raise TaxonomyValidationError(
                    f"Duplicate {label}: {alias}"
                )

            seen.add(normalized)

    @staticmethod
    def _require_mapping(
            value: Any,
            path: str,
    ) -> Mapping[str, Any]:
        if not isinstance(value, Mapping):
            raise TaxonomyValidationError(
                f"{path} must be an object"
            )

        return value

    @staticmethod
    def _require_list(
            value: Any,
            path: str,
            allow_empty: bool = False,
    ) -> list[Any]:
        if not isinstance(value, list):
            raise TaxonomyValidationError(
                f"{path} must be a list"
            )

        if not value and not allow_empty:
            raise TaxonomyValidationError(
                f"{path} must not be empty"
            )

        return value

    @staticmethod
    def _require_non_blank_string(
            value: Any,
            path: str,
    ) -> str:
        if not isinstance(value, str):
            raise TaxonomyValidationError(
                f"{path} must be a string"
            )

        normalized = re.sub(
            r"\s+",
            " ",
            value,
        ).strip()

        if not normalized:
            raise TaxonomyValidationError(
                f"{path} must not be blank"
            )

        return normalized