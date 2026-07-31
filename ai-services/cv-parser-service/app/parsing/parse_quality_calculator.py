from __future__ import annotations

from dataclasses import dataclass

from app.schemas import (
    ContactInformation,
    Education,
    ParseQuality,
    ParsedSection,
    Skill,
    WorkExperience,
)


IMPORTANT_SECTION_TYPES = {
    "SUMMARY",
    "OBJECTIVE",
    "SKILLS",
    "WORK_EXPERIENCE",
    "EDUCATION",
    "CERTIFICATIONS",
    "LICENSES",
    "LANGUAGES",
}

AMBIGUOUS_WARNING_FIELDS = {
    "AMBIGUOUS_WORK_EXPERIENCE_DATE": (
        "workExperiences.date"
    ),
    "WORK_EXPERIENCE_PARTIALLY_PARSED": (
        "workExperiences"
    ),
    "EDUCATION_PARTIALLY_PARSED": (
        "educations"
    ),
    "CERTIFICATION_PARTIALLY_PARSED": (
        "certifications"
    ),
    "LICENSE_PARTIALLY_PARSED": (
        "licenses"
    ),
    "SKILL_TAXONOMY_MATCH_LOW": (
        "skills"
    ),
    "CV_LANGUAGE_UNKNOWN": (
        "detectedLanguage"
    ),
    "TEXT_LAYOUT_MAY_BE_LOST": (
        "rawText.layout"
    ),
    "MULTI_COLUMN_LAYOUT_SUSPECTED": (
        "rawText.layout"
    ),
    "UNCLASSIFIED_SECTIONS_PRESENT": (
        "sections"
    ),
    "TRUNCATED_EXTRACTED_TEXT": (
        "rawText"
    ),
    "TRUNCATED_SECTION_TEXT": (
        "sections"
    ),
}


@dataclass(frozen=True, slots=True)
class _ScoreComponents:
    text_extraction: float
    section_detection: float
    work_experience: float
    completeness: float


class ParseQualityCalculator:
    def calculate(
            self,
            *,
            raw_text: str,
            sections: (
                    list[ParsedSection]
                    | tuple[ParsedSection, ...]
            ),
            full_name: str | None,
            headline: str | None,
            contact: ContactInformation,
            skills: (
                    list[Skill]
                    | tuple[Skill, ...]
            ),
            work_experiences: (
                    list[WorkExperience]
                    | tuple[WorkExperience, ...]
            ),
            educations: (
                    list[Education]
                    | tuple[Education, ...]
            ),
            parser_warnings: (
                    list[str]
                    | tuple[str, ...]
            ),
    ) -> ParseQuality:
        warnings = tuple(
            dict.fromkeys(parser_warnings)
        )

        missing_fields = (
            self._missing_important_fields(
                full_name=full_name,
                headline=headline,
                contact=contact,
                skills=skills,
                work_experiences=(
                    work_experiences
                ),
                educations=educations,
            )
        )

        ambiguous_fields = (
            self._ambiguous_fields(
                warnings
            )
        )

        components = _ScoreComponents(
            text_extraction=(
                self._text_extraction_score(
                    raw_text,
                    warnings,
                )
            ),
            section_detection=(
                self._section_detection_score(
                    sections,
                    warnings,
                )
            ),
            work_experience=(
                self._work_experience_score(
                    work_experiences,
                    sections,
                )
            ),
            completeness=(
                self._completeness_score(
                    full_name=full_name,
                    headline=headline,
                    contact=contact,
                    skills=skills,
                    work_experiences=(
                        work_experiences
                    ),
                    educations=educations,
                )
            ),
        )

        overall = (
                components.text_extraction
                * 0.35
                + components.section_detection
                * 0.25
                + components.work_experience
                * 0.25
                + components.completeness
                * 0.15
        )

        overall -= (
            self._overall_warning_penalty(
                warnings
            )
        )

        overall = self._clamp(
            overall
        )

        return ParseQuality(
            overallScore=round(
                overall,
                4,
            ),
            textExtractionScore=round(
                components.text_extraction,
                4,
            ),
            sectionDetectionScore=round(
                components.section_detection,
                4,
            ),
            workExperienceScore=round(
                components.work_experience,
                4,
            ),
            missingImportantFields=(
                missing_fields
            ),
            ambiguousFields=(
                ambiguous_fields
            ),
        )

    @staticmethod
    def _text_extraction_score(
            raw_text: str,
            warnings: tuple[str, ...],
    ) -> float:
        length = len(
            raw_text.strip()
        )

        if length < 100:
            score = 0.15
        elif length < 300:
            score = 0.35
        elif length < 800:
            score = 0.58
        elif length < 1_500:
            score = 0.75
        elif length < 5_000:
            score = 0.9
        else:
            score = 1.0

        letters = sum(
            character.isalpha()
            for character in raw_text
        )

        visible = sum(
            not character.isspace()
            for character in raw_text
        )

        alphabetic_ratio = (
                letters
                / max(
            visible,
            1,
        )
        )

        if alphabetic_ratio < 0.35:
            score -= 0.25
        elif alphabetic_ratio < 0.5:
            score -= 0.1

        non_empty_lines = [
            line.strip()
            for line in raw_text.splitlines()
            if line.strip()
        ]

        if non_empty_lines:
            tiny_line_ratio = (
                    sum(
                        len(line) <= 2
                        for line in non_empty_lines
                    )
                    / len(non_empty_lines)
            )

            if tiny_line_ratio >= 0.3:
                score -= 0.18
            elif tiny_line_ratio >= 0.15:
                score -= 0.08

        if (
                "TRUNCATED_EXTRACTED_TEXT"
                in warnings
        ):
            score -= 0.1

        if (
                "TEXT_LAYOUT_MAY_BE_LOST"
                in warnings
        ):
            score -= 0.12

        if (
                "MULTI_COLUMN_LAYOUT_SUSPECTED"
                in warnings
        ):
            score -= 0.08

        return ParseQualityCalculator._clamp(
            score
        )

    @staticmethod
    def _section_detection_score(
            sections: (
                    list[ParsedSection]
                    | tuple[ParsedSection, ...]
            ),
            warnings: tuple[str, ...],
    ) -> float:
        recognized = {
            section.section_type
            for section in sections
            if section.section_type
               not in {
                   "HEADER",
                   "OTHER",
               }
        }

        if not recognized:
            score = 0.05
        else:
            breadth = min(
                len(recognized) / 6.0,
                1.0,
                )

            important_coverage = (
                    len(
                        recognized
                        & IMPORTANT_SECTION_TYPES
                    )
                    / len(
                IMPORTANT_SECTION_TYPES
            )
            )

            score = (
                    breadth * 0.65
                    + important_coverage * 0.35
            )

        if (
                "UNCLASSIFIED_SECTIONS_PRESENT"
                in warnings
        ):
            score -= 0.12

        if (
                "TRUNCATED_SECTION_TEXT"
                in warnings
        ):
            score -= 0.08

        return ParseQualityCalculator._clamp(
            score
        )

    @staticmethod
    def _work_experience_score(
            work_experiences: (
                    list[WorkExperience]
                    | tuple[WorkExperience, ...]
            ),
            sections: (
                    list[ParsedSection]
                    | tuple[ParsedSection, ...]
            ),
    ) -> float:
        has_work_section = any(
            section.section_type
            == "WORK_EXPERIENCE"
            for section in sections
        )

        if not work_experiences:
            return (
                0.05
                if has_work_section
                else 0.0
            )

        entry_scores: list[float] = []

        for experience in work_experiences:
            score = 0.0

            score += (
                0.24
                if experience.job_title
                else 0.0
            )

            score += (
                0.18
                if experience.company_name
                else 0.0
            )

            score += (
                0.2
                if experience.start_date
                else 0.0
            )

            score += (
                0.14
                if (
                        experience.end_date
                        or experience.current
                )
                else 0.0
            )

            score += (
                0.12
                if (
                        experience.responsibilities
                        or experience.description
                )
                else 0.0
            )

            score += (
                0.06
                if experience.achievements
                else 0.0
            )

            score += (
                0.06
                if (
                        experience.skills
                        or experience.tools
                        or experience.equipment
                )
                else 0.0
            )

            entry_scores.append(score)

        completeness = (
                sum(entry_scores)
                / len(entry_scores)
        )

        volume_bonus = (
                min(
                    len(work_experiences) / 3.0,
                    1.0,
                    )
                * 0.08
        )

        return ParseQualityCalculator._clamp(
            completeness
            + volume_bonus
        )

    @staticmethod
    def _completeness_score(
            *,
            full_name: str | None,
            headline: str | None,
            contact: ContactInformation,
            skills: (
                    list[Skill]
                    | tuple[Skill, ...]
            ),
            work_experiences: (
                    list[WorkExperience]
                    | tuple[WorkExperience, ...]
            ),
            educations: (
                    list[Education]
                    | tuple[Education, ...]
            ),
    ) -> float:
        score = 0.0

        score += (
            0.2
            if full_name
            else 0.0
        )

        score += (
            0.1
            if headline
            else 0.0
        )

        score += (
            0.15
            if (
                    contact.email
                    or contact.phone
            )
            else 0.0
        )

        score += (
            0.15
            if skills
            else 0.0
        )

        score += (
            0.25
            if work_experiences
            else 0.0
        )

        score += (
            0.15
            if educations
            else 0.0
        )

        return ParseQualityCalculator._clamp(
            score
        )

    @staticmethod
    def _missing_important_fields(
            *,
            full_name: str | None,
            headline: str | None,
            contact: ContactInformation,
            skills: (
                    list[Skill]
                    | tuple[Skill, ...]
            ),
            work_experiences: (
                    list[WorkExperience]
                    | tuple[WorkExperience, ...]
            ),
            educations: (
                    list[Education]
                    | tuple[Education, ...]
            ),
    ) -> list[str]:
        missing: list[str] = []

        if not full_name:
            missing.append("fullName")

        if not headline:
            missing.append("headline")

        if (
                not contact.email
                and not contact.phone
        ):
            missing.append("contact")

        if not skills:
            missing.append("skills")

        if not work_experiences:
            missing.append(
                "workExperiences"
            )

        if not educations:
            missing.append("educations")

        return missing

    @staticmethod
    def _ambiguous_fields(
            warnings: tuple[str, ...],
    ) -> list[str]:
        result: list[str] = []
        seen: set[str] = set()

        for warning in warnings:
            field = (
                AMBIGUOUS_WARNING_FIELDS.get(
                    warning
                )
            )

            if (
                    field is None
                    or field in seen
            ):
                continue

            seen.add(field)
            result.append(field)

        return result

    @staticmethod
    def _overall_warning_penalty(
            warnings: tuple[str, ...],
    ) -> float:
        penalty = 0.0

        if (
                "WORK_EXPERIENCE_PARTIALLY_PARSED"
                in warnings
        ):
            penalty += 0.04

        if (
                "AMBIGUOUS_WORK_EXPERIENCE_DATE"
                in warnings
        ):
            penalty += 0.03

        if (
                "EDUCATION_PARTIALLY_PARSED"
                in warnings
        ):
            penalty += 0.02

        if (
                "UNCLASSIFIED_SECTIONS_PRESENT"
                in warnings
        ):
            penalty += 0.02

        if (
                "CV_LANGUAGE_UNKNOWN"
                in warnings
        ):
            penalty += 0.02

        return min(
            penalty,
            0.15,
        )

    @staticmethod
    def _clamp(
            value: float,
    ) -> float:
        return max(
            0.0,
            min(
                1.0,
                value,
            ),
        )