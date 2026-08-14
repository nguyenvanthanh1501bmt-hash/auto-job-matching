from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

from app.schemas import WorkExperience
from app.taxonomy.shared_taxonomy_loader import (
    SharedSeniorityLevel,
    SharedSeniorityTaxonomy,
)


WHITESPACE_PATTERN = re.compile(
    r"\s+"
)


EXPERIENCE_BANDS = {
    "INTERN": 0,
    "TRAINEE": 0,
    "FRESHER": 0,
    "ENTRY_LEVEL": 0,
    "JUNIOR": 1,
    "MID": 2,
    "SENIOR": 3,
}


@dataclass(
    frozen=True,
    slots=True,
)
class SeniorityParseResult:
    seniority: str
    warnings: tuple[str, ...]


@dataclass(
    frozen=True,
    slots=True,
)
class _Signal:
    seniority: str
    source_priority: int
    recency_priority: int
    title: str


@dataclass(
    frozen=True,
    slots=True,
)
class _CompiledRule:
    level: str
    patterns: tuple[
        re.Pattern[str],
        ...
    ]
    exclude_patterns: tuple[
        re.Pattern[str],
        ...
    ]
    allow_patterns: tuple[
        re.Pattern[str],
        ...
    ]


class SeniorityParser:

    def __init__(
            self,
            taxonomy: SharedSeniorityTaxonomy,
    ) -> None:
        self._taxonomy = taxonomy

        self._rank_by_level = {
            item.level: item.rank
            for item in taxonomy.levels
        }

        self._rules = tuple(
            self._compile_rule(
                item
            )
            for item in taxonomy.levels
            if item.level != "UNKNOWN"
        )

    def parse(
            self,
            *,
            headline: str | None,
            target_job_titles: (
                    list[str]
                    | tuple[str, ...]
            ),
            work_experiences: (
                    list[WorkExperience]
                    | tuple[WorkExperience, ...]
            ),
            experience_years: float | None,
    ) -> SeniorityParseResult:
        signals: list[_Signal] = []

        if headline is not None:
            explicit = (
                self._classify_title(
                    headline
                )
            )

            if explicit is not None:
                signals.append(
                    _Signal(
                        seniority=explicit,
                        source_priority=100,
                        recency_priority=100,
                        title=headline,
                    )
                )

        ordered_work = (
            self._ordered_work_experiences(
                work_experiences
            )
        )

        for index, experience in enumerate(
                ordered_work
        ):
            title = (
                    experience.job_title
                    or experience.normalized_job_title
            )

            if title is None:
                continue

            explicit = (
                self._classify_title(
                    title
                )
            )

            if explicit is None:
                continue

            signals.append(
                _Signal(
                    seniority=explicit,
                    source_priority=95,
                    recency_priority=max(
                        0,
                        90 - index * 5,
                        ),
                    title=title,
                )
            )

        for index, title in enumerate(
                target_job_titles
        ):
            explicit = (
                self._classify_title(
                    title
                )
            )

            if explicit is None:
                continue

            signals.append(
                _Signal(
                    seniority=explicit,
                    source_priority=80,
                    recency_priority=max(
                        0,
                        70 - index * 5,
                        ),
                    title=title,
                )
            )

        fallback = (
            self._from_experience_years(
                experience_years
            )
        )

        if not signals:
            return SeniorityParseResult(
                seniority=fallback,
                warnings=(),
            )

        selected = (
            self._select_signal(
                signals
            )
        )

        warnings: list[str] = []

        relevant_signals = [
            signal
            for signal in signals
            if (
                    signal.source_priority >= 95
                    and signal.recency_priority >= 90
            )
        ]

        explicit_levels = {
            signal.seniority
            for signal in relevant_signals
        }

        if len(explicit_levels) > 1:
            min_rank = min(
                self._rank(
                    level
                )
                for level in explicit_levels
            )

            max_rank = max(
                self._rank(
                    level
                )
                for level in explicit_levels
            )

            if (
                    max_rank
                    - min_rank
                    >= 2
            ):
                warnings.append(
                    "SENIORITY_SIGNALS_CONFLICT"
                )

        if (
                fallback != "UNKNOWN"
                and self._experience_signals_conflict(
            selected.seniority,
            fallback,
        )
        ):
            warnings.append(
                "SENIORITY_SIGNALS_CONFLICT"
            )

        return SeniorityParseResult(
            seniority=selected.seniority,
            warnings=tuple(
                dict.fromkeys(
                    warnings
                )
            ),
        )

    def _classify_title(
            self,
            value: str,
    ) -> str | None:
        folded = (
            self._fold(
                value.replace(
                    "_",
                    " ",
                )
            )
        )

        if not folded:
            return None

        for rule in self._rules:
            if not self._matches_any(
                    rule.patterns,
                    folded,
            ):
                continue

            excluded = (
                self._matches_any(
                    rule.exclude_patterns,
                    folded,
                )
            )

            if not excluded:
                return rule.level

            explicitly_allowed = (
                self._matches_any(
                    rule.allow_patterns,
                    folded,
                )
            )

            if explicitly_allowed:
                return rule.level

        return None

    def _select_signal(
            self,
            signals: list[_Signal],
    ) -> _Signal:
        return max(
            signals,
            key=lambda signal: (
                signal.source_priority,
                signal.recency_priority,
                self._rank(
                    signal.seniority
                ),
            ),
        )

    def _from_experience_years(
            self,
            experience_years: float | None,
    ) -> str:
        if experience_years is None:
            return "UNKNOWN"

        if experience_years < 0:
            return "UNKNOWN"

        thresholds = (
            self._taxonomy.experience
        )

        if (
                experience_years
                < thresholds.entry_level_under
        ):
            return "ENTRY_LEVEL"

        if (
                experience_years
                < thresholds.junior_under
        ):
            return "JUNIOR"

        if (
                experience_years
                < thresholds.mid_under
        ):
            return "MID"

        return "SENIOR"

    @staticmethod
    def _experience_signals_conflict(
            explicit_level: str,
            experience_level: str,
    ) -> bool:
        """
        Compare only levels that have a meaningful
        experience-band interpretation.

        Leadership hierarchy such as LEAD, MANAGER,
        HEAD or DIRECTOR must not be compared to
        years-of-experience fallback using raw rank.
        """

        explicit_band = (
            EXPERIENCE_BANDS.get(
                explicit_level
            )
        )

        experience_band = (
            EXPERIENCE_BANDS.get(
                experience_level
            )
        )

        if (
                explicit_band is None
                or experience_band is None
        ):
            return False

        return (
                abs(
                    explicit_band
                    - experience_band
                )
                >= 2
        )

    def _rank(
            self,
            level: str,
    ) -> int:
        try:
            return self._rank_by_level[
                level
            ]
        except KeyError as exception:
            raise RuntimeError(
                "Unknown shared seniority level: "
                f"{level}"
            ) from exception

    @staticmethod
    def _compile_rule(
            item: SharedSeniorityLevel,
    ) -> _CompiledRule:
        return _CompiledRule(
            level=item.level,
            patterns=tuple(
                re.compile(
                    value
                )
                for value in item.patterns
            ),
            exclude_patterns=tuple(
                re.compile(
                    value
                )
                for value
                in item.exclude_patterns
            ),
            allow_patterns=tuple(
                re.compile(
                    value
                )
                for value
                in item.allow_patterns
            ),
        )

    @staticmethod
    def _matches_any(
            patterns: tuple[
                re.Pattern[str],
                ...
            ],
            value: str,
    ) -> bool:
        return any(
            pattern.search(
                value
            )
            is not None
            for pattern in patterns
        )

    @staticmethod
    def _fold(
            value: str,
    ) -> str:
        if not value.strip():
            return ""

        decomposed = (
            unicodedata.normalize(
                "NFD",
                value,
            )
        )

        without_diacritics = "".join(
            character
            for character in decomposed
            if unicodedata.category(
                character
            )
            != "Mn"
        )

        without_diacritics = (
            without_diacritics
            .replace(
                "đ",
                "d",
            )
            .replace(
                "Đ",
                "D",
            )
        )

        return WHITESPACE_PATTERN.sub(
            " ",
            without_diacritics
            .casefold()
            .strip(),
            )

    @staticmethod
    def _ordered_work_experiences(
            work_experiences: (
                    list[WorkExperience]
                    | tuple[WorkExperience, ...]
            ),
    ) -> list[WorkExperience]:
        indexed = list(
            enumerate(
                work_experiences
            )
        )

        def sort_key(
                item: tuple[
                    int,
                    WorkExperience,
                ],
        ) -> tuple[
            int,
            str,
            str,
            int,
        ]:
            index, experience = item

            current_rank = (
                1
                if experience.current
                else 0
            )

            if experience.current:
                end_date = (
                        experience.end_date
                        or "9999-12"
                )
            else:
                end_date = (
                        experience.end_date
                        or "0000"
                )

            start_date = (
                    experience.start_date
                    or "0000"
            )

            return (
                current_rank,
                end_date,
                start_date,
                -index,
            )

        indexed.sort(
            key=sort_key,
            reverse=True,
        )

        return [
            experience
            for _, experience
            in indexed
        ]