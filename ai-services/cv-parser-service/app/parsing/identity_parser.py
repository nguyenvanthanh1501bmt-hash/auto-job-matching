from __future__ import annotations

import re
from dataclasses import dataclass

from app.normalization.text_normalizer import normalize_for_matching
from app.normalization.value_normalizer import clean_optional_text, stable_unique
from app.taxonomy.taxonomy_loader import TaxonomyBundle


EMAIL_MARKER_PATTERN = re.compile(r"@")
PHONE_LIKE_PATTERN = re.compile(r"\+?\d[\d\s().-]{7,}\d")
URL_MARKER_PATTERN = re.compile(r"(?:https?://|www\.)", re.IGNORECASE)
DATE_LIKE_PATTERN = re.compile(
    r"(?:\b(?:19|20)\d{2}\b|\b\d{1,2}[/.-]\d{1,2}[/.-](?:19|20)?\d{2}\b)"
)
LABELLED_NAME_PATTERN = re.compile(
    r"^(?:full\s*name|name|candidate\s*name|họ\s*(?:và\s*)?tên|họ\s*tên)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)
TARGET_JOB_LABEL_PATTERN = re.compile(
    r"^(?:desired\s+(?:position|role|job)|target\s+(?:position|role|job)|"
    r"position\s+applied\s+for|applying\s+for|career\s+target|"
    r"vị\s+trí\s+(?:mong\s+muốn|ứng\s+tuyển)|công\s+việc\s+mong\s+muốn)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)
TARGET_INDUSTRY_LABEL_PATTERN = re.compile(
    r"^(?:desired\s+industr(?:y|ies)|target\s+industr(?:y|ies)|"
    r"preferred\s+industr(?:y|ies)|ngành\s+nghề\s+mong\s+muốn|"
    r"lĩnh\s+vực\s+mong\s+muốn)\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)
LIST_SEPARATOR_PATTERN = re.compile(r"\s*(?:[,;|]|\s+/\s+)\s*")
SENTENCE_END_PATTERN = re.compile(r"[.!?]$")
MULTI_SPACE_PATTERN = re.compile(r"\s+")

GENERIC_HEADINGS = {
    "resume",
    "curriculum vitae",
    "cv",
    "profile",
    "summary",
    "professional summary",
    "career objective",
    "objective",
    "personal information",
    "contact information",
    "experience",
    "work experience",
    "education",
    "skills",
    "hồ sơ xin việc",
    "sơ yếu lý lịch",
    "thông tin cá nhân",
    "thông tin liên hệ",
    "kinh nghiệm",
    "kinh nghiệm làm việc",
    "học vấn",
    "kỹ năng",
    "mục tiêu nghề nghiệp",
}

NAME_REJECT_WORDS = {
    "accountant",
    "administrator",
    "analyst",
    "architect",
    "assistant",
    "chef",
    "consultant",
    "developer",
    "director",
    "driver",
    "engineer",
    "executive",
    "intern",
    "lawyer",
    "manager",
    "nurse",
    "officer",
    "operator",
    "pharmacist",
    "representative",
    "specialist",
    "supervisor",
    "teacher",
    "technician",
    "trưởng",
    "quản lý",
    "kỹ sư",
    "nhân viên",
    "chuyên viên",
    "giáo viên",
    "điều dưỡng",
    "dược sĩ",
    "kế toán",
}

SUMMARY_MAX_LENGTH = 3_000
OBJECTIVE_MAX_LENGTH = 2_000
HEADLINE_MAX_LENGTH = 300
NAME_MAX_LENGTH = 200
TARGET_MAX_ITEMS = 10


@dataclass(frozen=True, slots=True)
class IdentityParseResult:
    full_name: str | None
    headline: str | None
    professional_summary: str | None
    career_objective: str | None
    target_job_titles: tuple[str, ...]
    target_industries: tuple[str, ...]
    warnings: tuple[str, ...]


class IdentityParser:
    def __init__(self, taxonomy: TaxonomyBundle) -> None:
        self._job_titles = taxonomy.job_titles
        self._section_headings = {
            normalize_for_matching(heading)
            for item in taxonomy.sections
            for heading in item.headings
        }

    def parse(
            self,
            raw_text: str,
            section_texts: dict[str, tuple[str, ...]],
    ) -> IdentityParseResult:
        header_text = self._header_text(raw_text, section_texts)
        header_lines = self._meaningful_lines(header_text)

        full_name = self._extract_full_name(header_lines)
        headline = self._extract_headline(header_lines, full_name)
        professional_summary = self._extract_section_text(
            section_texts.get("SUMMARY", ()),
            maximum_length=SUMMARY_MAX_LENGTH,
        )
        career_objective = self._extract_section_text(
            section_texts.get("OBJECTIVE", ()),
            maximum_length=OBJECTIVE_MAX_LENGTH,
        )

        target_job_titles = self._extract_target_values(
            (header_text, career_objective or ""),
            TARGET_JOB_LABEL_PATTERN,
        )
        target_industries = self._extract_target_values(
            (header_text, career_objective or ""),
            TARGET_INDUSTRY_LABEL_PATTERN,
        )

        if not target_job_titles and headline is not None:
            if self._normalize_job_title(headline) is not None:
                target_job_titles = [headline]

        warnings: list[str] = []

        if full_name is None:
            warnings.append("FULL_NAME_NOT_DETECTED")

        if headline is None:
            warnings.append("HEADLINE_NOT_DETECTED")

        if professional_summary is None:
            warnings.append("SUMMARY_NOT_DETECTED")

        if career_objective is None:
            warnings.append("OBJECTIVE_NOT_DETECTED")

        return IdentityParseResult(
            full_name=full_name,
            headline=headline,
            professional_summary=professional_summary,
            career_objective=career_objective,
            target_job_titles=tuple(target_job_titles),
            target_industries=tuple(target_industries),
            warnings=tuple(warnings),
        )

    @staticmethod
    def _header_text(
            raw_text: str,
            section_texts: dict[str, tuple[str, ...]],
    ) -> str:
        header_sections = section_texts.get("HEADER", ())

        if header_sections:
            return "\n".join(header_sections)

        return "\n".join(raw_text.splitlines()[:20])

    @staticmethod
    def _meaningful_lines(text: str) -> list[str]:
        result: list[str] = []

        for line in text.splitlines()[:30]:
            cleaned = clean_optional_text(
                line,
                maximum_length=500,
            )

            if cleaned is not None:
                result.append(cleaned)

        return result

    def _extract_full_name(
            self,
            lines: list[str],
    ) -> str | None:
        for line in lines:
            labelled = LABELLED_NAME_PATTERN.match(line)

            if labelled is None:
                continue

            value = clean_optional_text(
                labelled.group("value"),
                maximum_length=NAME_MAX_LENGTH,
            )

            if (
                    value is not None
                    and self._looks_like_person_name(value)
            ):
                return value

        scored: list[tuple[int, int, str]] = []

        for index, line in enumerate(lines[:12]):
            score = self._name_score(line, index)

            if score >= 5:
                scored.append(
                    (
                        score,
                        -index,
                        line,
                    )
                )

        if not scored:
            return None

        scored.sort(reverse=True)

        return clean_optional_text(
            scored[0][2],
            maximum_length=NAME_MAX_LENGTH,
        )

    def _name_score(
            self,
            value: str,
            index: int,
    ) -> int:
        if not self._looks_like_person_name(value):
            return -100

        words = value.split()
        letters = [
            character
            for character in value
            if character.isalpha()
        ]

        uppercase_ratio = (
                sum(
                    1
                    for character in letters
                    if character.isupper()
                )
                / len(letters)
        )

        title_case_ratio = (
                sum(
                    1
                    for word in words
                    if word[:1].isupper()
                )
                / len(words)
        )

        score = 4

        if index == 0:
            score += 3
        elif index <= 2:
            score += 2
        elif index <= 5:
            score += 1

        if 2 <= len(words) <= 5:
            score += 2

        if (
                uppercase_ratio >= 0.75
                or title_case_ratio >= 0.8
        ):
            score += 2

        if len(value) <= 60:
            score += 1

        return score

    def _looks_like_person_name(
            self,
            value: str,
    ) -> bool:
        normalized = normalize_for_matching(value)

        if (
                normalized in GENERIC_HEADINGS
                or normalized in self._section_headings
        ):
            return False

        if self._has_contact_or_date_marker(value):
            return False

        if SENTENCE_END_PATTERN.search(value):
            return False

        if not 2 <= len(value.split()) <= 7:
            return False

        letters = sum(
            1
            for character in value
            if character.isalpha()
        )

        if (
                letters < 4
                or letters / max(len(value), 1) < 0.65
        ):
            return False

        for character in value:
            if (
                    character.isalpha()
                    or character.isspace()
            ):
                continue

            if character in {
                "-",
                "'",
                "’",
                ".",
            }:
                continue

            return False

        padded = f" {normalized} "

        if any(
                f" {word} " in padded
                for word in NAME_REJECT_WORDS
        ):
            return False

        if self._normalize_job_title(value) is not None:
            return False

        return True

    def _extract_headline(
            self,
            lines: list[str],
            full_name: str | None,
    ) -> str | None:
        name_key = normalize_for_matching(
            full_name or ""
        )
        candidates: list[tuple[int, int, str]] = []

        for index, line in enumerate(lines[:15]):
            normalized = normalize_for_matching(line)

            if (
                    not normalized
                    or normalized == name_key
            ):
                continue

            if (
                    normalized in GENERIC_HEADINGS
                    or normalized in self._section_headings
            ):
                continue

            if self._has_contact_or_date_marker(line):
                continue

            if (
                    len(line) > HEADLINE_MAX_LENGTH
                    or len(line.split()) > 14
            ):
                continue

            if line.endswith((".", "!", "?")):
                continue

            if LABELLED_NAME_PATTERN.match(line):
                continue

            normalized_title = self._normalize_job_title(
                line
            )

            score = (
                2
                if normalized_title is not None
                else 0
            )

            if index <= 4:
                score += 2

            if 1 <= len(line.split()) <= 8:
                score += 1

            if self._contains_professional_keyword(
                    normalized
            ):
                score += 2

            if score >= 4:
                candidates.append(
                    (
                        score,
                        -index,
                        line,
                    )
                )

        if not candidates:
            return None

        candidates.sort(reverse=True)

        return clean_optional_text(
            candidates[0][2],
            maximum_length=HEADLINE_MAX_LENGTH,
        )

    @staticmethod
    def _extract_section_text(
            values: tuple[str, ...],
            maximum_length: int,
    ) -> str | None:
        if not values:
            return None

        paragraphs: list[str] = []

        for value in values:
            cleaned = clean_optional_text(
                value,
                maximum_length=maximum_length,
            )

            if cleaned is not None:
                paragraphs.append(cleaned)

        if not paragraphs:
            return None

        return clean_optional_text(
            "\n\n".join(paragraphs),
            maximum_length=maximum_length,
        )

    @staticmethod
    def _extract_target_values(
            scopes: tuple[str, ...],
            pattern: re.Pattern[str],
    ) -> list[str]:
        values: list[str] = []

        for scope in scopes:
            for line in scope.splitlines():
                match = pattern.match(
                    line.strip()
                )

                if match is None:
                    continue

                values.extend(
                    part
                    for part in LIST_SEPARATOR_PATTERN.split(
                        match.group("value")
                    )
                    if part.strip()
                )

        return stable_unique(
            values,
            maximum_items=TARGET_MAX_ITEMS,
        )

    def _normalize_job_title(
            self,
            value: str,
    ) -> str | None:
        normalized = normalize_for_matching(value)
        candidates: list[tuple[int, int, str]] = []

        for item in self._job_titles:
            for alias in item.aliases:
                alias_normalized = normalize_for_matching(
                    alias
                )

                pattern = re.compile(
                    rf"(?<![\w]){re.escape(alias_normalized)}(?![\w])",
                    re.UNICODE,
                )

                match = pattern.search(normalized)

                if match is None:
                    continue

                residue = (
                        normalized[:match.start()]
                        + " "
                        + normalized[match.end():]
                ).strip(" -|,/")

                if not self._is_only_seniority_modifier(
                        residue
                ):
                    continue

                exact_rank = (
                    0
                    if normalized == alias_normalized
                    else 1
                )

                candidates.append(
                    (
                        exact_rank,
                        -len(alias_normalized),
                        item.canonical,
                    )
                )

        if not candidates:
            return None

        candidates.sort()

        return candidates[0][2]

    @staticmethod
    def _is_only_seniority_modifier(
            value: str,
    ) -> bool:
        if not value:
            return True

        allowed = {
            "senior",
            "junior",
            "lead",
            "principal",
            "chief",
            "head",
            "assistant",
            "associate",
            "staff",
            "registered",
            "executive",
            "cao cấp",
            "trưởng",
            "phó",
        }

        return all(
            part in allowed
            for part in value.split()
        )

    @staticmethod
    def _contains_professional_keyword(
            normalized: str,
    ) -> bool:
        padded = f" {normalized} "

        return any(
            f" {keyword} " in padded
            for keyword in NAME_REJECT_WORDS
        )

    @staticmethod
    def _has_contact_or_date_marker(
            value: str,
    ) -> bool:
        return any(
            pattern.search(value) is not None
            for pattern in (
                EMAIL_MARKER_PATTERN,
                PHONE_LIKE_PATTERN,
                URL_MARKER_PATTERN,
                DATE_LIKE_PATTERN,
            )
        )