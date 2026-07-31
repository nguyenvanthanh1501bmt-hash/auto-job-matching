from __future__ import annotations

import re
from collections.abc import Mapping
from dataclasses import dataclass

from app.config import Settings
from app.normalization.date_normalizer import (
    DateRange,
    duration_months,
    extract_date_range,
)
from app.normalization.text_normalizer import normalize_for_matching
from app.normalization.value_normalizer import clean_optional_text, stable_unique
from app.parsing.skill_parser import SkillParser
from app.schemas import WorkExperience
from app.taxonomy.taxonomy_loader import TaxonomyBundle


BULLET_PATTERN = re.compile(
    r"^\s*(?:[-*•●▪◦‣∙·]|\d{1,2}[.)])"
    r"\s*(?P<value>.+)$"
)
LABEL_PATTERN = re.compile(
    r"^(?P<label>"
    r"company|employer|organization|organisation|công\s+ty|đơn\s+vị|"
    r"position|job\s+title|title|role|chức\s+danh|vị\s+trí|"
    r"location|địa\s+điểm"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)
INLINE_AT_PATTERN = re.compile(
    r"\s+(?:at|@|tại)\s+",
    re.IGNORECASE,
)
INLINE_SEPARATOR_PATTERN = re.compile(
    r"\s*(?:\||\s+[-–—]\s+)\s*"
)
SENTENCE_END_PATTERN = re.compile(
    r"[.!?]$"
)
COMPANY_INDICATOR_PATTERN = re.compile(
    r"\b(?:"
    r"company|co\.?|corporation|corp\.?|inc\.?|ltd\.?|limited|llc|group|"
    r"bank|hospital|clinic|pharmacy|school|university|college|"
    r"hotel|restaurant|factory|plant|warehouse|agency|studio|"
    r"center|centre|department|"
    r"công\s+ty|tập\s+đoàn|ngân\s+hàng|bệnh\s+viện|"
    r"phòng\s+khám|nhà\s+thuốc|trường|khách\s+sạn|"
    r"nhà\s+hàng|nhà\s+máy|kho|trung\s+tâm"
    r")\b",
    re.IGNORECASE,
)
ACHIEVEMENT_PATTERN = re.compile(
    r"(?:"
    r"\b(?:achieved|awarded|delivered|exceeded|generated|grew|"
    r"improved|increased|reduced|saved|won|recognized|"
    r"promoted|optimized)\b|"
    r"\b\d+(?:[.,]\d+)?\s*%|"
    r"(?:đạt|vượt|tăng|giảm|tiết\s+kiệm|cải\s+thiện|"
    r"tối\s+ưu|được\s+khen\s+thưởng)"
    r")",
    re.IGNORECASE,
)

EMPLOYMENT_CONTEXT_LINES = 5
MAX_HEADER_LINE_LENGTH = 180
MAX_DESCRIPTION_LENGTH = 5_000
MAX_LIST_ITEMS = 50


@dataclass(frozen=True, slots=True)
class WorkExperienceParseResult:
    work_experiences: tuple[WorkExperience, ...]
    warnings: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class _EntrySlice:
    lines: tuple[str, ...]
    date_range: DateRange


class WorkExperienceParser:
    def __init__(
            self,
            settings: Settings,
            taxonomy: TaxonomyBundle,
            skill_parser: SkillParser,
    ) -> None:
        self._settings = settings
        self._job_titles = taxonomy.job_titles
        self._locations = taxonomy.locations
        self._employment_types = (
            taxonomy.preferences.employment_types
        )
        self._work_modes = (
            taxonomy.preferences.work_modes
        )
        self._skill_parser = skill_parser

    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
    ) -> WorkExperienceParseResult:
        sections = section_texts.get(
            "WORK_EXPERIENCE",
            (),
        )

        if not sections:
            return WorkExperienceParseResult(
                work_experiences=(),
                warnings=(
                    "WORK_EXPERIENCE_SECTION_NOT_DETECTED",
                ),
            )

        entries: list[WorkExperience] = []
        warnings: list[str] = []

        for section in sections:
            slices = self._split_entries(
                section
            )

            for entry_slice in slices:
                (
                    experience,
                    entry_warnings,
                ) = self._parse_entry(
                    entry_slice
                )

                warnings.extend(
                    entry_warnings
                )

                if experience is not None:
                    entries.append(
                        experience
                    )

                if (
                        len(entries)
                        >= self._settings.max_work_experiences
                ):
                    break

            if (
                    len(entries)
                    >= self._settings.max_work_experiences
            ):
                break

        if not entries:
            warnings.append(
                "WORK_EXPERIENCE_PARTIALLY_PARSED"
            )
        elif any(
                experience.company_name is None
                or experience.job_title is None
                or experience.start_date is None
                for experience in entries
        ):
            warnings.append(
                "WORK_EXPERIENCE_PARTIALLY_PARSED"
            )

        return WorkExperienceParseResult(
            work_experiences=tuple(entries),
            warnings=tuple(
                dict.fromkeys(warnings)
            ),
        )

    def _split_entries(
            self,
            section_text: str,
    ) -> list[_EntrySlice]:
        lines = section_text.splitlines()
        anchors: list[
            tuple[int, DateRange]
        ] = []

        for index, line in enumerate(lines):
            date_range = extract_date_range(
                line
            )

            if date_range is not None:
                anchors.append(
                    (
                        index,
                        date_range,
                    )
                )

        if not anchors:
            return []

        header_starts: list[int] = []

        for anchor_index, _ in anchors:
            header_starts.append(
                self._find_header_start(
                    lines,
                    anchor_index,
                )
            )

        result: list[_EntrySlice] = []

        for index, (
                anchor_index,
                date_range,
        ) in enumerate(anchors):
            start = header_starts[index]

            end = (
                header_starts[index + 1]
                if index + 1 < len(header_starts)
                else len(lines)
            )

            if end <= start:
                end = (
                    anchors[index + 1][0]
                    if index + 1 < len(anchors)
                    else len(lines)
                )

            entry_lines = tuple(
                line
                for line in lines[start:end]
            )

            result.append(
                _EntrySlice(
                    lines=entry_lines,
                    date_range=date_range,
                )
            )

        return result

    def _find_header_start(
            self,
            lines: list[str],
            anchor_index: int,
    ) -> int:
        start = anchor_index
        inspected = 0
        index = anchor_index - 1

        while (
                index >= 0
                and inspected < 3
        ):
            line = lines[index].strip()

            if not line:
                break

            if BULLET_PATTERN.match(line):
                break

            if extract_date_range(line) is not None:
                break

            if not self._looks_like_header_line(
                    line
            ):
                break

            start = index
            inspected += 1
            index -= 1

        return start

    def _parse_entry(
            self,
            entry_slice: _EntrySlice,
    ) -> tuple[
        WorkExperience | None,
        list[str],
    ]:
        raw_lines = [
            line.strip()
            for line in entry_slice.lines
            if line.strip()
        ]

        if not raw_lines:
            return None, []

        date_range = entry_slice.date_range
        header_lines: list[str] = []
        body_lines: list[str] = []
        date_seen = False

        for line in raw_lines:
            line_date = extract_date_range(
                line
            )

            if (
                    line_date is not None
                    and not date_seen
            ):
                residue = (
                        line[:line_date.start_index]
                        + " "
                        + line[line_date.end_index:]
                ).strip(" -–—|,;")

                if residue:
                    header_lines.extend(
                        self._split_inline_header(
                            residue
                        )
                    )

                date_seen = True
                continue

            if (
                    not date_seen
                    and self._looks_like_header_line(
                line
            )
            ):
                header_lines.extend(
                    self._split_inline_header(
                        line
                    )
                )
                continue

            if (
                    date_seen
                    and not body_lines
                    and len(header_lines) < 3
                    and self._looks_like_header_line(
                line
            )
                    and (
                    self._normalize_job_title(
                        line
                    )
                    is not None
                    or COMPANY_INDICATOR_PATTERN.search(
                line
            )
                    is not None
                    or (
                            header_lines
                            and self._normalize_job_title(
                        header_lines[0]
                    )
                            is not None
                    )
            )
            ):
                header_lines.extend(
                    self._split_inline_header(
                        line
                    )
                )
                continue

            body_lines.append(line)

        (
            company_name,
            job_title,
            normalized_job_title,
            location,
        ) = self._parse_header(
            header_lines
        )

        context = "\n".join(
            (
                *header_lines,
                *body_lines[
                    :EMPLOYMENT_CONTEXT_LINES
                ],
            )
        )

        employment_type = self._match_preference(
            context,
            self._employment_types,
        )
        work_mode = self._match_preference(
            context,
            self._work_modes,
        )

        responsibilities: list[str] = []
        achievements: list[str] = []
        description_lines: list[str] = []

        for line in body_lines:
            bullet = BULLET_PATTERN.match(
                line
            )

            if bullet is not None:
                value = clean_optional_text(
                    bullet.group("value"),
                    maximum_length=1_000,
                )

                if value is None:
                    continue

                if ACHIEVEMENT_PATTERN.search(
                        value
                ):
                    achievements.append(value)
                else:
                    responsibilities.append(
                        value
                    )

                continue

            if extract_date_range(line) is not None:
                continue

            description_lines.append(line)

        description = clean_optional_text(
            "\n".join(description_lines),
            maximum_length=(
                MAX_DESCRIPTION_LENGTH
            ),
        )

        scoped_text = "\n".join(raw_lines)

        (
            skills,
            tools,
            equipment,
        ) = self._skill_parser.extract_grouped_names(
            scoped_text
        )

        calculated_duration = duration_months(
            date_range.start,
            date_range.end,
        )

        warnings: list[str] = []

        if calculated_duration is None:
            warnings.append(
                "AMBIGUOUS_WORK_EXPERIENCE_DATE"
            )

        experience = WorkExperience(
            company_name=company_name,
            company_industry=None,
            job_title=job_title,
            normalized_job_title=(
                normalized_job_title
            ),
            employment_type=employment_type,
            location=location,
            work_mode=work_mode,
            start_date=date_range.start.value,
            end_date=date_range.end.value,
            current=date_range.end.current,
            duration_months=(
                calculated_duration
            ),
            description=description,
            responsibilities=stable_unique(
                responsibilities,
                maximum_items=MAX_LIST_ITEMS,
            ),
            achievements=stable_unique(
                achievements,
                maximum_items=MAX_LIST_ITEMS,
            ),
            skills=stable_unique(
                skills,
                maximum_items=100,
            ),
            tools=stable_unique(
                tools,
                maximum_items=100,
            ),
            equipment=stable_unique(
                equipment,
                maximum_items=100,
            ),
        )

        return experience, warnings

    def _parse_header(
            self,
            header_lines: list[str],
    ) -> tuple[
        str | None,
        str | None,
        str | None,
        str | None,
    ]:
        company_name: str | None = None
        job_title: str | None = None
        normalized_job_title: str | None = None
        location: str | None = None
        unclassified: list[str] = []

        for raw_value in header_lines:
            value = clean_optional_text(
                raw_value,
                maximum_length=500,
            )

            if value is None:
                continue

            labelled = LABEL_PATTERN.match(value)

            if labelled is not None:
                label = normalize_for_matching(
                    labelled.group("label")
                )

                labelled_value = clean_optional_text(
                    labelled.group("value"),
                    maximum_length=500,
                )

                if labelled_value is None:
                    continue

                if label in {
                    "company",
                    "employer",
                    "organization",
                    "organisation",
                    "công ty",
                    "đơn vị",
                }:
                    company_name = (
                            company_name
                            or labelled_value
                    )
                    continue

                if label in {
                    "position",
                    "job title",
                    "title",
                    "role",
                    "chức danh",
                    "vị trí",
                }:
                    job_title = (
                            job_title
                            or labelled_value
                    )

                    normalized_job_title = (
                            normalized_job_title
                            or self._normalize_job_title(
                        labelled_value
                    )
                    )
                    continue

                if label in {
                    "location",
                    "địa điểm",
                }:
                    location = (
                            location
                            or labelled_value
                    )
                    continue

            normalized_title = (
                self._normalize_job_title(
                    value
                )
            )

            if (
                    normalized_title is not None
                    and job_title is None
            ):
                job_title = value
                normalized_job_title = (
                    normalized_title
                )
                continue

            if (
                    COMPANY_INDICATOR_PATTERN.search(
                        value
                    )
                    and company_name is None
            ):
                company_name = value
                continue

            matched_location = (
                self._match_location(
                    value
                )
            )

            if (
                    matched_location is not None
                    and self._is_location_only(
                value,
                matched_location,
            )
            ):
                location = (
                        location
                        or matched_location
                )
                continue

            unclassified.append(value)

        if job_title is None:
            for value in list(unclassified):
                normalized = (
                    self._normalize_job_title(
                        value
                    )
                )

                if normalized is not None:
                    job_title = value
                    normalized_job_title = (
                        normalized
                    )
                    unclassified.remove(value)
                    break

        if (
                company_name is None
                and len(unclassified) == 1
                and job_title is not None
        ):
            company_name = unclassified[0]
        elif (
                company_name is None
                and len(unclassified) >= 2
        ):
            for value in unclassified:
                if self._looks_like_company(
                        value
                ):
                    company_name = value
                    break

        if location is None:
            for value in header_lines:
                matched_location = (
                    self._match_location(
                        value
                    )
                )

                if matched_location is not None:
                    location = matched_location
                    break

        return (
            company_name,
            job_title,
            normalized_job_title,
            location,
        )

    def _split_inline_header(
            self,
            value: str,
    ) -> list[str]:
        at_parts = INLINE_AT_PATTERN.split(
            value,
            maxsplit=1,
        )

        if len(at_parts) == 2:
            return [
                part.strip()
                for part in at_parts
                if part.strip()
            ]

        parts = INLINE_SEPARATOR_PATTERN.split(
            value
        )

        return [
            part.strip()
            for part in parts
            if part.strip()
        ]

    def _normalize_job_title(
            self,
            value: str,
    ) -> str | None:
        normalized = normalize_for_matching(
            value
        )

        candidates: list[
            tuple[int, int, str]
        ] = []

        for item in self._job_titles:
            for alias in item.aliases:
                normalized_alias = (
                    normalize_for_matching(
                        alias
                    )
                )

                pattern = re.compile(
                    rf"(?<![\w])"
                    rf"{re.escape(normalized_alias)}"
                    rf"(?![\w])",
                    re.UNICODE,
                )

                match = pattern.search(
                    normalized
                )

                if match is None:
                    continue

                residue = (
                        normalized[:match.start()]
                        + " "
                        + normalized[match.end():]
                ).strip(" -–—|,/")

                if not self._is_safe_title_residue(
                        residue
                ):
                    continue

                exact_rank = (
                    0
                    if normalized == normalized_alias
                    else 1
                )

                candidates.append(
                    (
                        exact_rank,
                        -len(normalized_alias),
                        item.canonical,
                    )
                )

        if not candidates:
            return None

        candidates.sort()

        return candidates[0][2]

    @staticmethod
    def _is_safe_title_residue(
            value: str,
    ) -> bool:
        if not value:
            return True

        modifiers = {
            "senior",
            "junior",
            "lead",
            "principal",
            "chief",
            "head",
            "assistant",
            "associate",
            "staff",
            "executive",
            "registered",
            "general",
            "cost",
            "cao cấp",
            "trưởng",
            "phó",
            "chính",
            "tổng hợp",
        }

        return all(
            part in modifiers
            for part in value.split()
        )

    def _match_location(
            self,
            value: str,
    ) -> str | None:
        normalized = normalize_for_matching(
            value
        )

        for item in self._locations:
            for alias in sorted(
                    item.aliases,
                    key=len,
                    reverse=True,
            ):
                normalized_alias = (
                    normalize_for_matching(
                        alias
                    )
                )

                if re.search(
                        rf"(?<![\w])"
                        rf"{re.escape(normalized_alias)}"
                        rf"(?![\w])",
                        normalized,
                        re.UNICODE,
                ):
                    return item.canonical

        return None

    @staticmethod
    def _is_location_only(
            value: str,
            canonical_location: str,
    ) -> bool:
        normalized_value = (
            normalize_for_matching(value)
            .strip(" ,|-–—")
        )

        normalized_location = (
            normalize_for_matching(
                canonical_location
            )
        )

        return (
                normalized_value
                == normalized_location
                or len(value.split()) <= 5
        )

    @staticmethod
    def _looks_like_company(
            value: str,
    ) -> bool:
        return (
                COMPANY_INDICATOR_PATTERN.search(
                    value
                )
                is not None
                or (
                        2 <= len(value.split()) <= 10
                        and not SENTENCE_END_PATTERN.search(
                    value
                )
                )
        )

    @staticmethod
    def _looks_like_header_line(
            value: str,
    ) -> bool:
        if (
                not value
                or len(value) > MAX_HEADER_LINE_LENGTH
        ):
            return False

        if BULLET_PATTERN.match(value):
            return False

        if SENTENCE_END_PATTERN.search(value):
            return False

        return len(value.split()) <= 18

    @staticmethod
    def _match_preference(
            text: str,
            mapping: Mapping[
                str,
                tuple[str, ...],
            ],
    ) -> str | None:
        normalized = normalize_for_matching(
            text
        )

        for canonical, aliases in mapping.items():
            for alias in sorted(
                    aliases,
                    key=len,
                    reverse=True,
            ):
                normalized_alias = (
                    normalize_for_matching(
                        alias
                    )
                )

                if re.search(
                        rf"(?<![\w])"
                        rf"{re.escape(normalized_alias)}"
                        rf"(?![\w])",
                        normalized,
                        re.UNICODE,
                ):
                    return canonical

        return None