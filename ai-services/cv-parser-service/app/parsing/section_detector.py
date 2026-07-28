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
        self._heading_lookup = self._build_heading_lookup(
            taxonomy
        )

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

                if len(content) > self._settings.max_section_chars:
                    content = content[
                        : self._settings.max_section_chars
                    ].rstrip()
                    warnings.append("TRUNCATED_SECTION_TEXT")

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

        unclassified_count = self._count_unclassified_heading_lines(
            raw_text,
            matches,
        )

        if unclassified_count > 0:
            warnings.append("UNCLASSIFIED_SECTIONS_PRESENT")

        return DetectedSections(
            sections=tuple(sections),
            section_texts={
                section_type: tuple(values)
                for section_type, values in section_texts.items()
            },
            warnings=tuple(dict.fromkeys(warnings)),
        )

    def _find_heading_matches(
            self,
            raw_text: str,
    ) -> list[_HeadingMatch]:
        matches: list[_HeadingMatch] = []
        offset = 0

        for line in raw_text.splitlines(keepends=True):
            raw_line = line.rstrip("\r\n")
            heading_candidate = self._normalize_heading_candidate(
                raw_line
            )

            section_type = self._heading_lookup.get(
                heading_candidate
            )

            if section_type is not None:
                content_start = offset + len(line)

                matches.append(
                    _HeadingMatch(
                        section_type=section_type,
                        heading=raw_line.strip(),
                        line_start=offset,
                        heading_end=offset + len(raw_line),
                        content_start=content_start,
                    )
                )

            offset += len(line)

        return self._deduplicate_adjacent_matches(matches)

    @staticmethod
    def _deduplicate_adjacent_matches(
            matches: list[_HeadingMatch],
    ) -> list[_HeadingMatch]:
        result: list[_HeadingMatch] = []

        for match in matches:
            if (
                    result
                    and result[-1].line_start == match.line_start
            ):
                continue

            result.append(match)

        return result

    def _build_heading_lookup(
            self,
            taxonomy: TaxonomyBundle,
    ) -> dict[str, str]:
        result: dict[str, str] = {}

        for section in taxonomy.sections:
            for heading in section.headings:
                normalized = self._normalize_heading_candidate(
                    heading
                )
                result[normalized] = section.section_type

        return result

    @staticmethod
    def _normalize_heading_candidate(
            value: str,
    ) -> str:
        candidate = value.strip()

        if not candidate or len(candidate) > MAX_HEADING_LENGTH:
            return ""

        candidate = HEADING_NUMBER_PREFIX_PATTERN.sub(
            "",
            candidate,
        )
        candidate = TRAILING_HEADING_PUNCTUATION_PATTERN.sub(
            "",
            candidate,
        )

        return normalize_for_matching(candidate)

    def _count_unclassified_heading_lines(
            self,
            raw_text: str,
            matches: list[_HeadingMatch],
    ) -> int:
        matched_offsets = {
            match.line_start
            for match in matches
        }

        count = 0
        offset = 0

        for line in raw_text.splitlines(keepends=True):
            stripped = line.strip()

            if (
                    offset not in matched_offsets
                    and self._looks_like_heading(stripped)
            ):
                count += 1

            offset += len(line)

        return count

    @staticmethod
    def _looks_like_heading(
            value: str,
    ) -> bool:
        if not value or len(value) > 80:
            return False

        if any(character in value for character in ".!?"):
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

        title_case_ratio = sum(
            1
            for word in words
            if word[:1].isupper()
        ) / len(words)

        return uppercase_ratio >= 0.75 or title_case_ratio >= 0.9