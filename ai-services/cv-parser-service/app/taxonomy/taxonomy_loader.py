from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path
from types import MappingProxyType
from typing import Any, Mapping

import yaml

from app.normalization.text_normalizer import normalize_for_matching


# Danh sách các file YAML chứa dữ liệu taxonomy của CV parser.
TAXONOMY_FILES = (
    "skills.yml",
    "job_titles.yml",
    "sections.yml",
    "degrees.yml",
    "locations.yml",
    "preferences.yml",
    "language_levels.yml",
)

# Các category hợp lệ được phép sử dụng cho skill taxonomy.
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

# Các loại section mà CV parser hỗ trợ.
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

# Các loại địa điểm được hỗ trợ trong location taxonomy.
LOCATION_KINDS = {
    "CITY",
    "REGION",
    "COUNTRY",
}

# Các cấp độ bằng cấp được hỗ trợ.
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
    # Exception được sử dụng khi dữ liệu taxonomy không hợp lệ.
    pass


@dataclass(frozen=True, slots=True)
class SkillTaxonomyItem:
    # Đại diện cho một skill trong taxonomy.
    canonical: str
    category: str
    aliases: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class JobTitleTaxonomyItem:
    # Đại diện cho một job title và các alias tương ứng.
    canonical: str
    aliases: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class SectionTaxonomyItem:
    # Đại diện cho một section type và các heading tương ứng.
    section_type: str
    headings: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class DegreeTaxonomyItem:
    # Đại diện cho một degree level và các alias tương ứng.
    level: str
    aliases: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class LocationTaxonomyItem:
    # Đại diện cho một location, loại location và các alias tương ứng.
    canonical: str
    kind: str
    aliases: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class PreferenceTaxonomy:
    # Chứa taxonomy cho employment type và work mode.
    employment_types: Mapping[str, tuple[str, ...]]
    work_modes: Mapping[str, tuple[str, ...]]


@dataclass(frozen=True, slots=True)
class LanguageLevelTaxonomyItem:
    # Đại diện cho một language level và các alias tương ứng.
    canonical: str
    aliases: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class TaxonomyBundle:
    # Bundle chứa toàn bộ taxonomy đã được load và validate.
    version: str
    skills: tuple[SkillTaxonomyItem, ...]
    job_titles: tuple[JobTitleTaxonomyItem, ...]
    sections: tuple[SectionTaxonomyItem, ...]
    degrees: tuple[DegreeTaxonomyItem, ...]
    locations: tuple[LocationTaxonomyItem, ...]
    preferences: PreferenceTaxonomy
    language_levels: tuple[LanguageLevelTaxonomyItem, ...]


class TaxonomyLoader:
    # Load và validate dữ liệu taxonomy từ các file YAML.
    def __init__(
            self,
            directory: str | Path,
            expected_version: str,
    ) -> None:
        self._directory = Path(directory)
        self._expected_version = expected_version

    def load(self) -> TaxonomyBundle:
        # Load toàn bộ taxonomy files trước khi bắt đầu validation.
        documents = {
            filename: self._read_yaml(filename)
            for filename in TAXONOMY_FILES
        }

        # Đảm bảo tất cả taxonomy files đều khai báo version hợp lệ.
        versions = {
            self._require_non_blank_string(
                document.get("version"),
                f"{filename}.version",
            )
            for filename, document in documents.items()
        }

        # Tất cả taxonomy files phải sử dụng cùng một version.
        if len(versions) != 1:
            raise TaxonomyValidationError(
                "All taxonomy files must use the same version"
            )

        version = next(iter(versions))

        # Taxonomy version phải khớp với version mà parser hiện tại yêu cầu.
        if version != self._expected_version:
            raise TaxonomyValidationError(
                "Taxonomy version does not match CV_PARSER_VERSION"
            )

        # Parse từng nhóm taxonomy thành các domain object tương ứng.
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
        # Xác định đường dẫn đầy đủ tới taxonomy file cần đọc.
        path = self._directory / filename

        if not path.is_file():
            raise TaxonomyValidationError(
                f"Taxonomy file is missing: {filename}"
            )

        try:
            # Đọc YAML bằng UTF-8 và chuyển đổi thành object.
            with path.open(
                    "r",
                    encoding="utf-8",
            ) as stream:
                value = yaml.safe_load(stream)
        except (OSError, yaml.YAMLError) as exception:
            # Chuyển lỗi đọc file hoặc parse YAML thành lỗi taxonomy.
            raise TaxonomyValidationError(
                f"Taxonomy file could not be loaded: {filename}"
            ) from exception

        # YAML phải chứa object/mapping ở cấp root.
        if not isinstance(value, dict):
            raise TaxonomyValidationError(
                f"Taxonomy file must contain an object: {filename}"
            )

        return value

    def _parse_skills(
            self,
            document: dict[str, Any],
    ) -> tuple[SkillTaxonomyItem, ...]:
        # Lấy danh sách skill và kiểm tra cấu trúc của từng item.
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

            # Category phải thuộc danh sách category mà parser hỗ trợ.
            if category not in SKILL_CATEGORIES:
                raise TaxonomyValidationError(
                    f"Unsupported skill category: {category}"
                )

            # Parse và chuẩn hóa danh sách alias của skill.
            item_aliases = self._parse_aliases(
                item.get("aliases"),
                canonical,
                f"skills.yml.items[{index}].aliases",
            )

            canonical_key = normalize_for_matching(canonical)

            # Không cho phép hai skill có cùng canonical sau khi normalize.
            if canonical_key in canonical_names:
                raise TaxonomyValidationError(
                    f"Duplicate skill canonical name: {canonical}"
                )

            canonical_names.add(canonical_key)

            # Không cho phép alias bị trùng sau khi normalize.
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

        # Sắp xếp kết quả để taxonomy có thứ tự deterministic.
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
        # Lấy và validate danh sách job title.
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

        # Đảm bảo alias của các job title không bị trùng nhau.
        self._validate_unique_aliases(
            (
                alias
                for item in result
                for alias in item.aliases
            ),
            "job title",
        )

        # Sắp xếp job title theo canonical name để có thứ tự deterministic.
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
        # Lấy và validate danh sách section taxonomy.
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

            # Section type phải thuộc danh sách section mà parser hỗ trợ.
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

        # Không cho phép cùng một heading được dùng cho nhiều section.
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
        # Lấy và validate danh sách degree taxonomy.
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

            # Degree level phải thuộc tập level mà parser hỗ trợ.
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
        # Lấy và validate danh sách location taxonomy.
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

            kind = self._require_non_blank_string(
                item.get("kind"),
                f"locations.yml.items[{index}].kind",
            ).upper()

            # Location kind phải thuộc các loại địa điểm được hỗ trợ.
            if kind not in LOCATION_KINDS:
                raise TaxonomyValidationError(
                    f"Unsupported location kind: {kind}"
                )

            aliases = self._parse_aliases(
                item.get("aliases"),
                canonical,
                f"locations.yml.items[{index}].aliases",
            )

            result.append(
                LocationTaxonomyItem(
                    canonical=canonical,
                    kind=kind,
                    aliases=aliases,
                )
            )

        return tuple(result)

    def _parse_preferences(
            self,
            document: dict[str, Any],
    ) -> PreferenceTaxonomy:
        # Parse employment types và work modes từ taxonomy.
        employment_types = self._parse_named_alias_mapping(
            document.get("employmentTypes"),
            "preferences.yml.employmentTypes",
        )
        work_modes = self._parse_named_alias_mapping(
            document.get("workModes"),
            "preferences.yml.workModes",
        )

        # Chuyển mapping thành read-only để tránh thay đổi taxonomy sau khi load.
        return PreferenceTaxonomy(
            employment_types=MappingProxyType(employment_types),
            work_modes=MappingProxyType(work_modes),
        )

    def _parse_language_levels(
            self,
            document: dict[str, Any],
    ) -> tuple[LanguageLevelTaxonomyItem, ...]:
        # Lấy và validate danh sách language level taxonomy.
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
        # Validate mapping và chuẩn hóa tên key thành uppercase.
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
        # Parse danh sách alias và cho phép danh sách ban đầu rỗng.
        aliases = list(
            self._parse_string_list(
                raw_aliases,
                path,
                allow_empty=True,
            )
        )

        # Đảm bảo canonical name luôn được sử dụng như một alias.
        if normalize_for_matching(canonical) not in {
            normalize_for_matching(alias)
            for alias in aliases
        }:
            aliases.append(canonical)

        # Ưu tiên alias dài hơn để pattern cụ thể được matching trước.
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
        # Validate danh sách string và kiểm tra duplicate sau khi normalize.
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

            # Không cho phép hai giá trị tương đương sau khi normalize.
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
        # Kiểm tra duplicate dựa trên giá trị đã normalize.
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
        # Đảm bảo giá trị YAML có kiểu object/mapping.
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
        # Đảm bảo giá trị YAML có kiểu list.
        if not isinstance(value, list):
            raise TaxonomyValidationError(
                f"{path} must be a list"
            )

        # Không cho phép list rỗng nếu field bắt buộc phải có dữ liệu.
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
        # Đảm bảo giá trị là string.
        if not isinstance(value, str):
            raise TaxonomyValidationError(
                f"{path} must be a string"
            )

        # Chuẩn hóa whitespace liên tiếp và loại bỏ khoảng trắng đầu/cuối.
        normalized = re.sub(
            r"\s+",
            " ",
            value,
        ).strip()

        # Không cho phép giá trị rỗng hoặc chỉ chứa whitespace.
        if not normalized:
            raise TaxonomyValidationError(
                f"{path} must not be blank"
            )

        return normalized