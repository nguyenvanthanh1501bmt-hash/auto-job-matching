from __future__ import annotations

import re
import unicodedata
from collections.abc import Iterable
from urllib.parse import urlsplit, urlunsplit


MULTI_SPACE_PATTERN = re.compile(r"\s+")
SAFE_HTTP_SCHEMES = {
    "http",
    "https",
}


def clean_optional_text(
        value: str | None,
        maximum_length: int,
) -> str | None:
    if value is None:
        return None

    normalized = unicodedata.normalize(
        "NFKC",
        value,
    )
    normalized = MULTI_SPACE_PATTERN.sub(
        " ",
        normalized,
    ).strip()

    if not normalized:
        return None

    return normalized[:maximum_length].rstrip()


def stable_unique(
        values: Iterable[str],
        *,
        maximum_items: int | None = None,
        case_insensitive: bool = True,
) -> list[str]:
    result: list[str] = []
    seen: set[str] = set()

    for value in values:
        cleaned = clean_optional_text(
            value,
            maximum_length=1_000,
        )

        if cleaned is None:
            continue

        key = (
            cleaned.casefold()
            if case_insensitive
            else cleaned
        )

        if key in seen:
            continue

        seen.add(key)
        result.append(cleaned)

        if maximum_items is not None and len(result) >= maximum_items:
            break

    return result


def validate_http_url(
        value: str,
) -> str | None:
    candidate = value.strip().rstrip(
        ".,;:)]}>"
    )

    if candidate.startswith("www."):
        candidate = f"https://{candidate}"

    try:
        parsed = urlsplit(candidate)
    except ValueError:
        return None

    scheme = parsed.scheme.casefold()

    if scheme not in SAFE_HTTP_SCHEMES:
        return None

    if not parsed.hostname:
        return None

    if parsed.username or parsed.password:
        return None

    try:
        port = parsed.port
    except ValueError:
        return None

    hostname = parsed.hostname.casefold()

    if (
            hostname == "localhost"
            or hostname.endswith(".localhost")
            or hostname.endswith(".local")
    ):
        return None

    normalized_netloc = hostname

    if port is not None:
        normalized_netloc = f"{hostname}:{port}"

    return urlunsplit(
        (
            scheme,
            normalized_netloc,
            parsed.path or "",
            parsed.query or "",
            "",
        )
    )