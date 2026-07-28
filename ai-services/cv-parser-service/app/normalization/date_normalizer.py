from __future__ import annotations

import calendar
import re
from dataclasses import dataclass
from datetime import date


MONTH_NAMES = {
    "jan": 1,
    "january": 1,
    "feb": 2,
    "february": 2,
    "mar": 3,
    "march": 3,
    "apr": 4,
    "april": 4,
    "may": 5,
    "jun": 6,
    "june": 6,
    "jul": 7,
    "july": 7,
    "aug": 8,
    "august": 8,
    "sep": 9,
    "sept": 9,
    "september": 9,
    "oct": 10,
    "october": 10,
    "nov": 11,
    "november": 11,
    "dec": 12,
    "december": 12,
}

PRESENT_VALUES = {
    "present",
    "current",
    "now",
    "nay",
    "hien tai",
    "hiện tại",
    "den nay",
    "đến nay",
}

MONTH_YEAR_NUMERIC_PATTERN = re.compile(
    r"^(?P<month>0?[1-9]|1[0-2])"
    r"[/.\-]"
    r"(?P<year>(?:19|20)\d{2})$",
    re.IGNORECASE,
)

YEAR_MONTH_NUMERIC_PATTERN = re.compile(
    r"^(?P<year>(?:19|20)\d{2})"
    r"[/.\-]"
    r"(?P<month>0?[1-9]|1[0-2])$",
    re.IGNORECASE,
)

ENGLISH_MONTH_PATTERN = re.compile(
    r"^(?P<month>"
    + "|".join(
        sorted(
            MONTH_NAMES.keys(),
            key=len,
            reverse=True,
        )
    )
    + r")\.?\s+(?P<year>(?:19|20)\d{2})$",
    re.IGNORECASE,
    )

VIETNAMESE_MONTH_PATTERN = re.compile(
    r"^tháng\s+(?P<month>0?[1-9]|1[0-2])"
    r"\s*[/.\-]\s*"
    r"(?P<year>(?:19|20)\d{2})$",
    re.IGNORECASE,
)

YEAR_PATTERN = re.compile(
    r"^(?P<year>(?:19|20)\d{2})$",
)

DATE_TOKEN = (
    r"(?:"
    r"(?:0?[1-9]|1[0-2])[/.\-](?:19|20)\d{2}"
    r"|(?:19|20)\d{2}[/.\-](?:0?[1-9]|1[0-2])"
    r"|(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?"
    r"|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?"
    r"|Sep(?:t(?:ember)?)?|Oct(?:ober)?"
    r"|Nov(?:ember)?|Dec(?:ember)?)\.?\s+(?:19|20)\d{2}"
    r"|Tháng\s+(?:0?[1-9]|1[0-2])\s*[/.\-]\s*(?:19|20)\d{2}"
    r"|(?:19|20)\d{2}"
    r")"
)

PRESENT_TOKEN = (
    r"(?:Present|Current|Now|Nay|Hiện\s+tại|Đến\s+nay)"
)

DATE_RANGE_PATTERN = re.compile(
    rf"(?P<start>{DATE_TOKEN})"
    rf"\s*(?:-|–|—|to|until|đến|tới)\s*"
    rf"(?P<end>{DATE_TOKEN}|{PRESENT_TOKEN})",
    re.IGNORECASE,
)


@dataclass(frozen=True, slots=True)
class DateValue:
    value: str | None
    year: int | None
    month: int | None
    precision: str | None
    current: bool = False


@dataclass(frozen=True, slots=True)
class DateRange:
    start: DateValue
    end: DateValue
    matched_text: str
    start_index: int
    end_index: int


def normalize_date_value(
        value: str,
) -> DateValue:
    normalized = " ".join(value.strip().split())
    casefolded = normalized.casefold()

    if casefolded in PRESENT_VALUES:
        return DateValue(
            value=None,
            year=None,
            month=None,
            precision=None,
            current=True,
        )

    match = MONTH_YEAR_NUMERIC_PATTERN.fullmatch(normalized)
    if match:
        year = int(match.group("year"))
        month = int(match.group("month"))
        return DateValue(
            value=f"{year:04d}-{month:02d}",
            year=year,
            month=month,
            precision="MONTH",
        )

    match = YEAR_MONTH_NUMERIC_PATTERN.fullmatch(normalized)
    if match:
        year = int(match.group("year"))
        month = int(match.group("month"))
        return DateValue(
            value=f"{year:04d}-{month:02d}",
            year=year,
            month=month,
            precision="MONTH",
        )

    match = ENGLISH_MONTH_PATTERN.fullmatch(normalized)
    if match:
        year = int(match.group("year"))
        month = MONTH_NAMES[match.group("month").casefold()]
        return DateValue(
            value=f"{year:04d}-{month:02d}",
            year=year,
            month=month,
            precision="MONTH",
        )

    match = VIETNAMESE_MONTH_PATTERN.fullmatch(normalized)
    if match:
        year = int(match.group("year"))
        month = int(match.group("month"))
        return DateValue(
            value=f"{year:04d}-{month:02d}",
            year=year,
            month=month,
            precision="MONTH",
        )

    match = YEAR_PATTERN.fullmatch(normalized)
    if match:
        year = int(match.group("year"))
        return DateValue(
            value=f"{year:04d}",
            year=year,
            month=None,
            precision="YEAR",
        )

    return DateValue(
        value=None,
        year=None,
        month=None,
        precision=None,
    )


def extract_date_range(
        text: str,
) -> DateRange | None:
    match = DATE_RANGE_PATTERN.search(text)

    if match is None:
        return None

    start = normalize_date_value(match.group("start"))
    end = normalize_date_value(match.group("end"))

    if start.value is None:
        return None

    if end.value is None and not end.current:
        return None

    return DateRange(
        start=start,
        end=end,
        matched_text=match.group(0),
        start_index=match.start(),
        end_index=match.end(),
    )


def to_month_index(
        value: DateValue,
        *,
        is_end: bool,
        today: date | None = None,
) -> int | None:
    if value.current:
        current_date = today or date.today()
        return current_date.year * 12 + current_date.month - 1

    if value.year is None:
        return None

    if value.month is not None:
        month = value.month
    elif is_end:
        month = 12
    else:
        month = 1

    return value.year * 12 + month - 1


def duration_months(
        start: DateValue,
        end: DateValue,
        today: date | None = None,
) -> int | None:
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

    if start_index is None or end_index is None:
        return None

    if end_index < start_index:
        return None

    return end_index - start_index + 1


def is_expired(
        expiration: DateValue,
        today: date | None = None,
) -> bool | None:
    if expiration.value is None:
        return None

    current_date = today or date.today()

    if expiration.month is None:
        expiration_day = date(
            expiration.year,
            12,
            31,
        )
    else:
        expiration_day = date(
            expiration.year,
            expiration.month,
            calendar.monthrange(
                expiration.year,
                expiration.month,
            )[1],
        )

    return expiration_day < current_date