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

MOJIBAKE_UTF8_TWO_BYTE_PATTERN = re.compile(
    r"(?:Ã|Â|Ä|Å|Æ|Ð|Ñ)"
    r"[\x80-\xBF\u00A0-\u00BF]",
)

MOJIBAKE_VIETNAMESE_THREE_BYTE_PATTERN = re.compile(
    r"á[º»]",
)

MOJIBAKE_PUNCTUATION_PATTERN = re.compile(
    r"â["
    r"\x80-\xBF"
    r"\u0080-\u009F"
    r"€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ"
    r"]",
)

MOJIBAKE_EMOJI_PATTERN = re.compile(
    r"ð[\x80-\xBF\u0080-\u009F]",
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
        (
            repaired_text,
            encoding_repaired,
        ) = self._repair_mojibake(
            text
        )

        normalized = unicodedata.normalize(
            "NFKC",
            repaired_text,
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

        if encoding_repaired:
            warnings.append(
                "TEXT_ENCODING_REPAIRED"
            )

        if len(normalized) > self._settings.max_extracted_chars:
            normalized = normalized[
                : self._settings.max_extracted_chars
            ].rstrip()

            warnings.append(
                "TRUNCATED_EXTRACTED_TEXT"
            )

        return NormalizedText(
            text=normalized,
            warnings=tuple(warnings),
        )

    @classmethod
    def _repair_mojibake(
            cls,
            text: str,
    ) -> tuple[str, bool]:
        whole_candidate = (
            cls._best_encoding_candidate(
                text
            )
        )

        if whole_candidate != text:
            return (
                whole_candidate,
                True,
            )

        repaired_lines: list[str] = []
        repaired = False

        for line in text.splitlines(
                keepends=True
        ):
            candidate = (
                cls._best_encoding_candidate(
                    line
                )
            )

            repaired_lines.append(
                candidate
            )

            if candidate != line:
                repaired = True

        if not repaired:
            return (
                text,
                False,
            )

        return (
            "".join(repaired_lines),
            True,
        )

    @classmethod
    def _best_encoding_candidate(
            cls,
            value: str,
    ) -> str:
        original_score = (
            cls._mojibake_score(
                value
            )
        )

        if original_score == 0:
            return value

        candidates = [
            value,
        ]

        for source_encoding in (
                "latin1",
                "cp1252",
        ):
            try:
                candidate = value.encode(
                    source_encoding
                ).decode(
                    "utf-8"
                )
            except (
                    UnicodeEncodeError,
                    UnicodeDecodeError,
            ):
                continue

            candidates.append(
                candidate
            )

        return min(
            candidates,
            key=lambda candidate: (
                cls._mojibake_score(
                    candidate
                ),
                candidate != value,
            ),
        )

    @staticmethod
    def _mojibake_score(
            value: str,
    ) -> int:
        control_count = sum(
            1
            for character in value
            if (
                    0x80
                    <= ord(character)
                    <= 0x9F
            )
        )

        two_byte_count = len(
            MOJIBAKE_UTF8_TWO_BYTE_PATTERN.findall(
                value
            )
        )

        vietnamese_count = len(
            MOJIBAKE_VIETNAMESE_THREE_BYTE_PATTERN.findall(
                value
            )
        )

        punctuation_count = len(
            MOJIBAKE_PUNCTUATION_PATTERN.findall(
                value
            )
        )

        emoji_count = len(
            MOJIBAKE_EMOJI_PATTERN.findall(
                value
            )
        )

        return (
                control_count * 4
                + two_byte_count * 3
                + vietnamese_count * 4
                + punctuation_count * 4
                + emoji_count * 4
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
        if unicodedata.category(
            character
        ) != "Mn"
    )