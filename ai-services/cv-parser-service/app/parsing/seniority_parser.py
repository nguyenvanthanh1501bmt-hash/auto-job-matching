from __future__ import annotations

import re
from dataclasses import dataclass

from app.normalization.text_normalizer import normalize_for_matching
from app.schemas import WorkExperience


SENIORITY_ORDER = {
    "INTERN": 0,
    "TRAINEE": 1,
    "FRESHER": 2,
    "ENTRY_LEVEL": 3,
    "JUNIOR": 4,
    "MID": 5,
    "SENIOR": 6,
    "LEAD": 7,
    "SUPERVISOR": 8,
    "MANAGER": 9,
    "HEAD": 10,
    "DIRECTOR": 11,
    "EXECUTIVE": 12,
    "UNKNOWN": -1,
}

TITLE_PATTERNS: tuple[
    tuple[
        str,
        tuple[re.Pattern[str], ...],
    ],
    ...,
] = (
    (
        "EXECUTIVE",
        (
            re.compile(
                r"\bchief\s+executive\s+officer\b"
            ),
            re.compile(
                r"\bchief\s+operating\s+officer\b"
            ),
            re.compile(
                r"\bchief\s+financial\s+officer\b"
            ),
            re.compile(
                r"\bchief\s+technology\s+officer\b"
            ),
            re.compile(
                r"\bchief\s+information\s+officer\b"
            ),
            re.compile(
                r"\bchief\s+marketing\s+officer\b"
            ),
            re.compile(
                r"\bmanaging\s+director\b"
            ),
            re.compile(
                r"\bgeneral\s+director\b"
            ),
            re.compile(r"\bpresident\b"),
            re.compile(r"\bceo\b"),
            re.compile(r"\bcoo\b"),
            re.compile(r"\bcfo\b"),
            re.compile(r"\bcto\b"),
            re.compile(r"\bcio\b"),
            re.compile(r"\bcmo\b"),
            re.compile(
                r"\btổng\s+giám\s+đốc\b"
            ),
            re.compile(
                r"\bphó\s+tổng\s+giám\s+đốc\b"
            ),
        ),
    ),
    (
        "DIRECTOR",
        (
            re.compile(r"\bdirector\b"),
            re.compile(r"\bgiám\s+đốc\b"),
            re.compile(
                r"\bphó\s+giám\s+đốc\b"
            ),
        ),
    ),
    (
        "HEAD",
        (
            re.compile(r"\bhead\s+of\b"),
            re.compile(
                r"\bdepartment\s+head\b"
            ),
            re.compile(
                r"\bchief\s+accountant\b"
            ),
            re.compile(r"\bhead\s+chef\b"),
            re.compile(
                r"\bexecutive\s+chef\b"
            ),
            re.compile(r"\bhead\s+nurse\b"),
            re.compile(
                r"\btrưởng\s+bộ\s+phận\b"
            ),
            re.compile(
                r"\btrưởng\s+khoa\b"
            ),
            re.compile(
                r"\bkế\s+toán\s+trưởng\b"
            ),
            re.compile(
                r"\bbếp\s+trưởng\b"
            ),
        ),
    ),
    (
        "MANAGER",
        (
            re.compile(r"\bmanager\b"),
            re.compile(
                r"\bbranch\s+manager\b"
            ),
            re.compile(
                r"\bstore\s+manager\b"
            ),
            re.compile(
                r"\boperations\s+manager\b"
            ),
            re.compile(
                r"\bwarehouse\s+manager\b"
            ),
            re.compile(r"\bquản\s+lý\b"),
            re.compile(
                r"\btrưởng\s+phòng\b"
            ),
            re.compile(
                r"\btrưởng\s+ban\b"
            ),
        ),
    ),
    (
        "SUPERVISOR",
        (
            re.compile(r"\bsupervisor\b"),
            re.compile(r"\bforeman\b"),
            re.compile(
                r"\bshift\s+supervisor\b"
            ),
            re.compile(
                r"\bgiám\s+sát\b"
            ),
            re.compile(
                r"\bca\s+trưởng\b"
            ),
            re.compile(
                r"\btổ\s+trưởng\b"
            ),
        ),
    ),
    (
        "LEAD",
        (
            re.compile(
                r"\bteam\s+lead(?:er)?\b"
            ),
            re.compile(r"\blead\b"),
            re.compile(
                r"\btrưởng\s+nhóm\b"
            ),
            re.compile(
                r"\bnhóm\s+trưởng\b"
            ),
        ),
    ),
    (
        "SENIOR",
        (
            re.compile(r"\bsenior\b"),
            re.compile(r"\bprincipal\b"),
            re.compile(
                r"\bstaff\s+"
                r"(?:engineer|nurse|accountant|specialist)\b"
            ),
            re.compile(r"\bexperienced\b"),
            re.compile(
                r"\bcao\s+cấp\b"
            ),
            re.compile(
                r"\bchuyên\s+viên\s+chính\b"
            ),
        ),
    ),
    (
        "MID",
        (
            re.compile(
                r"\bmid(?:dle)?[-\s]?level\b"
            ),
            re.compile(
                r"\bintermediate\s+level\b"
            ),
        ),
    ),
    (
        "JUNIOR",
        (
            re.compile(r"\bjunior\b"),
            re.compile(r"\bassociate\b"),
            re.compile(
                r"\bsơ\s+cấp\b"
            ),
        ),
    ),
    (
        "ENTRY_LEVEL",
        (
            re.compile(
                r"\bentry[-\s]?level\b"
            ),
            re.compile(
                r"\bnew\s+graduate\b"
            ),
            re.compile(
                r"\bgraduate\s+"
                r"(?:role|position|program)\b"
            ),
            re.compile(
                r"\bmới\s+tốt\s+nghiệp\b"
            ),
        ),
    ),
    (
        "FRESHER",
        (
            re.compile(r"\bfresher\b"),
            re.compile(
                r"\bfresh\s+graduate\b"
            ),
        ),
    ),
    (
        "TRAINEE",
        (
            re.compile(r"\btrainee\b"),
            re.compile(
                r"\bmanagement\s+trainee\b"
            ),
            re.compile(
                r"\bhọc\s+việc\b"
            ),
        ),
    ),
    (
        "INTERN",
        (
            re.compile(
                r"\bintern(?:ship)?\b"
            ),
            re.compile(
                r"\bthực\s+tập\s+sinh\b"
            ),
        ),
    ),
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


class SeniorityParser:
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
            explicit = self._classify_title(
                headline
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

            explicit = self._classify_title(
                title
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
            explicit = self._classify_title(
                title
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

        fallback = self._from_experience_years(
            experience_years
        )

        if not signals:
            return SeniorityParseResult(
                seniority=fallback,
                warnings=(),
            )

        selected = self._select_signal(
            signals
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
                SENIORITY_ORDER[level]
                for level in explicit_levels
            )

            max_rank = max(
                SENIORITY_ORDER[level]
                for level in explicit_levels
            )

            if max_rank - min_rank >= 2:
                warnings.append(
                    "SENIORITY_SIGNALS_CONFLICT"
                )

        if fallback != "UNKNOWN":
            selected_rank = SENIORITY_ORDER[
                selected.seniority
            ]
            fallback_rank = SENIORITY_ORDER[
                fallback
            ]

            if (
                    abs(
                        selected_rank
                        - fallback_rank
                    )
                    >= 3
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

    @staticmethod
    def _classify_title(
            value: str,
    ) -> str | None:
        normalized = normalize_for_matching(
            value.replace("_", " ")
        )

        if not normalized:
            return None

        for seniority, patterns in TITLE_PATTERNS:
            if any(
                    pattern.search(normalized)
                    is not None
                    for pattern in patterns
            ):
                return seniority

        return None

    @staticmethod
    def _ordered_work_experiences(
            work_experiences: (
                    list[WorkExperience]
                    | tuple[WorkExperience, ...]
            ),
    ) -> list[WorkExperience]:
        indexed = list(
            enumerate(work_experiences)
        )

        def sort_key(
                item: tuple[int, WorkExperience],
        ) -> tuple[int, str, str, int]:
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

    @staticmethod
    def _select_signal(
            signals: list[_Signal],
    ) -> _Signal:
        return max(
            signals,
            key=lambda signal: (
                signal.source_priority,
                signal.recency_priority,
                SENIORITY_ORDER[
                    signal.seniority
                ],
            ),
        )

    @staticmethod
    def _from_experience_years(
            experience_years: float | None,
    ) -> str:
        if experience_years is None:
            return "UNKNOWN"

        if experience_years < 0.5:
            return "ENTRY_LEVEL"

        if experience_years < 2.0:
            return "JUNIOR"

        if experience_years < 5.0:
            return "MID"

        return "SENIOR"