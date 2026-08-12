from __future__ import annotations

import calendar
import re
from dataclasses import dataclass
from datetime import date


# Ánh xạ tên tháng tiếng Anh và dạng viết tắt sang số tháng.
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

# Các giá trị biểu thị thời điểm hiện tại trong CV.
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

# Nhận diện định dạng MM/YYYY, MM-YYYY hoặc MM.YYYY.
MONTH_YEAR_NUMERIC_PATTERN = re.compile(
    r"^(?P<month>0?[1-9]|1[0-2])"
    r"[/.\-]"
    r"(?P<year>(?:19|20)\d{2})$",
    re.IGNORECASE,
)

# Nhận diện định dạng YYYY/MM, YYYY-MM hoặc YYYY.MM.
YEAR_MONTH_NUMERIC_PATTERN = re.compile(
    r"^(?P<year>(?:19|20)\d{2})"
    r"[/.\-]"
    r"(?P<month>0?[1-9]|1[0-2])$",
    re.IGNORECASE,
)

# Nhận diện tên tháng tiếng Anh kèm năm, ví dụ "Jan 2024" hoặc "January 2024".
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

# Nhận diện định dạng tháng tiếng Việt, ví dụ "Tháng 01/2024".
VIETNAMESE_MONTH_PATTERN = re.compile(
    r"^tháng\s+(?P<month>0?[1-9]|1[0-2])"
    r"\s*[/.\-]\s*"
    r"(?P<year>(?:19|20)\d{2})$",
    re.IGNORECASE,
)

# Nhận diện năm độc lập, ví dụ "2024".
YEAR_PATTERN = re.compile(
    r"^(?P<year>(?:19|20)\d{2})$",
)

# Pattern dùng để nhận diện một token ngày/tháng/năm
# bên trong chuỗi khoảng thời gian.
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

# Các giá trị đặc biệt biểu thị thời điểm hiện tại trong date range.
PRESENT_TOKEN = (
    r"(?:Present|Current|Now|Nay|Hiện\s+tại|Đến\s+nay)"
)

# Nhận diện khoảng thời gian, ví dụ:
# "01/2022 - 03/2024", "2022 - Present", "Jan 2022 to Now".
DATE_RANGE_PATTERN = re.compile(
    rf"(?P<start>{DATE_TOKEN})"
    rf"\s*(?:-|–|—|to|until|đến|tới)\s*"
    rf"(?P<end>{DATE_TOKEN}|{PRESENT_TOKEN})",
    re.IGNORECASE,
)


@dataclass(frozen=True, slots=True)
class DateValue:
    # Giá trị ngày sau khi normalize, ví dụ "2024-03" hoặc "2024".
    value: str | None

    # Năm được parse từ input.
    year: int | None

    # Tháng được parse từ input, nếu có.
    month: int | None

    # Độ chính xác của giá trị: MONTH hoặc YEAR.
    precision: str | None

    # True nếu giá trị biểu thị thời điểm hiện tại.
    current: bool = False


@dataclass(frozen=True, slots=True)
class DateRange:
    # Thời điểm bắt đầu của khoảng thời gian.
    start: DateValue

    # Thời điểm kết thúc của khoảng thời gian.
    end: DateValue

    # Phần text gốc được regex match.
    matched_text: str

    # Vị trí bắt đầu của match trong text.
    start_index: int

    # Vị trí kết thúc của match trong text.
    end_index: int


def normalize_date_value(
        value: str,
) -> DateValue:
    # Chuẩn hóa khoảng trắng trước khi phân tích giá trị ngày.
    normalized = " ".join(value.strip().split())
    casefolded = normalized.casefold()

    # Kiểm tra các giá trị biểu thị thời điểm hiện tại.
    if casefolded in PRESENT_VALUES:
        return DateValue(
            value=None,
            year=None,
            month=None,
            precision=None,
            current=True,
        )

    # Parse định dạng MM/YYYY, MM-YYYY hoặc MM.YYYY.
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

    # Parse định dạng YYYY/MM, YYYY-MM hoặc YYYY.MM.
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

    # Parse tên tháng tiếng Anh, ví dụ "Jan 2024".
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

    # Parse tháng tiếng Việt, ví dụ "Tháng 03/2024".
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

    # Nếu chỉ có năm thì giữ precision ở mức YEAR.
    match = YEAR_PATTERN.fullmatch(normalized)
    if match:
        year = int(match.group("year"))
        return DateValue(
            value=f"{year:04d}",
            year=year,
            month=None,
            precision="YEAR",
        )

    # Không nhận diện được định dạng ngày hợp lệ.
    return DateValue(
        value=None,
        year=None,
        month=None,
        precision=None,
    )


def extract_date_range(
        text: str,
) -> DateRange | None:
    # Tìm khoảng thời gian đầu tiên xuất hiện trong text.
    match = DATE_RANGE_PATTERN.search(text)

    if match is None:
        return None

    # Parse riêng thời điểm bắt đầu và kết thúc.
    start = normalize_date_value(match.group("start"))
    end = normalize_date_value(match.group("end"))

    # Khoảng thời gian phải có start hợp lệ.
    if start.value is None:
        return None

    # End phải là một date hợp lệ hoặc biểu thị thời điểm hiện tại.
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
    # "Present" được quy đổi thành tháng hiện tại.
    if value.current:
        current_date = today or date.today()
        return current_date.year * 12 + current_date.month - 1

    if value.year is None:
        return None

    # Nếu input có tháng thì sử dụng trực tiếp.
    if value.month is not None:
        month = value.month

    # Nếu chỉ có năm:
    # - Start mặc định là tháng 1.
    # - End mặc định là tháng 12.
    elif is_end:
        month = 12
    else:
        month = 1

    # Chuyển year/month thành một chỉ số tháng liên tục
    # để có thể tính khoảng cách giữa hai thời điểm.
    return value.year * 12 + month - 1


def duration_months(
        start: DateValue,
        end: DateValue,
        today: date | None = None,
) -> int | None:
    # Chuyển start và end thành month index.
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

    # Không thể tính duration nếu một trong hai giá trị không hợp lệ.
    if start_index is None or end_index is None:
        return None

    # Khoảng thời gian kết thúc trước thời điểm bắt đầu là không hợp lệ.
    if end_index < start_index:
        return None

    # Cộng 1 vì cả tháng bắt đầu và tháng kết thúc đều được tính.
    return end_index - start_index + 1


def is_expired(
        expiration: DateValue,
        today: date | None = None,
) -> bool | None:
    # Không thể xác định trạng thái nếu expiration không có giá trị.
    if expiration.value is None:
        return None

    current_date = today or date.today()

    # Nếu chỉ biết năm, coi ngày hết hạn là ngày cuối cùng của năm.
    if expiration.month is None:
        expiration_day = date(
            expiration.year,
            12,
            31,
        )

    # Nếu biết tháng, coi ngày hết hạn là ngày cuối cùng của tháng.
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