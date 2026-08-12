from __future__ import annotations

import re
import unicodedata
from collections.abc import Iterable
from urllib.parse import urlsplit, urlunsplit


# Gom nhiều khoảng trắng liên tiếp thành một khoảng trắng.
MULTI_SPACE_PATTERN = re.compile(r"\s+")

# Chỉ cho phép URL sử dụng HTTP hoặc HTTPS.
SAFE_HTTP_SCHEMES = {
    "http",
    "https",
}


def clean_optional_text(
        value: str | None,
        maximum_length: int,
) -> str | None:
    # Giá trị None được giữ nguyên để phân biệt với text rỗng.
    if value is None:
        return None

    # Chuẩn hóa Unicode để đưa các dạng ký tự tương đương về cùng một dạng.
    normalized = unicodedata.normalize(
        "NFKC",
        value,
    )

    # Gom whitespace và loại bỏ khoảng trắng ở đầu/cuối.
    normalized = MULTI_SPACE_PATTERN.sub(
        " ",
        normalized,
    ).strip()

    # Text chỉ chứa whitespace được coi là không có giá trị.
    if not normalized:
        return None

    # Giới hạn độ dài sau khi normalize để tránh dữ liệu quá dài.
    return normalized[:maximum_length].rstrip()


def stable_unique(
        values: Iterable[str],
        *,
        maximum_items: int | None = None,
        case_insensitive: bool = True,
) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()

    # Duyệt theo thứ tự ban đầu để giữ nguyên thứ tự xuất hiện.
    for value in values:
        # Chuẩn hóa từng giá trị trước khi kiểm tra duplicate.
        cleaned = clean_optional_text(
            value,
            maximum_length=1_000,
        )

        # Bỏ qua các giá trị None hoặc text rỗng.
        if cleaned is None:
            continue

        # Case-insensitive giúp coi "Java" và "java" là cùng một giá trị.
        key = (
            cleaned.casefold()
            if case_insensitive
            else cleaned
        )

        # Bỏ qua giá trị đã xuất hiện trước đó.
        if key in seen:
            continue

        seen.add(key)
        result.append(cleaned)

        # Dừng sớm khi đã đạt giới hạn số lượng phần tử.
        if maximum_items is not None and len(result) >= maximum_items:
            break

    return result


def validate_http_url(
        value: str,
) -> str | None:
    # Loại bỏ whitespace và các dấu câu thường bị dính vào cuối URL
    # khi URL được trích xuất từ CV hoặc plain text.
    candidate = value.strip().rstrip(
        ".,;:)]}>"
    )

    # Bổ sung HTTPS nếu URL chỉ có dạng "www.example.com".
    if candidate.startswith("www."):
        candidate = f"https://{candidate}"

    # Parse URL; ValueError có thể xảy ra với URL không hợp lệ.
    try:
        parsed = urlsplit(candidate)
    except ValueError:
        return None

    scheme = parsed.scheme.casefold()

    # Chỉ chấp nhận HTTP và HTTPS.
    if scheme not in SAFE_HTTP_SCHEMES:
        return None

    # URL phải có hostname hợp lệ.
    if not parsed.hostname:
        return None

    # Không cho phép username/password trong URL.
    if parsed.username or parsed.password:
        return None

    # Kiểm tra port; một port không hợp lệ sẽ gây ValueError.
    try:
        port = parsed.port
    except ValueError:
        return None

    hostname = parsed.hostname.casefold()

    # Loại bỏ localhost và các local domain để tránh lưu URL nội bộ.
    if (
            hostname == "localhost"
            or hostname.endswith(".localhost")
            or hostname.endswith(".local")
    ):
        return None

    # Chỉ giữ hostname và port trong phần network location.
    normalized_netloc = hostname

    if port is not None:
        normalized_netloc = f"{hostname}:{port}"

    # Chuẩn hóa URL và loại bỏ fragment (#...).
    return urlunsplit(
        (
            scheme,
            normalized_netloc,
            parsed.path or "",
            parsed.query or "",
            "",
        )
    )