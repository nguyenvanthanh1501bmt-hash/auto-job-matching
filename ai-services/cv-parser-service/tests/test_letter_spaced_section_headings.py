from __future__ import annotations

from app.parsing.section_detector import (
    SectionDetector,
)


def test_detects_letter_spaced_headings_from_pdf_font(
        section_detector: SectionDetector,
) -> None:
    text = (
        "NGUYEN VAN A\n"
        "GitHub: https://github.com/candidate\n"
        "Phone: 0900000000\n"
        "Email: candidate@example.com\n\n"
        "S U M M A R Y\n"
        "Aspiring Full-stack Developer with "
        "hands-on project experience.\n\n"
        "E D U C A T I O N\n"
        "University of Information Technology\n"
        "Information Technology\n"
        "Sep 2023 - Present\n\n"
        "P R O J E C T S\n"
        "Online Marketplace | Full-stack Developer\n"
        "Tech Stack: React, Node.js, MongoDB\n\n"
        "C E R T I F I C A T I O N S\n"
        "TOEIC Listening and Reading\n\n"
        "S K I L L S\n"
        "Languages: JavaScript, TypeScript, Java\n"
    )

    result = section_detector.detect(text)

    assert [
               section.section_type
               for section in result.sections
           ] == [
               "HEADER",
               "SUMMARY",
               "EDUCATION",
               "PROJECTS",
               "CERTIFICATIONS",
               "SKILLS",
           ]

    assert (
            result.sections[1].heading
            == "S U M M A R Y"
    )

    assert (
            "Aspiring Full-stack Developer"
            in result.section_texts["SUMMARY"][0]
    )

    assert (
            "University of Information Technology"
            in result.section_texts["EDUCATION"][0]
    )

    assert (
            "Online Marketplace"
            in result.section_texts["PROJECTS"][0]
    )

    assert (
            "TOEIC"
            in result.section_texts[
                "CERTIFICATIONS"
            ][0]
    )

    assert (
            "JavaScript"
            in result.section_texts["SKILLS"][0]
    )

    assert (
            "UNCLASSIFIED_SECTIONS_PRESENT"
            not in result.warnings
    )


def test_does_not_compact_normal_non_heading_text(
        section_detector: SectionDetector,
) -> None:
    text = (
        "NGUYEN VAN A\n"
        "A B Testing Specialist\n"
        "candidate@example.com\n"
    )

    result = section_detector.detect(text)

    assert [
               section.section_type
               for section in result.sections
           ] == [
               "HEADER",
           ]

    assert result.section_texts["HEADER"] == (
        text,
    )