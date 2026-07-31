from __future__ import annotations

import re
from dataclasses import dataclass
from datetime import date

from app.normalization.date_normalizer import (
    DateValue,
    to_month_index,
)
from app.schemas import WorkExperience


EXPLICIT_EXPERIENCE_PATTERN = re.compile(
    r"(?:more\s+than\s+|over\s+|above\s+|hơn\s+|trên\s+)?"
    r"(?P<years>\d{1,2}(?:[.,]\d)?)\s*\+?\s*"
    r"(?:years?|yrs?|năm)\s+"
    r"(?:of\s+)?"
    r"(?:professional\s+|relevant\s+|working\s+)?"
    r"(?:experience|kinh\s+nghiệm)",
    re.IGNORECASE,
)


@dataclass(frozen=True, slots=True)
class ExperienceCalculationResult:
    experience_years: float | None
    warnings: tuple[str, ...]


class ExperienceCalculator:
    def calculate(
            self,
            work_experiences: list[WorkExperience],
            raw_text: str,
            today: date | None = None,
    ) -> ExperienceCalculationResult:
        ranges = self._build_month_ranges(
            work_experiences,
            today=today,
        )

        if ranges:
            merged = self._merge_ranges(ranges)

            total_months = sum(
                end - start + 1
                for start, end in merged
            )

            years = round(
                total_months / 12.0,
                1,
                )

            return ExperienceCalculationResult(
                experience_years=years,
                warnings=(),
            )

        fallback = self._extract_explicit_years(
            raw_text
        )

        if fallback is not None:
            return ExperienceCalculationResult(
                experience_years=fallback,
                warnings=(
                    "EXPERIENCE_YEARS_INFERRED_WITHOUT_STRUCTURED_HISTORY",
                ),
            )

        return ExperienceCalculationResult(
            experience_years=None,
            warnings=(),
        )

    @staticmethod
    def _build_month_ranges(
            work_experiences: list[WorkExperience],
            today: date | None,
    ) -> list[tuple[int, int]]:
        ranges: list[tuple[int, int]] = []

        for experience in work_experiences:
            start = ExperienceCalculator._date_value(
                experience.start_date,
                current=False,
            )

            end = ExperienceCalculator._date_value(
                experience.end_date,
                current=bool(experience.current),
            )

            start_index = to_month_index(
                start,
                is_end=False,
                today=today,
            )

            end_index = to_month_index(
                end,
                is_end=True,
                today=today,
            )

            if (
                    start_index is None
                    or end_index is None
            ):
                continue

            if end_index < start_index:
                continue

            ranges.append(
                (
                    start_index,
                    end_index,
                )
            )

        return ranges

    @staticmethod
    def _date_value(
            value: str | None,
            current: bool,
    ) -> DateValue:
        if current:
            return DateValue(
                value=None,
                year=None,
                month=None,
                precision=None,
                current=True,
            )

        if value is None:
            return DateValue(
                value=None,
                year=None,
                month=None,
                precision=None,
                current=False,
            )

        parts = value.split("-")
        year = int(parts[0])
        month = (
            int(parts[1])
            if len(parts) == 2
            else None
        )

        return DateValue(
            value=value,
            year=year,
            month=month,
            precision=(
                "MONTH"
                if month is not None
                else "YEAR"
            ),
            current=False,
        )

    @staticmethod
    def _merge_ranges(
            ranges: list[tuple[int, int]],
    ) -> list[tuple[int, int]]:
        ordered = sorted(ranges)
        merged: list[tuple[int, int]] = []

        for start, end in ordered:
            if (
                    not merged
                    or start > merged[-1][1] + 1
            ):
                merged.append(
                    (
                        start,
                        end,
                    )
                )
                continue

            previous_start, previous_end = merged[-1]

            merged[-1] = (
                previous_start,
                max(previous_end, end),
            )

        return merged

    @staticmethod
    def _extract_explicit_years(
            raw_text: str,
    ) -> float | None:
        match = EXPLICIT_EXPERIENCE_PATTERN.search(
            raw_text
        )

        if match is None:
            return None

        value = float(
            match.group("years").replace(
                ",",
                ".",
            )
        )

        if not 0 <= value <= 100:
            return None

        return round(value, 1)