from __future__ import annotations

import re
from dataclasses import dataclass

from app.config import Settings
from app.normalization.date_normalizer import (
    DateRange,
    extract_date_range,
    normalize_date_value,
)
from app.normalization.text_normalizer import normalize_for_matching
from app.normalization.value_normalizer import clean_optional_text, stable_unique
from app.schemas import Education
from app.taxonomy.taxonomy_loader import TaxonomyBundle


BULLET_PATTERN = re.compile(
    r"^\s*(?:[-*•●▪◦‣∙·]|\d{1,2}[.)])\s*(?P<value>.+)$"
)

LABEL_PATTERN = re.compile(
    r"^(?P<label>"
    r"institution|school|university|college|academy|institute|"
    r"trường|truong|học\s+viện|hoc\s+vien|"
    r"cơ\s+sở\s+đào\s+tạo|co\s+so\s+dao\s+tao|"
    r"degree|qualification|bằng\s+cấp|bang\s+cap|"
    r"trình\s+độ|trinh\s+do|"
    r"major|field\s+of\s+study|field|ngành|nganh|"
    r"chuyên\s+ngành|chuyen\s+nganh|"
    r"specialization|specialisation|concentration|"
    r"định\s+hướng|dinh\s+huong|"
    r"grade|gpa|classification|xếp\s+loại|xep\s+loai|"
    r"điểm\s+trung\s+bình|diem\s+trung\s+binh|đtb|dtb|điểm|diem"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

STRONG_INSTITUTION_PATTERN = re.compile(
    r"(?:^|\s[-–—|]\s)(?:"
    r"university|college|school|academy|institute|polytechnic|"
    r"conservatory|faculty|campus|training\s+cent(?:er|re)|"
    r"trường(?:\s+(?:đại\s+học|cao\s+đẳng|trung\s+cấp))?|"
    r"truong(?:\s+(?:dai\s+hoc|cao\s+dang|trung\s+cap))?|"
    r"học\s+viện|hoc\s+vien|viện|vien|"
    r"trung\s+tâm\s+đào\s+tạo|trung\s+tam\s+dao\s+tao"
    r")\b",
    re.IGNORECASE,
)

INSTITUTION_PATTERN = re.compile(
    r"\b(?:"
    r"university|college|school|academy|institute|polytechnic|"
    r"conservatory|faculty|campus|training\s+center|"
    r"training\s+centre|đại\s+học|dai\s+hoc|"
    r"cao\s+đẳng|cao\s+dang|trung\s+cấp|trung\s+cap|"
    r"trường|truong|học\s+viện|hoc\s+vien|viện|vien|"
    r"trung\s+tâm\s+đào\s+tạo|trung\s+tam\s+dao\s+tao"
    r")\b",
    re.IGNORECASE,
)

GRADE_LABEL = (
    r"(?:gpa|grade|classification|"
    r"xếp\s+loại|xep\s+loai|"
    r"điểm\s+trung\s+bình|diem\s+trung\s+binh|"
    r"đtb|dtb|điểm|diem)"
)

GRADE_PATTERN = re.compile(
    rf"^{GRADE_LABEL}\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

INLINE_GRADE_PATTERN = re.compile(
    rf"(?<![\w]){GRADE_LABEL}\s*[:：-]?\s*"
    r"(?P<value>"
    r"\d{1,3}(?:[.,]\d+)?(?:\s*/\s*\d{1,3}(?:[.,]\d+)?)?"
    r"|(?:first|second|third)\s+class(?:\s+honou?rs?)?"
    r"|distinction|merit|pass|excellent|very\s+good|good|average"
    r"|xuất\s+sắc|xuat\s+sac|giỏi|gioi|khá|kha|trung\s+bình|trung\s+binh"
    r")",
    re.IGNORECASE,
)

FIELD_PATTERN = re.compile(
    r"^(?:major|field\s+of\s+study|field|ngành|nganh|"
    r"chuyên\s+ngành|chuyen\s+nganh)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

SPECIALIZATION_PATTERN = re.compile(
    r"^(?:specialization|specialisation|concentration|"
    r"định\s+hướng|dinh\s+huong)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

INLINE_FIELD_PATTERN = re.compile(
    r"\b(?:major(?:ed)?\s+in|speciali[sz](?:ed|ation)\s+in|"
    r"field\s+of\s+study|ngành|nganh|"
    r"chuyên\s+ngành|chuyen\s+nganh)"
    r"\s*[:：-]?\s*(?P<value>.+)$",
    re.IGNORECASE,
)

EXPECTED_GRADUATION_PATTERN = re.compile(
    r"(?:"
    r"(?:expected|anticipated|projected)\s+"
    r"(?:graduation|completion)(?:\s+date)?"
    r"|(?:dự\s+kiến|du\s+kien)\s+"
    r"(?:tốt\s+nghiệp|tot\s+nghiep|hoàn\s+thành|hoan\s+thanh)"
    r"|(?:graduation|completion)\s+expected"
    r")\s*[:：-]?\s*"
    r"(?P<value>"
    r"(?:0?[1-9]|1[0-2])[/.-](?:19|20)\d{2}"
    r"|(?:19|20)\d{2}[/.-](?:0?[1-9]|1[0-2])"
    r"|(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|"
    r"Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|"
    r"Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\.?\s+(?:19|20)\d{2}"
    r"|(?:Tháng|Thang)\s+(?:0?[1-9]|1[0-2])"
    r"(?:\s*[/.-]\s*|\s+(?:năm|nam)\s+)(?:19|20)\d{2}"
    r"|(?:19|20)\d{2}"
    r")",
    re.IGNORECASE,
)

NON_FIELD_LABEL_PATTERN = re.compile(
    r"^(?:"
    r"relevant\s+coursework|coursework|subjects?|modules?|"
    r"activities|description|summary|achievements?|honou?rs?|awards?|"
    r"môn\s+học|mon\s+hoc|học\s+phần|hoc\s+phan|"
    r"hoạt\s+động|hoat\s+dong|mô\s+tả|mo\s+ta|"
    r"thành\s+tích|thanh\s+tich|giải\s+thưởng|giai\s+thuong"
    r")\s*[:：-]",
    re.IGNORECASE,
)

DEGREE_IN_FIELD_PATTERN = re.compile(
    r"\bin\s+(?P<value>[^|;,]{2,200})$",
    re.IGNORECASE,
)

DEGREE_OF_FIELD_PATTERN = re.compile(
    r"\b(?:bachelor|master|doctor|diploma|certificate)\s+of\s+"
    r"(?P<value>[^|;,]{2,200})$",
    re.IGNORECASE,
)

SINGLE_DATE_PATTERN = re.compile(
    r"(?<!\d)(?P<value>"
    r"(?:0?[1-9]|1[0-2])[/.-](?:19|20)\d{2}"
    r"|(?:19|20)\d{2}[/.-](?:0?[1-9]|1[0-2])"
    r"|(?:19|20)\d{2}"
    r")(?!\d)"
)

ACHIEVEMENT_PATTERN = re.compile(
    r"(?:honou?r|award|scholarship|dean'?s\s+list|distinction|"
    r"graduated\s+with|top\s+\d+|học\s+bổng|thủ\s+khoa|"
    r"tốt\s+nghiệp\s+loại|giải\s+thưởng|thành\s+tích)",
    re.IGNORECASE,
)

SENTENCE_END_PATTERN = re.compile(r"[.!?]$")

MAX_DESCRIPTION_LENGTH = 5_000
MAX_ACHIEVEMENTS = 30
MAX_ENTRY_LINES = 80

DEGREE_RANK = {
    "UNKNOWN": 0,
    "OTHER": 1,
    "SECONDARY": 2,
    "HIGH_SCHOOL": 3,
    "VOCATIONAL": 4,
    "CERTIFICATE": 5,
    "DIPLOMA": 6,
    "ASSOCIATE": 7,
    "BACHELOR": 8,
    "PROFESSIONAL_DEGREE": 9,
    "MASTER": 10,
    "DOCTORATE": 11,
}

GENERIC_DEGREE_SUFFIXES = {
    "arts",
    "science",
    "engineering",
    "medicine",
    "surgery",
    "philosophy",
}


@dataclass(frozen=True, slots=True)
class EducationParseResult:
    educations: tuple[Education, ...]
    highest_education_level: str | None
    warnings: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class _EducationBlock:
    lines: tuple[str, ...]
    date_range: DateRange | None


class EducationParser:
    def __init__(
            self,
            settings: Settings,
            taxonomy: TaxonomyBundle,
    ) -> None:
        self._settings = settings
        self._degrees = taxonomy.degrees

    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
    ) -> EducationParseResult:
        sections = section_texts.get("EDUCATION", ())

        if not sections:
            return EducationParseResult(
                educations=(),
                highest_education_level=None,
                warnings=(),
            )

        educations: list[Education] = []
        warnings: list[str] = []

        for section in sections:
            for block in self._split_blocks(section):
                education, partially_parsed = self._parse_block(block)

                if education is None:
                    continue

                educations.append(education)

                if partially_parsed:
                    warnings.append("EDUCATION_PARTIALLY_PARSED")

                if len(educations) >= self._settings.max_educations:
                    break

            if len(educations) >= self._settings.max_educations:
                break

        if not educations:
            warnings.append("EDUCATION_PARTIALLY_PARSED")

        return EducationParseResult(
            educations=tuple(educations),
            highest_education_level=self._highest_level(
                educations
            ),
            warnings=tuple(dict.fromkeys(warnings)),
        )

    def _split_blocks(
            self,
            section_text: str,
    ) -> list[_EducationBlock]:
        lines = section_text.splitlines()
        anchors: list[tuple[int, DateRange]] = []

        for index, line in enumerate(lines):
            date_range = extract_date_range(line)

            if date_range is not None:
                anchors.append(
                    (
                        index,
                        date_range,
                    )
                )

        if anchors:
            return self._split_by_date_anchors(
                lines,
                anchors,
            )

        paragraph_blocks = self._split_paragraphs(
            lines
        )
        result: list[_EducationBlock] = []

        for paragraph in paragraph_blocks:
            result.extend(
                self._split_paragraph_by_degree(
                    paragraph
                )
            )

        return result

    def _split_by_date_anchors(
            self,
            lines: list[str],
            anchors: list[tuple[int, DateRange]],
    ) -> list[_EducationBlock]:
        starts = [
            self._find_block_start(
                lines,
                anchor_index,
            )
            for anchor_index, _ in anchors
        ]

        result: list[_EducationBlock] = []

        for index, (_, date_range) in enumerate(anchors):
            start = starts[index]

            end = (
                starts[index + 1]
                if index + 1 < len(starts)
                else len(lines)
            )

            if end <= start:
                end = (
                    anchors[index + 1][0]
                    if index + 1 < len(anchors)
                    else len(lines)
                )

            block_lines = tuple(
                line.strip()
                for line in lines[start:end]
                if line.strip()
            )

            if block_lines:
                result.append(
                    _EducationBlock(
                        lines=block_lines[
                            :MAX_ENTRY_LINES
                        ],
                        date_range=date_range,
                    )
                )

        return result

    def _find_block_start(
            self,
            lines: list[str],
            anchor_index: int,
    ) -> int:
        start = anchor_index
        inspected = 0
        index = anchor_index - 1

        while index >= 0 and inspected < 3:
            value = lines[index].strip()

            if not value:
                break

            if BULLET_PATTERN.match(value):
                break

            if extract_date_range(value) is not None:
                break

            if not self._looks_like_header_line(
                    value
            ):
                break

            start = index
            inspected += 1
            index -= 1

        return start

    @staticmethod
    def _split_paragraphs(
            lines: list[str],
    ) -> list[tuple[str, ...]]:
        result: list[tuple[str, ...]] = []
        current: list[str] = []

        for raw_line in lines:
            line = raw_line.strip()

            if not line:
                if current:
                    result.append(
                        tuple(
                            current[
                                :MAX_ENTRY_LINES
                            ]
                        )
                    )
                    current = []
                continue

            current.append(line)

        if current:
            result.append(
                tuple(
                    current[
                        :MAX_ENTRY_LINES
                    ]
                )
            )

        return result

    def _split_paragraph_by_degree(
            self,
            lines: tuple[str, ...],
    ) -> list[_EducationBlock]:
        if not lines:
            return []

        degree_indexes = [
            index
            for index, line in enumerate(lines)
            if self._match_degree(line) is not None
        ]

        if len(degree_indexes) <= 1:
            return [
                _EducationBlock(
                    lines=lines,
                    date_range=None,
                )
            ]

        result: list[_EducationBlock] = []

        for index, degree_index in enumerate(
                degree_indexes
        ):
            start = degree_index

            if (
                    degree_index > 0
                    and self._looks_like_institution(
                lines[degree_index - 1]
            )
            ):
                start = degree_index - 1

            end = (
                degree_indexes[index + 1]
                if index + 1 < len(degree_indexes)
                else len(lines)
            )

            if (
                    index + 1 < len(degree_indexes)
                    and end > 0
                    and self._looks_like_institution(
                lines[end - 1]
            )
            ):
                end -= 1

            block_lines = lines[start:end]

            if block_lines:
                result.append(
                    _EducationBlock(
                        lines=block_lines,
                        date_range=None,
                    )
                )

        return result

    def _parse_block(
            self,
            block: _EducationBlock,
    ) -> tuple[Education | None, bool]:
        institution_name: str | None = None
        degree: str | None = None
        normalized_degree_level: str | None = None
        field_of_study: str | None = None
        specialization: str | None = None
        grade: str | None = None
        achievements: list[str] = []
        description_lines: list[str] = []

        start_date = (
            block.date_range.start.value
            if block.date_range is not None
            else None
        )

        end_date = (
            block.date_range.end.value
            if block.date_range is not None
            else None
        )

        current = (
            block.date_range.end.current
            if block.date_range is not None
            else None
        )

        for raw_line in block.lines:
            line = self._remove_date_range(
                raw_line,
                block.date_range,
            )

            if not line:
                continue

            (
                line,
                expected_graduation_date,
                expected_graduation_text,
            ) = self._extract_expected_graduation(
                line
            )

            if expected_graduation_date is not None:
                if expected_graduation_text is not None:
                    description_lines.append(
                        expected_graduation_text
                    )

                if not line:
                    continue

            line, inline_grade = (
                self._extract_inline_grade(line)
            )

            if grade is None and inline_grade is not None:
                grade = inline_grade

            if not line:
                continue

            bullet = BULLET_PATTERN.match(line)

            if bullet is not None:
                value = clean_optional_text(
                    bullet.group("value"),
                    maximum_length=1_000,
                )

                if value is None:
                    continue

                if ACHIEVEMENT_PATTERN.search(value):
                    achievements.append(value)
                else:
                    description_lines.append(value)

                continue

            labelled = LABEL_PATTERN.match(line)

            if labelled is not None:
                label = normalize_for_matching(
                    labelled.group("label")
                )

                value = clean_optional_text(
                    labelled.group("value"),
                    maximum_length=1_000,
                )

                if value is None:
                    continue

                if label in {
                    "institution",
                    "school",
                    "university",
                    "college",
                    "academy",
                    "institute",
                    "trường",
                    "truong",
                    "học viện",
                    "hoc vien",
                    "cơ sở đào tạo",
                    "co so dao tao",
                }:
                    institution_name = (
                            institution_name
                            or value
                    )
                    continue

                if label in {
                    "degree",
                    "qualification",
                    "bằng cấp",
                    "bang cap",
                    "trình độ",
                    "trinh do",
                }:
                    degree = degree or value

                    normalized_degree_level = (
                            normalized_degree_level
                            or self._match_degree(value)
                    )

                    field_of_study = (
                            field_of_study
                            or self._extract_field_from_degree(
                        value
                    )
                    )
                    continue

                if label in {
                    "major",
                    "field of study",
                    "field",
                    "ngành",
                    "nganh",
                    "chuyên ngành",
                    "chuyen nganh",
                }:
                    field_of_study = (
                            field_of_study
                            or value
                    )
                    continue

                if label in {
                    "specialization",
                    "specialisation",
                    "concentration",
                    "định hướng",
                    "dinh huong",
                }:
                    specialization = (
                            specialization
                            or value
                    )
                    continue

                if label in {
                    "grade",
                    "gpa",
                    "classification",
                    "xếp loại",
                    "xep loai",
                    "điểm trung bình",
                    "diem trung binh",
                    "đtb",
                    "dtb",
                    "điểm",
                    "diem",
                }:
                    grade = grade or value
                    continue

            grade_match = GRADE_PATTERN.match(line)

            if grade_match is not None:
                grade = (
                        grade
                        or clean_optional_text(
                    grade_match.group(
                        "value"
                    ),
                    maximum_length=500,
                )
                )
                continue

            field_match = FIELD_PATTERN.match(line)

            if field_match is not None:
                field_of_study = (
                        field_of_study
                        or clean_optional_text(
                    field_match.group(
                        "value"
                    ),
                    maximum_length=500,
                )
                )
                continue

            specialization_match = (
                SPECIALIZATION_PATTERN.match(line)
            )

            if specialization_match is not None:
                specialization = (
                        specialization
                        or clean_optional_text(
                    specialization_match.group(
                        "value"
                    ),
                    maximum_length=500,
                )
                )
                continue

            if (
                    institution_name is None
                    and self._looks_like_strong_institution(
                line
            )
            ):
                institution_name = clean_optional_text(
                    line,
                    maximum_length=1_000,
                )
                continue

            degree_level = self._match_degree(line)

            if (
                    degree_level is not None
                    and degree is None
            ):
                degree = clean_optional_text(
                    line,
                    maximum_length=1_000,
                )

                normalized_degree_level = (
                    degree_level
                )

                field_of_study = (
                        field_of_study
                        or self._extract_field_from_degree(
                    line
                )
                )
                continue

            if (
                    institution_name is None
                    and self._looks_like_institution(
                line
            )
            ):
                institution_name = (
                    clean_optional_text(
                        line,
                        maximum_length=1_000,
                    )
                )
                continue

            inline_field = (
                INLINE_FIELD_PATTERN.search(line)
            )

            if (
                    inline_field is not None
                    and field_of_study is None
            ):
                field_of_study = (
                    clean_optional_text(
                        inline_field.group(
                            "value"
                        ),
                        maximum_length=500,
                    )
                )
                continue

            if (
                    start_date is None
                    and end_date is None
            ):
                single_dates = [
                    normalize_date_value(
                        match.group("value")
                    )
                    for match in (
                        SINGLE_DATE_PATTERN.finditer(
                            line
                        )
                    )
                ]

                canonical_dates = [
                    item.value
                    for item in single_dates
                    if item.value is not None
                ]

                if len(canonical_dates) == 1:
                    end_date = canonical_dates[0]
                    current = False

                    residue = (
                        SINGLE_DATE_PATTERN.sub(
                            "",
                            line,
                        ).strip(" -–—|,;")
                    )

                    if not residue:
                        continue

                    line = residue

            if (
                    field_of_study is None
                    and self._looks_like_unlabelled_field(
                line
            )
            ):
                field_of_study = clean_optional_text(
                    line,
                    maximum_length=500,
                )
                continue

            description_lines.append(line)

        if (
                normalized_degree_level is None
                and degree is not None
        ):
            normalized_degree_level = (
                self._match_degree(degree)
            )

        has_useful_data = any(
            value is not None
            for value in (
                institution_name,
                degree,
                normalized_degree_level,
                field_of_study,
                start_date,
                end_date,
            )
        )

        if not has_useful_data:
            return None, False

        description = clean_optional_text(
            "\n".join(description_lines),
            maximum_length=MAX_DESCRIPTION_LENGTH,
        )

        has_academic_detail = any(
            value is not None
            for value in (
                degree,
                normalized_degree_level,
                field_of_study,
                grade,
            )
        )

        partially_parsed = (
                institution_name is None
                or not has_academic_detail
        )

        return (
            Education(
                institution_name=institution_name,
                degree=degree,
                normalized_degree_level=(
                    normalized_degree_level
                ),
                field_of_study=field_of_study,
                specialization=specialization,
                start_date=start_date,
                end_date=end_date,
                current=current,
                grade=grade,
                achievements=stable_unique(
                    achievements,
                    maximum_items=(
                        MAX_ACHIEVEMENTS
                    ),
                ),
                description=description,
            ),
            partially_parsed,
        )

    @staticmethod
    def _extract_expected_graduation(
            value: str,
    ) -> tuple[str, str | None, str | None]:
        match = EXPECTED_GRADUATION_PATTERN.search(
            value
        )

        if match is None:
            return value.strip(), None, None

        normalized_date = (
            EducationParser._normalize_education_date(
                match.group("value")
            )
        )

        if normalized_date is None:
            return value.strip(), None, None

        residue = (
                value[:match.start()]
                + " "
                + value[match.end():]
        ).strip(" -–—|,;")

        return (
            residue,
            normalized_date,
            match.group(0).strip(),
        )

    @staticmethod
    def _normalize_education_date(
            value: str,
    ) -> str | None:
        normalized = normalize_date_value(value)

        if normalized.value is not None:
            return normalized.value

        vietnamese_month = re.fullmatch(
            r"(?:tháng|thang)\s+"
            r"(?P<month>0?[1-9]|1[0-2])"
            r"(?:\s*[/.-]\s*|\s+(?:năm|nam)\s+)"
            r"(?P<year>(?:19|20)\d{2})",
            " ".join(value.strip().split()),
            re.IGNORECASE,
        )

        if vietnamese_month is None:
            return None

        return (
            f"{int(vietnamese_month.group('year')):04d}-"
            f"{int(vietnamese_month.group('month')):02d}"
        )

    @staticmethod
    def _extract_inline_grade(
            value: str,
    ) -> tuple[str, str | None]:
        match = INLINE_GRADE_PATTERN.search(value)

        if match is None:
            return value.strip(), None

        grade = clean_optional_text(
            match.group("value"),
            maximum_length=500,
        )

        residue = (
                value[:match.start()]
                + " "
                + value[match.end():]
        ).strip(" -–—|,;")

        return residue, grade

    @staticmethod
    def _looks_like_strong_institution(
            value: str,
    ) -> bool:
        return (
                STRONG_INSTITUTION_PATTERN.search(
                    value
                )
                is not None
        )

    def _looks_like_unlabelled_field(
            self,
            value: str,
    ) -> bool:
        candidate = value.strip()

        if not candidate or len(candidate) > 160:
            return False

        if len(candidate.split()) > 14:
            return False

        if BULLET_PATTERN.match(candidate):
            return False

        if SENTENCE_END_PATTERN.search(candidate):
            return False

        if ":" in candidate or "：" in candidate:
            return False

        if NON_FIELD_LABEL_PATTERN.match(candidate):
            return False

        if ACHIEVEMENT_PATTERN.search(candidate):
            return False

        if extract_date_range(candidate) is not None:
            return False

        if SINGLE_DATE_PATTERN.fullmatch(candidate):
            return False

        if self._match_degree(candidate) is not None:
            return False

        if self._looks_like_institution(candidate):
            return False

        return any(
            character.isalpha()
            for character in candidate
        )

    def _match_degree(
            self,
            value: str,
    ) -> str | None:
        candidates: list[
            tuple[int, int, str]
        ] = []

        normalized_value = (
            normalize_for_matching(value)
        )

        for item in self._degrees:
            for alias in item.aliases:
                if not self._contains_degree_alias(
                        value,
                        normalized_value,
                        alias,
                ):
                    continue

                normalized_alias = (
                    normalize_for_matching(
                        alias
                    )
                )

                exact_rank = (
                    0
                    if normalized_value
                       == normalized_alias
                    else 1
                )

                candidates.append(
                    (
                        exact_rank,
                        -len(normalized_alias),
                        item.level,
                    )
                )

        if not candidates:
            return None

        candidates.sort()

        return candidates[0][2]

    @staticmethod
    def _contains_degree_alias(
            raw_value: str,
            normalized_value: str,
            alias: str,
    ) -> bool:
        compact_alias = re.sub(
            r"[^A-Za-z]",
            "",
            alias,
        )

        if (
                len(compact_alias) <= 3
                and compact_alias
        ):
            ambiguous_aliases = {
                "aa",
                "as",
                "ba",
                "bs",
                "jd",
                "ma",
                "md",
                "ms",
            }

            flags = (
                0
                if compact_alias.casefold()
                   in ambiguous_aliases
                else re.IGNORECASE
            )

            pattern = re.compile(
                rf"(?<![A-Za-z])"
                rf"{re.escape(alias)}"
                rf"(?![A-Za-z])",
                flags,
            )

            return (
                    pattern.search(raw_value)
                    is not None
            )

        normalized_alias = (
            normalize_for_matching(alias)
        )

        pattern = re.compile(
            rf"(?<![\w])"
            rf"{re.escape(normalized_alias)}"
            rf"(?![\w])",
            re.UNICODE,
        )

        return (
                pattern.search(normalized_value)
                is not None
        )

    @staticmethod
    def _extract_field_from_degree(
            value: str,
    ) -> str | None:
        match = DEGREE_IN_FIELD_PATTERN.search(
            value
        )

        if match is not None:
            return clean_optional_text(
                match.group("value"),
                maximum_length=500,
            )

        match = DEGREE_OF_FIELD_PATTERN.search(
            value
        )

        if match is None:
            return None

        candidate = clean_optional_text(
            match.group("value"),
            maximum_length=500,
        )

        if (
                candidate is None
                or normalize_for_matching(candidate)
                in GENERIC_DEGREE_SUFFIXES
        ):
            return None

        return candidate

    @staticmethod
    def _remove_date_range(
            value: str,
            date_range: DateRange | None,
    ) -> str:
        if date_range is None:
            return value.strip()

        line_date = extract_date_range(value)

        if line_date is None:
            return value.strip()

        return (
                value[:line_date.start_index]
                + " "
                + value[line_date.end_index:]
        ).strip(" -–—|,;")

    @staticmethod
    def _looks_like_institution(
            value: str,
    ) -> bool:
        return (
                INSTITUTION_PATTERN.search(value)
                is not None
        )

    @staticmethod
    def _looks_like_header_line(
            value: str,
    ) -> bool:
        if not value or len(value) > 200:
            return False

        if BULLET_PATTERN.match(value):
            return False

        if SENTENCE_END_PATTERN.search(value):
            return False

        return len(value.split()) <= 20

    @staticmethod
    def _highest_level(
            educations: list[Education],
    ) -> str | None:
        levels = [
            education.normalized_degree_level
            for education in educations
            if (
                    education.normalized_degree_level
                    is not None
            )
        ]

        if not levels:
            return None

        return max(
            levels,
            key=lambda level: DEGREE_RANK.get(
                level,
                -1,
            ),
        )