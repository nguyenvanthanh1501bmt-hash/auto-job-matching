from __future__ import annotations

import re
from dataclasses import dataclass

from app.normalization.text_normalizer import (
    normalize_for_matching,
)


VIETNAMESE_DIACRITIC_PATTERN = re.compile(
    r"[ăâđêôơư"
    r"àáảãạằắẳẵặầấẩẫậèéẻẽẹềếểễệ"
    r"ìíỉĩịòóỏõọồốổỗộờớởỡợ"
    r"ùúủũụừứửữựỳýỷỹỵ]",
    re.IGNORECASE,
)

WORD_PATTERN = re.compile(
    r"[^\W\d_]+",
    re.UNICODE,
)

VIETNAMESE_COMMON_WORDS = {
    "và",
    "của",
    "có",
    "trong",
    "với",
    "làm",
    "việc",
    "kinh",
    "nghiệm",
    "kỹ",
    "năng",
    "học",
    "vấn",
    "mục",
    "tiêu",
    "nghề",
    "nghiệp",
    "trình",
    "độ",
    "quản",
    "lý",
    "khách",
    "hàng",
    "công",
    "ty",
    "tại",
    "từ",
    "đến",
    "hiện",
    "nay",
}

ENGLISH_COMMON_WORDS = {
    "and",
    "the",
    "of",
    "with",
    "for",
    "in",
    "to",
    "work",
    "experience",
    "skills",
    "education",
    "objective",
    "professional",
    "management",
    "customer",
    "company",
    "current",
    "present",
    "responsible",
    "business",
    "project",
    "training",
    "summary",
    "career",
}

VIETNAMESE_HEADINGS = {
    "kinh nghiệm",
    "kinh nghiệm làm việc",
    "học vấn",
    "kỹ năng",
    "mục tiêu nghề nghiệp",
    "chứng chỉ",
    "giới thiệu",
    "thông tin cá nhân",
}

ENGLISH_HEADINGS = {
    "work experience",
    "professional experience",
    "education",
    "skills",
    "career objective",
    "certifications",
    "professional summary",
    "contact information",
}


@dataclass(frozen=True, slots=True)
class LanguageDetection:
    language: str
    vietnamese_score: float
    english_score: float


class LanguageDetector:
    def detect(
            self,
            text: str,
    ) -> LanguageDetection:
        normalized = normalize_for_matching(text)
        words = [
            word.casefold()
            for word in WORD_PATTERN.findall(normalized)
        ]

        if len(words) < 8:
            return LanguageDetection(
                language="UNKNOWN",
                vietnamese_score=0.0,
                english_score=0.0,
            )

        vietnamese_common_count = sum(
            1
            for word in words
            if word in VIETNAMESE_COMMON_WORDS
        )
        english_common_count = sum(
            1
            for word in words
            if word in ENGLISH_COMMON_WORDS
        )

        vietnamese_heading_count = sum(
            1
            for heading in VIETNAMESE_HEADINGS
            if heading in normalized
        )
        english_heading_count = sum(
            1
            for heading in ENGLISH_HEADINGS
            if heading in normalized
        )

        diacritic_count = len(
            VIETNAMESE_DIACRITIC_PATTERN.findall(text)
        )

        token_denominator = max(len(words), 1)

        vietnamese_score = min(
            1.0,
            (
                    vietnamese_common_count / token_denominator * 6.0
                    + vietnamese_heading_count * 0.12
                    + min(diacritic_count / 40.0, 0.35)
            ),
        )
        english_score = min(
            1.0,
            (
                    english_common_count / token_denominator * 6.0
                    + english_heading_count * 0.12
            ),
        )

        language = self._choose_language(
            vietnamese_score=vietnamese_score,
            english_score=english_score,
        )

        return LanguageDetection(
            language=language,
            vietnamese_score=round(vietnamese_score, 4),
            english_score=round(english_score, 4),
        )

    @staticmethod
    def _choose_language(
            vietnamese_score: float,
            english_score: float,
    ) -> str:
        if vietnamese_score < 0.16 and english_score < 0.16:
            return "UNKNOWN"

        if (
                vietnamese_score >= 0.22
                and english_score >= 0.22
                and abs(vietnamese_score - english_score) <= 0.35
        ):
            return "MIXED"

        if vietnamese_score > english_score:
            return "VI"

        return "EN"