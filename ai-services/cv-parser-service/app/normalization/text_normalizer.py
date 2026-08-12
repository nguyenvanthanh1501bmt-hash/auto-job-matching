from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass

from app.config import Settings


# Chuẩn hóa nhiều loại whitespace ngang thành một khoảng trắng.
HORIZONTAL_WHITESPACE_PATTERN = re.compile(
    r"[^\S\r\n]+",
)

# Giới hạn tối đa 2 dòng trống liên tiếp.
EXCESSIVE_BLANK_LINES_PATTERN = re.compile(
    r"\n{3,}",
)

# Nhận diện các ký hiệu bullet phổ biến trong CV.
BULLET_PREFIX_PATTERN = re.compile(
    r"^[\s\u00A0]*[•●▪◦‣∙·\uF0B7]\s*",
)

# Loại bỏ các ký tự control không cần thiết.
CONTROL_CHARACTER_PATTERN = re.compile(
    r"[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]"
)

# Các pattern thường xuất hiện khi text UTF-8 bị decode sai.
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
    # Text sau khi đã được normalize.
    text: str

    # Các cảnh báo phát sinh trong quá trình normalize.
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
        # Phát hiện và sửa lỗi encoding/mojibake nếu có.
        (
            repaired_text,
            encoding_repaired,
        ) = self._repair_mojibake(
            text
        )

        # Chuẩn hóa Unicode về dạng NFKC.
        normalized = unicodedata.normalize(
            "NFKC",
            repaired_text,
        )

        # Chuẩn hóa tất cả kiểu xuống dòng về "\n".
        normalized = normalized.replace(
            "\r\n",
            "\n",
        ).replace(
            "\r",
            "\n",
        )

        # Loại bỏ các ký tự control không cần thiết.
        normalized = CONTROL_CHARACTER_PATTERN.sub(
            "",
            normalized,
        )

        lines: list[str] = []

        # Xử lý từng dòng riêng biệt.
        for source_line in normalized.split("\n"):
            # Gom whitespace ngang thành một khoảng trắng
            # và loại bỏ khoảng trắng đầu/cuối dòng.
            line = HORIZONTAL_WHITESPACE_PATTERN.sub(
                " ",
                source_line,
            ).strip()

            # Chuẩn hóa các bullet thành "- ".
            line = BULLET_PREFIX_PATTERN.sub(
                "- ",
                line,
            )

            lines.append(line)

        normalized = "\n".join(lines)

        # Không cho phép quá nhiều dòng trống liên tiếp.
        normalized = EXCESSIVE_BLANK_LINES_PATTERN.sub(
            "\n\n",
            normalized,
        ).strip()

        warnings: list[str] = []

        if encoding_repaired:
            warnings.append(
                "TEXT_ENCODING_REPAIRED"
            )

        # Giới hạn độ dài text sau extraction.
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
        # Trước tiên thử sửa toàn bộ text cùng lúc.
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

        # Nếu không sửa được toàn bộ text,
        # thử xử lý từng dòng riêng biệt.
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
        # Tính mức độ mojibake của text ban đầu.
        original_score = (
            cls._mojibake_score(
                value
            )
        )

        # Score = 0 nghĩa là không phát hiện dấu hiệu encoding lỗi.
        if original_score == 0:
            return value

        candidates = [
            value,
        ]

        # Thử decode lại bằng latin1 và cp1252.
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

        # Chọn candidate có mojibake score thấp nhất.
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
        # Đếm các ký tự control thường xuất hiện khi decode sai.
        control_count = sum(
            1
            for character in value
            if (
                    0x80
                    <= ord(character)
                    <= 0x9F
            )
        )

        # Đếm các pattern mojibake UTF-8 phổ biến.
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

        # Score càng cao thì text càng có khả năng bị lỗi encoding.
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
    # Chuẩn hóa Unicode và chuyển về lowercase để phục vụ matching.
    normalized = unicodedata.normalize(
        "NFKC",
        value,
    ).casefold()

    # Gom whitespace và loại bỏ khoảng trắng đầu/cuối.
    return HORIZONTAL_WHITESPACE_PATTERN.sub(
        " ",
        normalized,
    ).strip()


def remove_diacritics(
        value: str,
) -> str:
    # Tách ký tự có dấu thành ký tự gốc + combining mark.
    decomposed = unicodedata.normalize(
        "NFD",
        value,
    )

    # Loại bỏ các combining mark để chuyển text về dạng không dấu.
    return "".join(
        character
        for character in decomposed
        if unicodedata.category(
            character
        ) != "Mn"
    )