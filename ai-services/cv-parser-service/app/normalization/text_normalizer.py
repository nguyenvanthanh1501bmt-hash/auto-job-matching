from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

from app.config import Settings


HORIZONTAL_WHITESPACE_PATTERN = re.compile(
    r"[^\S\r\n]+",
)
EXCESSIVE_BLANK_LINES_PATTERN = re.compile(
    r"\n{3,}",
)
BULLET_PREFIX_PATTERN = re.compile(
    r"^[\s\u00A0]*[•●▪◦‣∙·\uF0B7]\s*",
)
CONTROL_CHARACTER_PATTERN = re.compile(
    r"[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]"
)


@dataclass(frozen=True, slots=True)
class NormalizedText:
    text: str
    warnings: tuple[str, ...]


class TextNormalizer:
    def __init__(
            self,
            settings: Settings,
    ) -> None:
        self._settings = settings

    def normalize(
            self,
            text: str,
    ) -> NormalizedText:
        normalized = unicodedata.normalize(
            "NFKC",
            text,
        )
        normalized = normalized.replace(
            "\r\n",
            "\n",
        ).replace(
            "\r",
            "\n",
        )
        normalized = CONTROL_CHARACTER_PATTERN.sub(
            "",
            normalized,
        )

        lines: list[str] = []

        for source_line in normalized.split("\n"):
            line = HORIZONTAL_WHITESPACE_PATTERN.sub(
                " ",
                source_line,
            ).strip()

            line = BULLET_PREFIX_PATTERN.sub(
                "- ",
                line,
            )

            lines.append(line)

        normalized = "\n".join(lines)
        normalized = EXCESSIVE_BLANK_LINES_PATTERN.sub(
            "\n\n",
            normalized,
        ).strip()

        warnings: list[str] = []

        if len(normalized) > self._settings.max_extracted_chars:
            normalized = normalized[
                : self._settings.max_extracted_chars
            ].rstrip()
            warnings.append("TRUNCATED_EXTRACTED_TEXT")

        return NormalizedText(
            text=normalized,
            warnings=tuple(warnings),
        )


def normalize_for_matching(
        value: str,
) -> str:
    normalized = unicodedata.normalize(
        "NFKC",
        value,
    ).casefold()

    return HORIZONTAL_WHITESPACE_PATTERN.sub(
        " ",
        normalized,
    ).strip()


def remove_diacritics(
        value: str,
) -> str:
    decomposed = unicodedata.normalize(
        "NFD",
        value,
    )

    return "".join(
        character
        for character in decomposed
        if unicodedata.category(character) != "Mn"
    )