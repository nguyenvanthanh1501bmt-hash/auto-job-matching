from __future__ import annotations

from app.config import Settings
from app.normalization.text_normalizer import (
    TextNormalizer,
)
from app.parsing.language_detector import (
    LanguageDetector,
)
from app.parsing.section_detector import (
    SectionDetector,
)
from app.taxonomy.taxonomy_loader import (
    TaxonomyBundle,
)


def test_detects_vietnamese_language(
        language_detector: LanguageDetector,
) -> None:
    text = """
    NGUYỄN THỊ MINH AN
    TÓM TẮT NGHỀ NGHIỆP
    Tôi có kinh nghiệm làm việc trong lĩnh vực kế toán và quản lý.
    KỸ NĂNG
    Báo cáo tài chính, kiểm toán, quản lý công nợ và Microsoft Excel.
    KINH NGHIỆM LÀM VIỆC
    Tôi làm việc tại công ty sản xuất từ năm 2020 đến hiện nay.
    HỌC VẤN
    Cử nhân Kế toán.
    """

    result = language_detector.detect(text)

    assert result.language == "VI"
    assert (
            result.vietnamese_score
            > result.english_score
    )


def test_detects_english_language(
        language_detector: LanguageDetector,
) -> None:
    text = """
    EMILY JOHNSON
    PROFESSIONAL SUMMARY
    Experienced registered nurse with professional experience in patient care,
    clinical assessment, medical records, and infection control.
    WORK EXPERIENCE
    Responsible for patient monitoring and collaboration with the hospital team.
    EDUCATION
    Bachelor of Nursing.
    SKILLS
    Patient care, emergency care, communication, and training.
    """

    result = language_detector.detect(text)

    assert result.language == "EN"
    assert (
            result.english_score
            > result.vietnamese_score
    )


def test_detects_mixed_language(
        language_detector: LanguageDetector,
) -> None:
    text = """
    PROFESSIONAL SUMMARY
    Chuyên viên kinh doanh có kinh nghiệm làm việc với khách hàng doanh nghiệp.
    WORK EXPERIENCE
    Responsible for account management, customer service and sales training.
    KỸ NĂNG
    Đàm phán, quản lý khách hàng, lead generation and business development.
    EDUCATION
    Cử nhân Quản trị Kinh doanh and professional training in sales management.
    """

    result = language_detector.detect(text)

    assert result.language == "MIXED"
    assert result.vietnamese_score >= 0.22
    assert result.english_score >= 0.22


def test_returns_unknown_for_insufficient_text(
        language_detector: LanguageDetector,
) -> None:
    result = language_detector.detect(
        "Nguyen Van A"
    )

    assert result.language == "UNKNOWN"
    assert result.vietnamese_score == 0.0
    assert result.english_score == 0.0


def test_detects_english_sections_and_header(
        section_detector: SectionDetector,
) -> None:
    text = (
        "JANE CARTER\n"
        "Warehouse Supervisor\n"
        "jane@example.com\n\n"
        "PROFESSIONAL SUMMARY\n"
        "Warehouse operations professional.\n\n"
        "WORK EXPERIENCE\n"
        "Warehouse Supervisor | ABC Logistics\n"
        "2021 - Present\n\n"
        "EDUCATION\n"
        "Diploma in Logistics\n"
    )

    result = section_detector.detect(text)

    assert [
               section.section_type
               for section in result.sections
           ] == [
               "HEADER",
               "SUMMARY",
               "WORK_EXPERIENCE",
               "EDUCATION",
           ]

    assert result.section_texts[
        "HEADER"
    ][0].startswith(
        "JANE CARTER"
    )

    assert (
            "Warehouse operations professional"
            in result.section_texts[
                "SUMMARY"
            ][0]
    )

    assert (
            result.sections[0].start_offset
            == 0
    )

    assert (
            result.sections[-1].end_offset
            == len(text)
    )

    assert all(
        section.text is None
        for section in result.sections
    )


def test_detects_vietnamese_numbered_headings(
        section_detector: SectionDetector,
) -> None:
    text = (
        "TRẦN VĂN BÌNH\n"
        "Kỹ thuật viên cơ khí\n\n"
        "1. TÓM TẮT NGHỀ NGHIỆP:\n"
        "Có kinh nghiệm bảo trì thiết bị.\n\n"
        "2. KINH NGHIỆM LÀM VIỆC\n"
        "Kỹ thuật viên tại Nhà máy ABC\n"
        "01/2020 - Hiện tại\n\n"
        "III. HỌC VẤN\n"
        "Cao đẳng Cơ khí\n"
    )

    result = section_detector.detect(text)

    section_types = [
        section.section_type
        for section in result.sections
    ]

    assert section_types == [
        "HEADER",
        "SUMMARY",
        "WORK_EXPERIENCE",
        "EDUCATION",
    ]

    assert (
            result.sections[1].heading
            == "1. TÓM TẮT NGHỀ NGHIỆP:"
    )

    assert (
            "Cao đẳng Cơ khí"
            in result.section_texts[
                "EDUCATION"
            ][0]
    )


def test_keeps_whole_document_as_header_when_no_section_is_detected(
        section_detector: SectionDetector,
) -> None:
    text = (
        "Nguyen Van A\n"
        "Accountant\n"
        "Email: candidate@example.com"
    )

    result = section_detector.detect(text)

    assert len(result.sections) == 1

    assert (
            result.sections[0].section_type
            == "HEADER"
    )

    assert (
            result.sections[0].start_offset
            == 0
    )

    assert (
            result.sections[0].end_offset
            == len(text)
    )

    assert result.section_texts == {
        "HEADER": (text,),
    }


def test_warns_about_unclassified_heading(
        section_detector: SectionDetector,
) -> None:
    text = (
        "ALEX MORGAN\n\n"
        "CAREER HIGHLIGHTS\n"
        "Improved warehouse accuracy by 20 percent.\n\n"
        "SKILLS\n"
        "Inventory Management\n"
    )

    result = section_detector.detect(text)

    assert (
            "UNCLASSIFIED_SECTIONS_PRESENT"
            in result.warnings
    )

    assert (
            "SKILLS"
            in result.section_texts
    )


def test_truncates_section_text_deterministically(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    constrained_settings = (
        settings.model_copy(
            update={
                "max_section_chars": 500,
            }
        )
    )

    detector = SectionDetector(
        settings=constrained_settings,
        taxonomy=taxonomy,
    )

    text = (
            "SUMMARY\n"
            + (
                    "Accounting experience. "
                    * 80
            )
    )

    result = detector.detect(text)

    summary = result.section_texts[
        "SUMMARY"
    ][0]

    assert len(summary) <= 500

    assert (
            "TRUNCATED_SECTION_TEXT"
            in result.warnings
    )

    assert (
            result.sections[0].end_offset
            == len(text)
    )


def test_text_normalizer_preserves_vietnamese_and_normalizes_layout(
        settings: Settings,
) -> None:
    normalizer = TextNormalizer(
        settings
    )

    result = normalizer.normalize(
        "  KỸ NĂNG\r\n"
        "\u2022   Quản lý kho   \r\n"
        "\r\n\r\n"
        "\u0000Microsoft Excel\t\t nâng cao  "
    )

    assert result.text == (
        "KỸ NĂNG\n"
        "- Quản lý kho\n\n"
        "Microsoft Excel nâng cao"
    )

    assert result.warnings == ()


def test_text_normalizer_warns_when_extracted_text_is_truncated(
        settings: Settings,
) -> None:
    constrained_settings = (
        settings.model_copy(
            update={
                "max_extracted_chars": 1_000,
            }
        )
    )

    normalizer = TextNormalizer(
        constrained_settings
    )

    result = normalizer.normalize(
        "A" * 1_500
    )

    assert len(result.text) == 1_000

    assert result.warnings == (
        "TRUNCATED_EXTRACTED_TEXT",
    )

def test_text_normalizer_repairs_utf8_decoded_as_latin1(
        settings: Settings,
) -> None:
    normalizer = TextNormalizer(
        settings
    )

    expected_text = (
        "Ngô Thị Hồng Ánh\n"
        "MỤC TIÊU NGHỀ NGHIỆP\n"
        "Trường Đại Học Ngân Hàng"
    )

    mojibake_text = (
        expected_text
        .encode("utf-8")
        .decode("latin1")
    )

    result = normalizer.normalize(
        mojibake_text
    )

    assert result.text == expected_text

    assert result.warnings == (
        "TEXT_ENCODING_REPAIRED",
    )


def test_text_normalizer_repairs_cp1252_punctuation_mojibake(
        settings: Settings,
) -> None:
    normalizer = TextNormalizer(
        settings
    )

    result = normalizer.normalize(
        "Senior â€“ Marketing Specialist"
    )

    assert result.text == (
        "Senior – Marketing Specialist"
    )

    assert result.warnings == (
        "TEXT_ENCODING_REPAIRED",
    )


def test_text_normalizer_does_not_modify_valid_unicode(
        settings: Settings,
) -> None:
    normalizer = TextNormalizer(
        settings
    )

    source = (
        "NGÔ THỊ HỒNG ÁNH\n"
        "KỸ NĂNG GIAO TIẾP\n"
        "JOÃO SILVA\n"
        "Café © 2026"
    )

    result = normalizer.normalize(
        source
    )

    assert result.text == source
    assert result.warnings == ()