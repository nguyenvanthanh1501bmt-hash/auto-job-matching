from __future__ import annotations

import re
from dataclasses import dataclass

from app.config import Settings
from app.normalization.text_normalizer import normalize_for_matching
from app.normalization.value_normalizer import clean_optional_text
from app.schemas import Skill
from app.taxonomy.taxonomy_loader import TaxonomyBundle


SKILL_SPLIT_PATTERN = re.compile(
    r"\s*(?:[,;|•●▪◦‣∙·]|\s+/\s+)\s*"
)
LEADING_BULLET_PATTERN = re.compile(
    r"^\s*(?:[-*]|\d{1,2}[.)])\s*"
)
SKILL_GROUP_LABEL_PATTERN = re.compile(
    r"^(?:"
    r"skills?|key\s+skills?|core\s+skills?|competencies|"
    r"technical\s+skills?|professional\s+skills?|"
    r"programming\s+languages?|languages?|"
    r"frameworks?|libraries?|libraries\s+and\s+frameworks?|"
    r"databases?|database\s+management\s+systems?|"
    r"tools?|development\s+tools?|technologies?|"
    r"cloud|platforms?|soft\s+skills?|"
    r"kỹ\s+năng(?:\s+(?:chuyên\s+môn|kỹ\s+thuật|mềm|cá\s+nhân))?|"
    r"ky\s+nang(?:\s+(?:chuyen\s+mon|ky\s+thuat|mem|ca\s+nhan))?|"
    r"năng\s+lực|nang\s+luc|chuyên\s+môn|chuyen\s+mon|"
    r"ngôn\s+ngữ(?:\s+lập\s+trình)?|"
    r"ngon\s+ngu(?:\s+lap\s+trinh)?|"
    r"thư\s+viện(?:\s+và\s+framework)?|"
    r"thu\s+vien(?:\s+va\s+framework)?|"
    r"framework|"
    r"cơ\s+sở\s+dữ\s+liệu|co\s+so\s+du\s+lieu|"
    r"hệ\s+quản\s+trị\s+cơ\s+sở\s+dữ\s+liệu|"
    r"he\s+quan\s+tri\s+co\s+so\s+du\s+lieu|"
    r"công\s+cụ(?:\s+phát\s+triển)?|"
    r"cong\s+cu(?:\s+phat\s+trien)?|"
    r"công\s+nghệ|cong\s+nghe"
    r")\s*[:：-]\s*",
    re.IGNORECASE,
)
YEAR_OR_DATE_PATTERN = re.compile(
    r"\b(?:19|20)\d{2}\b"
    r"|\b\d{1,2}[/.-]\d{4}\b"
)
EMAIL_OR_URL_PATTERN = re.compile(
    r"@|(?:https?://|www\.)",
    re.IGNORECASE,
)
SENTENCE_PUNCTUATION_PATTERN = re.compile(
    r"[.!?]"
)
NUMERIC_ONLY_PATTERN = re.compile(
    r"^[\d\s.+/%-]+$"
)

GENERIC_SKILL_VALUES = {
    "skills",
    "skill",
    "key skills",
    "core skills",
    "professional skills",
    "technical skills",
    "competencies",
    "expertise",
    "kỹ năng",
    "kỹ năng chuyên môn",
    "năng lực",
    "chuyên môn",
    "other",
    "others",
    "khác",
}

TOOL_CATEGORIES = {
    "SOFTWARE",
    "TOOL",
}

EQUIPMENT_CATEGORIES = {
    "EQUIPMENT",
    "MACHINERY",
}


@dataclass(frozen=True, slots=True)
class SkillMatch:
    name: str
    normalized_name: str
    category: str
    proficiency_text: str | None
    normalized_proficiency: str | None
    evidence_source: str
    position: int


@dataclass(frozen=True, slots=True)
class SkillParseResult:
    skills: tuple[Skill, ...]
    warnings: tuple[str, ...]


class SkillParser:
    def __init__(
            self,
            settings: Settings,
            taxonomy: TaxonomyBundle,
    ) -> None:
        self._settings = settings
        self._skills = taxonomy.skills
        self._language_levels = (
            taxonomy.language_levels
        )

    def parse(
            self,
            raw_text: str,
            section_texts: dict[str, tuple[str, ...]],
    ) -> SkillParseResult:
        matches: list[SkillMatch] = []

        for section in section_texts.get(
                "SKILLS",
                (),
        ):
            matches.extend(
                self._match_taxonomy(
                    section,
                    "SKILLS_SECTION",
                )
            )
            matches.extend(
                self._extract_unknown_skills(
                    section
                )
            )

        for section in section_texts.get(
                "WORK_EXPERIENCE",
                (),
        ):
            matches.extend(
                self._match_taxonomy(
                    section,
                    "WORK_EXPERIENCE",
                )
            )

        for section in section_texts.get(
                "PROJECTS",
                (),
        ):
            matches.extend(
                self._match_taxonomy(
                    section,
                    "PROJECTS",
                )
            )

        if not matches:
            header_and_summary = "\n".join(
                (
                    *section_texts.get(
                        "HEADER",
                        (),
                    ),
                    *section_texts.get(
                        "SUMMARY",
                        (),
                    ),
                )
            )

            matches.extend(
                self._match_taxonomy(
                    header_and_summary,
                    "PROFILE_TEXT",
                )
            )

        skills = self._merge_matches(matches)

        warnings: list[str] = []

        taxonomy_count = sum(
            1
            for skill in skills
            if skill.category != "OTHER"
        )

        if (
                skills
                and taxonomy_count / len(skills) < 0.4
        ):
            warnings.append(
                "SKILL_TAXONOMY_MATCH_LOW"
            )

        return SkillParseResult(
            skills=tuple(
                skills[
                    :self._settings.max_skills
                ]
            ),
            warnings=tuple(warnings),
        )

    def extract_names(
            self,
            text: str,
            *,
            categories: set[str] | None = None,
            maximum_items: int = 100,
    ) -> list[str]:
        result: list[str] = []
        seen: set[str] = set()

        for match in self._match_taxonomy(
                text,
                "SCOPED_TEXT",
        ):
            if (
                    categories is not None
                    and match.category not in categories
            ):
                continue

            key = match.normalized_name.casefold()

            if key in seen:
                continue

            seen.add(key)
            result.append(
                match.normalized_name
            )

            if len(result) >= maximum_items:
                break

        return result

    def extract_grouped_names(
            self,
            text: str,
            *,
            maximum_items: int = 100,
    ) -> tuple[list[str], list[str], list[str]]:
        matches = self._match_taxonomy(
            text,
            "SCOPED_TEXT",
        )

        skills: list[str] = []
        tools: list[str] = []
        equipment: list[str] = []
        seen: set[str] = set()

        for match in matches:
            key = match.normalized_name.casefold()

            if key in seen:
                continue

            seen.add(key)

            if match.category in TOOL_CATEGORIES:
                tools.append(
                    match.normalized_name
                )
            elif match.category in EQUIPMENT_CATEGORIES:
                equipment.append(
                    match.normalized_name
                )
            else:
                skills.append(
                    match.normalized_name
                )

            if len(seen) >= maximum_items:
                break

        return skills, tools, equipment

    def _match_taxonomy(
            self,
            text: str,
            evidence_source: str,
    ) -> list[SkillMatch]:
        normalized_text = normalize_for_matching(
            text
        )

        if not normalized_text:
            return []

        span_matches: list[
            tuple[SkillMatch, int, int]
        ] = []

        for item in self._skills:
            seen_item_spans: set[
                tuple[int, int]
            ] = set()

            for alias in sorted(
                    item.aliases,
                    key=len,
                    reverse=True,
            ):
                normalized_alias = (
                    normalize_for_matching(alias)
                )

                pattern = self._phrase_pattern(
                    normalized_alias
                )

                for match in pattern.finditer(
                        normalized_text
                ):
                    span = (
                        match.start(),
                        match.end(),
                    )

                    if span in seen_item_spans:
                        continue

                    seen_item_spans.add(span)

                    source_line = (
                        self._line_containing_normalized_phrase(
                            text,
                            alias,
                        )
                    )

                    (
                        proficiency_text,
                        normalized_proficiency,
                    ) = self._extract_proficiency(
                        source_line,
                        alias,
                    )

                    span_matches.append(
                        (
                            SkillMatch(
                                name=item.canonical,
                                normalized_name=(
                                    item.canonical
                                ),
                                category=item.category,
                                proficiency_text=(
                                    proficiency_text
                                ),
                                normalized_proficiency=(
                                    normalized_proficiency
                                ),
                                evidence_source=(
                                    evidence_source
                                ),
                                position=match.start(),
                            ),
                            match.start(),
                            match.end(),
                        )
                    )

        selected: list[
            tuple[SkillMatch, int, int]
        ] = []

        for candidate in sorted(
                span_matches,
                key=lambda item: (
                        item[1],
                        -(item[2] - item[1]),
                        item[0].normalized_name.casefold(),
                ),
        ):
            _, start, end = candidate

            if any(
                    start >= selected_start
                    and end <= selected_end
                    and (start, end)
                    != (selected_start, selected_end)
                    for _, selected_start, selected_end
                    in selected
            ):
                continue

            selected.append(candidate)

        result: list[SkillMatch] = []
        seen_names: set[str] = set()

        for skill_match, _, _ in sorted(
                selected,
                key=lambda candidate: (
                        candidate[0].position,
                        candidate[0].normalized_name.casefold(),
                ),
        ):
            key = skill_match.normalized_name.casefold()

            if key in seen_names:
                continue

            seen_names.add(key)
            result.append(skill_match)

        return result

    def _extract_unknown_skills(
            self,
            section_text: str,
    ) -> list[SkillMatch]:
        result: list[SkillMatch] = []

        known_names = {
            item.canonical.casefold()
            for item in self._skills
        }

        position = 0

        for raw_line in section_text.splitlines():
            line = LEADING_BULLET_PATTERN.sub(
                "",
                raw_line,
            ).strip()

            line = SKILL_GROUP_LABEL_PATTERN.sub(
                "",
                line,
            ).strip()

            if not line:
                continue

            parts = SKILL_SPLIT_PATTERN.split(
                line
            )

            if (
                    len(parts) == 1
                    and self._looks_like_sentence(
                line
            )
            ):
                position += len(raw_line) + 1
                continue

            for part in parts:
                candidate = (
                    self._clean_unknown_candidate(
                        part
                    )
                )

                if candidate is None:
                    continue

                normalized_candidate = (
                    normalize_for_matching(
                        candidate
                    )
                )

                if (
                        normalized_candidate
                        in GENERIC_SKILL_VALUES
                ):
                    continue

                (
                    proficiency_text,
                    normalized_proficiency,
                ) = self._extract_proficiency(
                    candidate,
                    None,
                )

                name = (
                    self._remove_trailing_proficiency(
                        candidate
                    )
                )

                name = clean_optional_text(
                    name,
                    maximum_length=300,
                )

                if (
                        name is not None
                        and name.casefold() in known_names
                ):
                    continue

                if (
                        name is not None
                        and self._matches_any_taxonomy(
                    name
                )
                ):
                    continue

                if name is None:
                    continue

                result.append(
                    SkillMatch(
                        name=name,
                        normalized_name=name,
                        category="OTHER",
                        proficiency_text=(
                            proficiency_text
                        ),
                        normalized_proficiency=(
                            normalized_proficiency
                        ),
                        evidence_source=(
                            "SKILLS_SECTION"
                        ),
                        position=position,
                    )
                )

                position += 1

            position += len(raw_line) + 1

        return result

    def _merge_matches(
            self,
            matches: list[SkillMatch],
    ) -> list[Skill]:
        ordered = sorted(
            matches,
            key=lambda match: (
                self._evidence_priority(
                    match.evidence_source
                ),
                match.position,
                match.normalized_name.casefold(),
            ),
        )

        merged: dict[str, Skill] = {}

        for match in ordered:
            key = (
                match.normalized_name.casefold()
            )
            existing = merged.get(key)

            if existing is None:
                merged[key] = Skill(
                    name=match.name,
                    normalized_name=(
                        match.normalized_name
                    ),
                    category=match.category,
                    proficiency_text=(
                        match.proficiency_text
                    ),
                    normalized_proficiency=(
                        match.normalized_proficiency
                    ),
                    years_of_experience=None,
                    last_used_date=None,
                    evidence_sources=[
                        match.evidence_source
                    ],
                )
                continue

            evidence_sources = list(
                existing.evidence_sources
            )

            if (
                    match.evidence_source
                    not in evidence_sources
            ):
                evidence_sources.append(
                    match.evidence_source
                )

            existing.evidence_sources = (
                evidence_sources
            )

            if (
                    existing.proficiency_text is None
                    and match.proficiency_text is not None
            ):
                existing.proficiency_text = (
                    match.proficiency_text
                )
                existing.normalized_proficiency = (
                    match.normalized_proficiency
                )

            if (
                    existing.category == "OTHER"
                    and match.category != "OTHER"
            ):
                existing.category = match.category
                existing.name = match.name
                existing.normalized_name = (
                    match.normalized_name
                )

        return list(merged.values())

    def _extract_proficiency(
            self,
            source_text: str,
            alias: str | None,
    ) -> tuple[str | None, str | None]:
        normalized_source = normalize_for_matching(
            source_text
        )

        if not normalized_source:
            return None, None

        search_scope = normalized_source

        if alias is not None:
            normalized_alias = (
                normalize_for_matching(alias)
            )
            alias_index = normalized_source.find(
                normalized_alias
            )

            if alias_index < 0:
                return None, None

            tail = normalized_source[
                alias_index
                + len(normalized_alias):
            ]

            tail = re.split(
                r"[,;|•●▪◦‣∙·]",
                tail,
                maxsplit=1,
            )[0]

            search_scope = tail[:60].strip()

            if (
                    not search_scope
                    or not re.match(
                r"^[-–—:：()]",
                tail.strip(),
            )
            ):
                return None, None

        for level in self._language_levels:
            if level.canonical == "UNKNOWN":
                continue

            for level_alias in sorted(
                    level.aliases,
                    key=len,
                    reverse=True,
            ):
                normalized_level = (
                    normalize_for_matching(
                        level_alias
                    )
                )

                if self._phrase_pattern(
                        normalized_level
                ).search(search_scope):
                    return (
                        level_alias,
                        level.canonical,
                    )

        return None, None

    def _remove_trailing_proficiency(
            self,
            value: str,
    ) -> str:
        result = value

        for level in self._language_levels:
            if level.canonical == "UNKNOWN":
                continue

            for alias in sorted(
                    level.aliases,
                    key=len,
                    reverse=True,
            ):
                result = re.sub(
                    rf"\s*(?:[-–—:：()]|\s)+"
                    rf"{re.escape(alias)}\s*\)?$",
                    "",
                    result,
                    flags=re.IGNORECASE,
                )

        return result.strip(
            " -–—:：()"
        )

    def _matches_any_taxonomy(
            self,
            value: str,
    ) -> bool:
        normalized = normalize_for_matching(
            value
        )

        for item in self._skills:
            for alias in item.aliases:
                if (
                        normalized
                        == normalize_for_matching(
                    alias
                )
                ):
                    return True

        return False

    @staticmethod
    def _phrase_pattern(
            normalized_phrase: str,
    ) -> re.Pattern[str]:
        return re.compile(
            rf"(?<![\w])"
            rf"{re.escape(normalized_phrase)}"
            rf"(?![\w])",
            re.UNICODE,
        )

    @staticmethod
    def _line_containing_normalized_phrase(
            text: str,
            alias: str,
    ) -> str:
        normalized_alias = (
            normalize_for_matching(alias)
        )

        for line in text.splitlines():
            if (
                    normalized_alias
                    in normalize_for_matching(line)
            ):
                return line

        return text[:500]

    @staticmethod
    def _clean_unknown_candidate(
            value: str,
    ) -> str | None:
        cleaned = clean_optional_text(
            value,
            maximum_length=300,
        )

        if cleaned is None:
            return None

        if (
                len(cleaned) < 2
                or len(cleaned.split()) > 8
        ):
            return None

        if YEAR_OR_DATE_PATTERN.search(cleaned):
            return None

        if EMAIL_OR_URL_PATTERN.search(cleaned):
            return None

        if NUMERIC_ONLY_PATTERN.fullmatch(cleaned):
            return None

        letters = sum(
            1
            for character in cleaned
            if character.isalpha()
        )

        if (
                letters < 2
                or letters / max(len(cleaned), 1) < 0.45
        ):
            return None

        return cleaned

    @staticmethod
    def _looks_like_sentence(
            value: str,
    ) -> bool:
        return (
                len(value) > 120
                or len(value.split()) > 14
                or SENTENCE_PUNCTUATION_PATTERN.search(
            value
        )
                is not None
        )

    @staticmethod
    def _evidence_priority(
            value: str,
    ) -> int:
        priorities = {
            "SKILLS_SECTION": 0,
            "WORK_EXPERIENCE": 1,
            "PROJECTS": 2,
            "PROFILE_TEXT": 3,
            "SCOPED_TEXT": 4,
        }

        return priorities.get(
            value,
            99,
        )