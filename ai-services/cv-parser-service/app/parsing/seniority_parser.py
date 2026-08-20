from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

from app.schemas import WorkExperience
from app.taxonomy.shared_taxonomy_loader import (
    SharedSeniorityLevel,
    SharedSeniorityTaxonomy,
)


WHITESPACE_PATTERN = re.compile(r"\s+")


EXPERIENCE_BANDS = {
    "INTERN": 0,
    "TRAINEE": 0,
    "FRESHER": 0,
    "ENTRY_LEVEL": 0,
    "JUNIOR": 1,
    "MID": 2,
    "SENIOR": 3,
}


EARLY_CAREER_LEVEL_PRIORITY = (
    "INTERN",
    "TRAINEE",
    "FRESHER",
    "ENTRY_LEVEL",
)


TARGET_HINT_LEVELS = {
    "INTERN",
    "TRAINEE",
    "FRESHER",
    "ENTRY_LEVEL",
    "JUNIOR",
}


STUDENT_PATTERNS = (
    re.compile(r"\bstudent\b"),
    re.compile(r"\bundergraduate\b"),
    re.compile(r"\bcollege student\b"),
    re.compile(r"\buniversity student\b"),
    re.compile(r"\bsinh vien\b"),
)


@dataclass(frozen=True, slots=True)
class SeniorityParseResult:
    seniority: str
    warnings: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class _Signal:
    seniority: str
    source_priority: int
    recency_priority: int
    title: str
    source: str


@dataclass(frozen=True, slots=True)
class _CompiledRule:
    level: str
    patterns: tuple[re.Pattern[str], ...]
    exclude_patterns: tuple[re.Pattern[str], ...]
    allow_patterns: tuple[re.Pattern[str], ...]


class SeniorityParser:
    """
    Resolve candidate-level seniority from CV evidence.

    Priority:
    1. Explicit seniority in headline.
    2. Explicit seniority in current / most-recent work role.
    3. Experience years.
    4. Career objective as an early-career hint.
    5. Target job title as a last-resort early-career hint.

    Historical roles must not permanently pin the candidate to an old level.

    Examples:
    - An old INTERN role must not make an experienced candidate INTERN.
    - An old DIRECTOR role must not automatically make the current candidate
      DIRECTOR if the latest role no longer carries that seniority.
    - "Assistant to the Director" must not become DIRECTOR.
    """

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
            self._compile_rule(item)
            for item in taxonomy.levels
            if item.level != "UNKNOWN"
        )

        self._rules_by_level = {
            rule.level: rule
            for rule in self._rules
        }

    def parse(
            self,
            *,
            headline: str | None,
            career_objective: str | None = None,
            target_job_titles: list[str] | tuple[str, ...],
            work_experiences: list[WorkExperience] | tuple[WorkExperience, ...],
            experience_years: float | None,
    ) -> SeniorityParseResult:
        experience_fallback = self._from_experience_years(
            experience_years
        )

        career_signal = self._from_career_objective(
            career_objective
        )

        primary_signals: list[_Signal] = []

        # Headline is strong explicit evidence.
        if headline:
            explicit = self._classify_title(
                headline
            )

            if explicit is not None:
                primary_signals.append(
                    _Signal(
                        seniority=explicit,
                        source_priority=100,
                        recency_priority=100,
                        title=headline,
                        source="HEADLINE",
                    )
                )

        # Only current / most-recent roles define current candidate seniority.
        ordered_work = self._ordered_work_experiences(
            work_experiences
        )

        primary_work = self._primary_work_experiences(
            ordered_work
        )

        for index, experience in enumerate(primary_work):
            classified = self._classify_work_experience(
                experience
            )

            if classified is None:
                continue

            explicit_level, title = classified

            primary_signals.append(
                _Signal(
                    seniority=explicit_level,
                    source_priority=95,
                    recency_priority=max(
                        0,
                        95 - index,
                        ),
                    title=title,
                    source="WORK_EXPERIENCE",
                )
            )

        warnings: list[str] = []

        # Explicit headline/current-role evidence wins.
        if primary_signals:
            selected = self._select_signal(
                primary_signals
            )

            if self._signals_conflict(
                    primary_signals
            ):
                warnings.append(
                    "SENIORITY_SIGNALS_CONFLICT"
                )

            if (
                    experience_fallback != "UNKNOWN"
                    and self._experience_signals_conflict(
                selected.seniority,
                experience_fallback,
            )
            ):
                warnings.append(
                    "SENIORITY_SIGNALS_CONFLICT"
                )

            return SeniorityParseResult(
                seniority=selected.seniority,
                warnings=tuple(
                    dict.fromkeys(warnings)
                ),
            )

        # Structured work history is stronger than career objective.
        #
        # A candidate may still write:
        # "I am a student..."
        # "I am looking for an opportunity..."
        #
        # while already having meaningful work history.
        #
        # Therefore career objective must never downgrade a seniority that
        # can already be inferred from experience years.
        if experience_fallback != "UNKNOWN":
            return SeniorityParseResult(
                seniority=experience_fallback,
                warnings=(),
            )

        # Career objective is only used when structured experience cannot
        # determine a seniority.
        if career_signal is not None:
            return SeniorityParseResult(
                seniority=career_signal,
                warnings=(),
            )

        # Target job title is an aspiration, not historical evidence.
        # Only allow low-level hints from it.
        target_hint = self._from_target_job_titles(
            target_job_titles
        )

        return SeniorityParseResult(
            seniority=target_hint or "UNKNOWN",
            warnings=(),
        )

    def _classify_work_experience(
            self,
            experience: WorkExperience,
    ) -> tuple[str, str] | None:
        # Raw title first because it can contain meaningful modifiers:
        #
        # Senior Software Engineer
        # Lead Developer
        # Junior Accountant
        #
        # while normalizedJobTitle may intentionally remove those modifiers.
        if experience.job_title:
            explicit = self._classify_title(
                experience.job_title
            )

            if explicit is not None:
                return (
                    explicit,
                    experience.job_title,
                )

        # Canonical title is fallback evidence.
        if experience.normalized_job_title:
            explicit = self._classify_title(
                experience.normalized_job_title
            )

            if explicit is not None:
                return (
                    explicit,
                    experience.normalized_job_title,
                )

        # Structured internship employment type is reliable evidence.
        if experience.employment_type == "INTERNSHIP":
            return (
                "INTERN",
                (
                        experience.job_title
                        or experience.normalized_job_title
                        or "INTERNSHIP"
                ),
            )

        return None

    def _classify_title(
            self,
            value: str,
    ) -> str | None:
        folded = self._fold(
            value.replace(
                "_",
                " ",
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

            excluded = self._matches_any(
                rule.exclude_patterns,
                folded,
            )

            if not excluded:
                return rule.level

            explicitly_allowed = self._matches_any(
                rule.allow_patterns,
                folded,
            )

            if explicitly_allowed:
                return rule.level

        return None

    def _from_career_objective(
            self,
            value: str | None,
    ) -> str | None:
        if value is None:
            return None

        folded = self._fold(value)

        if not folded:
            return None

        # Only early-career levels are allowed to come from objective text.
        #
        # We intentionally do not infer:
        # SENIOR / LEAD / MANAGER / DIRECTOR / EXECUTIVE
        #
        # from an aspiration paragraph.
        for level in EARLY_CAREER_LEVEL_PRIORITY:
            rule = self._rules_by_level.get(
                level
            )

            if rule is None:
                continue

            if not self._matches_any(
                    rule.patterns,
                    folded,
            ):
                continue

            excluded = self._matches_any(
                rule.exclude_patterns,
                folded,
            )

            if not excluded:
                return level

            if self._matches_any(
                    rule.allow_patterns,
                    folded,
            ):
                return level

        if self._matches_any(
                STUDENT_PATTERNS,
                folded,
        ):
            return "ENTRY_LEVEL"

        return None

    def _from_target_job_titles(
            self,
            target_job_titles: list[str] | tuple[str, ...],
    ) -> str | None:
        hints: list[_Signal] = []

        for index, title in enumerate(
                target_job_titles
        ):
            explicit = self._classify_title(
                title
            )

            # Target role is an aspiration.
            #
            # Somebody targeting "Engineering Manager" is not necessarily
            # already a MANAGER.
            if (
                    explicit is None
                    or explicit not in TARGET_HINT_LEVELS
            ):
                continue

            hints.append(
                _Signal(
                    seniority=explicit,
                    source_priority=20,
                    recency_priority=max(
                        0,
                        20 - index,
                        ),
                    title=title,
                    source="TARGET_JOB_TITLE",
                )
            )

        if not hints:
            return None

        return self._select_signal(
            hints
        ).seniority

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

    def _signals_conflict(
            self,
            signals: list[_Signal],
    ) -> bool:
        if len(signals) <= 1:
            return False

        levels = {
            signal.seniority
            for signal in signals
            if signal.source_priority >= 95
        }

        if len(levels) <= 1:
            return False

        ranks = [
            self._rank(level)
            for level in levels
        ]

        return (
                max(ranks)
                - min(ranks)
                >= 2
        )

    def _from_experience_years(
            self,
            experience_years: float | None,
    ) -> str:
        if experience_years is None:
            return "UNKNOWN"

        if experience_years < 0:
            return "UNKNOWN"

        thresholds = self._taxonomy.experience

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
        Compare only career levels that have a direct years-of-experience
        interpretation.

        Leadership hierarchy such as LEAD, MANAGER, HEAD and DIRECTOR is
        title semantics and must not automatically be downgraded from years
        alone.
        """

        explicit_band = EXPERIENCE_BANDS.get(
            explicit_level
        )

        experience_band = EXPERIENCE_BANDS.get(
            experience_level
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
                re.compile(value)
                for value in item.patterns
            ),
            exclude_patterns=tuple(
                re.compile(value)
                for value in item.exclude_patterns
            ),
            allow_patterns=tuple(
                re.compile(value)
                for value in item.allow_patterns
            ),
        )

    @staticmethod
    def _matches_any(
            patterns: tuple[re.Pattern[str], ...],
            value: str,
    ) -> bool:
        return any(
            pattern.search(value) is not None
            for pattern in patterns
        )

    @staticmethod
    def _fold(
            value: str,
    ) -> str:
        if not value.strip():
            return ""

        decomposed = unicodedata.normalize(
            "NFD",
            value,
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
    def _primary_work_experiences(
            ordered_work: list[WorkExperience],
    ) -> list[WorkExperience]:
        if not ordered_work:
            return []

        # If CV explicitly marks current jobs, use all current jobs.
        current = [
            experience
            for experience in ordered_work
            if experience.current is True
        ]

        if current:
            return current

        # Otherwise only use role(s) with the latest end date.
        #
        # This avoids historical titles permanently determining the current
        # candidate seniority.
        latest = ordered_work[0]

        if latest.end_date is None:
            return [latest]

        return [
            experience
            for experience in ordered_work
            if experience.end_date == latest.end_date
        ]

    @staticmethod
    def _ordered_work_experiences(
            work_experiences: list[WorkExperience] | tuple[WorkExperience, ...],
    ) -> list[WorkExperience]:
        indexed = list(
            enumerate(
                work_experiences
            )
        )

        def sort_key(
                item: tuple[int, WorkExperience],
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
            for _, experience in indexed
        ]