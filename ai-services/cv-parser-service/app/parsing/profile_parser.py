from __future__ import annotations

from app.config import Settings
from app.exceptions import CvTextNotExtractableError
from app.normalization.value_normalizer import stable_unique
from app.parsing.contact_parser import ContactParser
from app.parsing.education_parser import EducationParser
from app.parsing.experience_calculator import ExperienceCalculator
from app.parsing.identity_parser import IdentityParser
from app.parsing.language_detector import LanguageDetector
from app.parsing.parse_quality_calculator import ParseQualityCalculator
from app.parsing.secondary_section_parsers import SecondarySectionParsers
from app.parsing.section_detector import SectionDetector
from app.parsing.seniority_parser import SeniorityParser
from app.parsing.skill_parser import SkillParser
from app.parsing.work_experience_parser import WorkExperienceParser
from app.schemas import (
    CandidateProfilePayload,
    ParseCvResponse,
    WorkExperience,
)
from app.taxonomy.taxonomy_loader import TaxonomyBundle


MAX_PARSER_WARNINGS = 100
MAX_RECENT_CAREER_VALUES = 5


class ProfileParser:
    def __init__(
            self,
            settings: Settings,
            taxonomy: TaxonomyBundle,
    ) -> None:
        self._settings = settings

        self._section_detector = SectionDetector(
            settings,
            taxonomy,
        )
        self._language_detector = LanguageDetector()

        self._contact_parser = ContactParser(
            settings,
            taxonomy,
        )
        self._identity_parser = IdentityParser(
            taxonomy
        )
        self._skill_parser = SkillParser(
            settings,
            taxonomy,
        )
        self._work_experience_parser = WorkExperienceParser(
            settings,
            taxonomy,
            self._skill_parser,
        )
        self._education_parser = EducationParser(
            settings,
            taxonomy,
        )
        self._secondary_parsers = SecondarySectionParsers(
            settings,
            taxonomy,
            self._skill_parser,
        )

        self._experience_calculator = ExperienceCalculator()
        self._seniority_parser = SeniorityParser(taxonomy.seniority)
        self._quality_calculator = ParseQualityCalculator()

    def parse(
            self,
            raw_cv_id: str,
            raw_text: str,
            extraction_warnings: tuple[str, ...],
    ) -> ParseCvResponse:
        if not raw_text.strip():
            raise CvTextNotExtractableError(
                raw_cv_id=raw_cv_id
            )

        detected_sections = self._section_detector.detect(
            raw_text
        )
        section_texts = detected_sections.section_texts

        language_result = self._language_detector.detect(
            raw_text
        )

        contact_result = self._contact_parser.parse(
            raw_text,
            section_texts,
        )

        identity_result = self._identity_parser.parse(
            raw_text,
            section_texts,
        )

        skill_result = self._skill_parser.parse(
            raw_text,
            section_texts,
        )

        work_result = self._work_experience_parser.parse(
            section_texts
        )

        education_result = self._education_parser.parse(
            section_texts
        )

        secondary_result = self._secondary_parsers.parse(
            section_texts
        )

        skills = list(
            skill_result.skills
        )
        work_experiences = list(
            work_result.work_experiences
        )
        educations = list(
            education_result.educations
        )

        experience_result = self._experience_calculator.calculate(
            work_experiences,
            raw_text,
        )

        seniority_result = self._seniority_parser.parse(
            headline=identity_result.headline,
            target_job_titles=(
                identity_result.target_job_titles
            ),
            work_experiences=work_experiences,
            experience_years=(
                experience_result.experience_years
            ),
        )

        warnings = self._collect_warnings(
            extraction_warnings=extraction_warnings,
            section_warnings=(
                detected_sections.warnings
            ),
            identity_warnings=(
                identity_result.warnings
            ),
            skill_warnings=skill_result.warnings,
            work_warnings=work_result.warnings,
            education_warnings=(
                education_result.warnings
            ),
            secondary_warnings=(
                secondary_result.warnings
            ),
            experience_warnings=(
                experience_result.warnings
            ),
            seniority_warnings=(
                seniority_result.warnings
            ),
            detected_language=(
                language_result.language
            ),
        )

        sections = list(
            detected_sections.sections
        )

        parse_quality = self._quality_calculator.calculate(
            raw_text=raw_text,
            sections=sections,
            full_name=identity_result.full_name,
            headline=identity_result.headline,
            contact=contact_result.contact,
            skills=skills,
            work_experiences=work_experiences,
            educations=educations,
            parser_warnings=warnings,
        )

        (
            recent_job_titles,
            recent_companies,
        ) = self._recent_career_values(
            work_experiences
        )

        profile = CandidateProfilePayload(
            full_name=identity_result.full_name,
            headline=identity_result.headline,
            professional_summary=(
                identity_result.professional_summary
            ),
            career_objective=(
                identity_result.career_objective
            ),
            contact=contact_result.contact,
            links=list(
                contact_result.links
            ),
            target_job_titles=list(
                identity_result.target_job_titles
            ),
            target_industries=list(
                identity_result.target_industries
            ),
            preferred_locations=list(
                secondary_result.preferred_locations
            ),
            preferred_work_modes=list(
                secondary_result.preferred_work_modes
            ),
            preferred_employment_types=list(
                secondary_result.preferred_employment_types
            ),
            expected_salary_text=(
                secondary_result.expected_salary_text
            ),
            availability_text=(
                secondary_result.availability_text
            ),
            skills=skills,
            work_experiences=work_experiences,
            projects=list(
                secondary_result.projects
            ),
            educations=educations,
            certifications=list(
                secondary_result.certifications
            ),
            licenses=list(
                secondary_result.licenses
            ),
            languages=list(
                secondary_result.languages
            ),
            awards=list(
                secondary_result.awards
            ),
            publications=list(
                secondary_result.publications
            ),
            volunteer_experiences=list(
                secondary_result.volunteer_experiences
            ),
            activities=list(
                secondary_result.activities
            ),
            training_courses=list(
                secondary_result.training_courses
            ),
            interests=list(
                secondary_result.interests
            ),
            experience_years=(
                experience_result.experience_years
            ),
            seniority=seniority_result.seniority,
            highest_education_level=(
                education_result.highest_education_level
            ),
            recent_job_titles=recent_job_titles,
            recent_companies=recent_companies,
            raw_text=raw_text,
            sections=sections,
            parser_warnings=warnings,
            parse_quality=parse_quality,
        )

        return ParseCvResponse(
            raw_cv_id=raw_cv_id,
            parser_version=(
                self._settings.parser_version
            ),
            extracted_text_length=len(raw_text),
            detected_language=(
                language_result.language
            ),
            profile=profile,
            warnings=warnings,
        )

    @staticmethod
    def _collect_warnings(
            *,
            extraction_warnings: tuple[str, ...],
            section_warnings: tuple[str, ...],
            identity_warnings: tuple[str, ...],
            skill_warnings: tuple[str, ...],
            work_warnings: tuple[str, ...],
            education_warnings: tuple[str, ...],
            secondary_warnings: tuple[str, ...],
            experience_warnings: tuple[str, ...],
            seniority_warnings: tuple[str, ...],
            detected_language: str,
    ) -> list[str]:
        values: list[str] = [
            *extraction_warnings,
            *section_warnings,
            *identity_warnings,
            *skill_warnings,
            *work_warnings,
            *education_warnings,
            *secondary_warnings,
            *experience_warnings,
            *seniority_warnings,
        ]

        if detected_language == "UNKNOWN":
            values.append(
                "CV_LANGUAGE_UNKNOWN"
            )

        return stable_unique(
            values,
            maximum_items=MAX_PARSER_WARNINGS,
        )

    @staticmethod
    def _recent_career_values(
            work_experiences: list[WorkExperience],
    ) -> tuple[list[str], list[str]]:
        ordered = sorted(
            enumerate(work_experiences),
            key=lambda item: (
                ProfileParser._date_sort_key(
                    item[1]
                ),
                -item[0],
            ),
            reverse=True,
        )

        recent_job_titles = stable_unique(
            (
                experience.job_title
                for _, experience in ordered
                if experience.job_title
            ),
            maximum_items=(
                MAX_RECENT_CAREER_VALUES
            ),
        )

        recent_companies = stable_unique(
            (
                experience.company_name
                for _, experience in ordered
                if experience.company_name
            ),
            maximum_items=(
                MAX_RECENT_CAREER_VALUES
            ),
        )

        return (
            recent_job_titles,
            recent_companies,
        )

    @staticmethod
    def _date_sort_key(
            experience: WorkExperience,
    ) -> tuple[int, str, str]:
        current_rank = (
            1
            if experience.current
            else 0
        )

        end_date = (
            "9999-12"
            if experience.current
            else (
                    experience.end_date
                    or "0000"
            )
        )

        start_date = (
                experience.start_date
                or "0000"
        )

        return (
            current_rank,
            end_date,
            start_date,
        )