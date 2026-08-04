from __future__ import annotations

import re
from dataclasses import dataclass

from app.config import Settings
from app.normalization.text_normalizer import (
    normalize_for_matching,
)
from app.schemas import ParsedSection
from app.taxonomy.taxonomy_loader import TaxonomyBundle


TRAILING_HEADING_PUNCTUATION_PATTERN = re.compile(
    r"[\s:：\-–—|]+$"
)

HEADING_NUMBER_PREFIX_PATTERN = re.compile(
    r"^\s*(?:\d{1,2}[.)]|[IVX]{1,5}[.)])\s+",
    re.IGNORECASE,
)

MAX_HEADING_LENGTH = 100
MIN_LETTER_SPACED_HEADING_LETTERS = 3
MIN_UNCLASSIFIED_HEADING_UPPERCASE_RATIO = 0.85


@dataclass(frozen=True, slots=True)
class DetectedSections:
    sections: tuple[ParsedSection, ...]
    section_texts: dict[str, tuple[str, ...]]
    warnings: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class _HeadingMatch:
    section_type: str
    heading: str
    line_start: int
    heading_end: int
    content_start: int


class SectionDetector:
    def __init__(
            self,
            settings: Settings,
            taxonomy: TaxonomyBundle,
    ) -> None:
        self._settings = settings

        (
            self._heading_lookup,
            self._compact_heading_lookup,
        ) = self._build_heading_lookups(taxonomy)

    def detect(
            self,
            raw_text: str,
    ) -> DetectedSections:
        matches = self._find_heading_matches(raw_text)

        sections: list[ParsedSection] = []
        section_texts: dict[str, list[str]] = {}
        warnings: list[str] = []

        if matches:
            first_heading = matches[0]

            if first_heading.line_start > 0:
                header_text = raw_text[
                    : first_heading.line_start
                ].strip()

                if header_text:
                    sections.append(
                        ParsedSection(
                            sectionType="HEADER",
                            heading=None,
                            startOffset=0,
                            endOffset=first_heading.line_start,
                            text=None,
                        )
                    )
                    section_texts.setdefault(
                        "HEADER",
                        [],
                    ).append(header_text)

            for index, match in enumerate(matches):
                next_start = (
                    matches[index + 1].line_start
                    if index + 1 < len(matches)
                    else len(raw_text)
                )

                content = raw_text[
                    match.content_start:next_start
                ].strip()

                if (
                        len(content)
                        > self._settings.max_section_chars
                ):
                    content = content[
                        : self._settings.max_section_chars
                    ].rstrip()

                    warnings.append(
                        "TRUNCATED_SECTION_TEXT"
                    )

                sections.append(
                    ParsedSection(
                        sectionType=match.section_type,
                        heading=match.heading,
                        startOffset=match.line_start,
                        endOffset=next_start,
                        text=None,
                    )
                )

                section_texts.setdefault(
                    match.section_type,
                    [],
                ).append(content)
        else:
            sections.append(
                ParsedSection(
                    sectionType="HEADER",
                    heading=None,
                    startOffset=0,
                    endOffset=len(raw_text),
                    text=None,
                )
            )

            section_texts["HEADER"] = [raw_text]

        unclassified_count = (
            self._count_unclassified_heading_lines(
                raw_text,
                matches,
            )
        )

        if unclassified_count > 0:
            warnings.append(
                "UNCLASSIFIED_SECTIONS_PRESENT"
            )

        return DetectedSections(
            sections=tuple(sections),
            section_texts={
                section_type: tuple(values)
                for section_type, values
                in section_texts.items()
            },
            warnings=tuple(
                dict.fromkeys(warnings)
            ),
        )

    def _find_heading_matches(
            self,
            raw_text: str,
    ) -> list[_HeadingMatch]:
        matches: list[_HeadingMatch] = []
        offset = 0

        for line in raw_text.splitlines(
                keepends=True
        ):
            raw_line = line.rstrip("\r\n")

            heading_candidate = (
                self._normalize_heading_candidate(
                    raw_line
                )
            )

            section_type = self._heading_lookup.get(
                heading_candidate
            )

            if (
                    section_type is None
                    and self._is_letter_spaced_heading(
                heading_candidate
            )
            ):
                compact_key = (
                    self._compact_heading_key(
                        heading_candidate
                    )
                )

                section_type = (
                    self._compact_heading_lookup.get(
                        compact_key
                    )
                )

            if section_type is not None:
                content_start = offset + len(line)

                matches.append(
                    _HeadingMatch(
                        section_type=section_type,
                        heading=raw_line.strip(),
                        line_start=offset,
                        heading_end=(
                                offset + len(raw_line)
                        ),
                        content_start=content_start,
                    )
                )

            offset += len(line)

        return self._deduplicate_adjacent_matches(
            matches
        )

    @staticmethod
    def _deduplicate_adjacent_matches(
            matches: list[_HeadingMatch],
    ) -> list[_HeadingMatch]:
        result: list[_HeadingMatch] = []

        for match in matches:
            if (
                    result
                    and result[-1].line_start
                    == match.line_start
            ):
                continue

            result.append(match)

        return result

    def _build_heading_lookups(
            self,
            taxonomy: TaxonomyBundle,
    ) -> tuple[
        dict[str, str],
        dict[str, str],
    ]:
        regular_lookup: dict[str, str] = {}

        compact_candidates: dict[
            str,
            set[str],
        ] = {}

        for section in taxonomy.sections:
            for heading in section.headings:
                normalized = (
                    self._normalize_heading_candidate(
                        heading
                    )
                )

                if not normalized:
                    continue

                regular_lookup[normalized] = (
                    section.section_type
                )

                compact_key = (
                    self._compact_heading_key(
                        normalized
                    )
                )

                if not compact_key:
                    continue

                compact_candidates.setdefault(
                    compact_key,
                    set(),
                ).add(section.section_type)

        # Chỉ giữ compact key ánh xạ duy nhất tới một section type.
        # Nếu taxonomy có collision, compact matching sẽ không đoán bừa.
        compact_lookup = {
            compact_key: next(
                iter(section_types)
            )
            for compact_key, section_types
            in compact_candidates.items()
            if len(section_types) == 1
        }

        return regular_lookup, compact_lookup

    @staticmethod
    def _normalize_heading_candidate(
            value: str,
    ) -> str:
        candidate = value.strip()

        if (
                not candidate
                or len(candidate) > MAX_HEADING_LENGTH
        ):
            return ""

        candidate = (
            HEADING_NUMBER_PREFIX_PATTERN.sub(
                "",
                candidate,
            )
        )

        candidate = (
            TRAILING_HEADING_PUNCTUATION_PATTERN.sub(
                "",
                candidate,
            )
        )

        return normalize_for_matching(
            candidate
        )

    @staticmethod
    def _is_letter_spaced_heading(
            normalized_value: str,
    ) -> bool:
        tokens = normalized_value.split()

        if (
                len(tokens)
                < MIN_LETTER_SPACED_HEADING_LETTERS
        ):
            return False

        return all(
            len(token) == 1
            and token.isalpha()
            for token in tokens
        )

    @staticmethod
    def _compact_heading_key(
            normalized_value: str,
    ) -> str:
        return "".join(
            character
            for character in normalized_value
            if not character.isspace()
        )

    def _count_unclassified_heading_lines(
            self,
            raw_text: str,
            matches: list[_HeadingMatch],
    ) -> int:
        matched_offsets = {
            match.line_start
            for match in matches
        }

        first_non_empty_offset = (
            self._first_non_empty_line_offset(
                raw_text
            )
        )

        count = 0
        offset = 0

        for line in raw_text.splitlines(
                keepends=True
        ):
            stripped = line.strip()

            is_first_identity_line = (
                    first_non_empty_offset is not None
                    and offset == first_non_empty_offset
            )

            if (
                    offset not in matched_offsets
                    and not is_first_identity_line
                    and self._looks_like_heading(
                stripped
            )
            ):
                count += 1

            offset += len(line)

        return count

    @staticmethod
    def _first_non_empty_line_offset(
            raw_text: str,
    ) -> int | None:
        offset = 0

        for line in raw_text.splitlines(
                keepends=True
        ):
            if line.strip():
                return offset

            offset += len(line)

        return None

    @staticmethod
    def _looks_like_heading(
            value: str,
    ) -> bool:
        if not value or len(value) > 80:
            return False

        if any(
                character in value
                for character in ".!?"
        ):
            return False

        words = value.split()

        if not 1 <= len(words) <= 8:
            return False

        letters = [
            character
            for character in value
            if character.isalpha()
        ]

        if len(letters) < 3:
            return False

        uppercase_ratio = sum(
            1
            for character in letters
            if character.isupper()
        ) / len(letters)

        return (
                uppercase_ratio
                >= MIN_UNCLASSIFIED_HEADING_UPPERCASE_RATIO
        )