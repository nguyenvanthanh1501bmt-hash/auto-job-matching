from __future__ import annotations

from datetime import date

from app.config import Settings
from app.parsing.education_parser import EducationParser
from app.parsing.secondary_section_parsers import (
    ActivityParser,
    AwardParser,
    CareerPreferenceParser,
    CertificationParser,
    InterestParser,
    LanguageParser,
    LicenseParser,
    ProjectParser,
    PublicationParser,
    TrainingParser,
    VolunteerParser,
)
from app.parsing.skill_parser import SkillParser
from app.taxonomy.taxonomy_loader import TaxonomyBundle


def _create_skill_parser(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> SkillParser:
    return SkillParser(
        settings,
        taxonomy,
    )


def test_parses_bachelor_degree_and_field_of_study(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = EducationParser(
        settings,
        taxonomy,
    )

    text = """
    Bachelor of Accounting
    University of Economics
    2016 - 2020
    """

    result = parser.parse(
        {
            "EDUCATION": (text,),
        }
    )

    assert len(result.educations) == 1

    education = result.educations[0]

    assert (
            education.institution_name
            == "University of Economics"
    )

    assert education.degree == "Bachelor of Accounting"

    assert (
            education.normalized_degree_level
            == "BACHELOR"
    )

    assert education.field_of_study == "Accounting"
    assert education.start_date == "2016"
    assert education.end_date == "2020"
    assert education.current is False

    assert (
            result.highest_education_level
            == "BACHELOR"
    )


def test_derives_highest_education_level(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = EducationParser(
        settings,
        taxonomy,
    )

    text = """
    Bachelor of Business Administration
    City University
    2014 - 2018

    Master of Business Administration
    International Business School
    2019 - 2021
    """

    result = parser.parse(
        {
            "EDUCATION": (text,),
        }
    )

    assert len(result.educations) == 2

    levels = {
        education.normalized_degree_level
        for education in result.educations
    }

    assert levels == {
        "BACHELOR",
        "MASTER",
    }

    assert (
            result.highest_education_level
            == "MASTER"
    )


def test_parses_vietnamese_vocational_education(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = EducationParser(
        settings,
        taxonomy,
    )

    text = """
    Trung cấp nghề Điện công nghiệp
    Trường Cao đẳng Kỹ thuật Thành phố Hồ Chí Minh
    2018 - 2020
    """

    result = parser.parse(
        {
            "EDUCATION": (text,),
        }
    )

    assert len(result.educations) == 1

    education = result.educations[0]

    assert (
            education.normalized_degree_level
            == "VOCATIONAL"
    )

    assert education.degree == (
        "Trung cấp nghề Điện công nghiệp"
    )

    assert education.institution_name == (
        "Trường Cao đẳng Kỹ thuật Thành phố Hồ Chí Minh"
    )

    assert education.start_date == "2018"
    assert education.end_date == "2020"


def test_parses_current_education(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = EducationParser(
        settings,
        taxonomy,
    )

    text = """
    Master of Nursing
    University of Health Sciences
    2024 - Present
    """

    result = parser.parse(
        {
            "EDUCATION": (text,),
        }
    )

    assert len(result.educations) == 1

    education = result.educations[0]

    assert (
            education.normalized_degree_level
            == "MASTER"
    )

    assert education.start_date == "2024"
    assert education.end_date is None
    assert education.current is True


def test_warns_when_education_is_partially_parsed(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = EducationParser(
        settings,
        taxonomy,
    )

    text = """
    University of Economics
    2018 - 2022
    """

    result = parser.parse(
        {
            "EDUCATION": (text,),
        }
    )

    assert len(result.educations) == 1

    education = result.educations[0]

    assert (
            education.institution_name
            == "University of Economics"
    )

    assert education.degree is None

    assert (
            "EDUCATION_PARTIALLY_PARSED"
            in result.warnings
    )


def test_parses_certification_with_issuer_and_dates(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    skill_parser = _create_skill_parser(
        settings,
        taxonomy,
    )

    parser = CertificationParser(
        settings,
        skill_parser,
    )

    text = """
    CPA
    Issuer: Vietnam Association of Certified Public Accountants
    Issued: 2024-06
    Expires: 2030-06
    Credential ID: CPA-123456
    Credential URL: https://credentials.example.com/cpa-123456
    """

    certifications, warnings = parser.parse(
        {
            "CERTIFICATIONS": (text,),
        },
        today=date(
            2026,
            7,
            31,
        ),
    )

    assert len(certifications) == 1

    certification = certifications[0]

    assert certification.name == "CPA"

    assert certification.issuer == (
        "Vietnam Association of Certified Public Accountants"
    )

    assert certification.issued_date == "2024-06"
    assert certification.expiration_date == "2030-06"
    assert certification.expired is False
    assert certification.credential_id == "CPA-123456"

    assert certification.credential_url == (
        "https://credentials.example.com/cpa-123456"
    )

    assert warnings == ()


def test_parses_unlabelled_issuer_name_scores_and_trailing_date(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = CertificationParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = "IIG Vietnam - TOEIC L&R 635, S&W 270 September 2025"

    certifications, warnings = parser.parse(
        {
            "CERTIFICATIONS": (text,),
        }
    )

    assert len(certifications) == 1

    certification = certifications[0]

    assert certification.name == "TOEIC L&R 635, S&W 270"
    assert certification.issuer == "IIG Vietnam"
    assert certification.issued_date == "2025-09"
    assert warnings == ()


def test_parses_provider_first_certificate_with_trailing_date(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = CertificationParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = (
        "Google - "
        "Data Analytics Professional Certificate "
        "May 2024"
    )

    certifications, warnings = parser.parse(
        {
            "CERTIFICATIONS": (text,),
        }
    )

    assert len(certifications) == 1

    certification = certifications[0]

    assert certification.name == (
        "Data Analytics Professional Certificate"
    )
    assert certification.issuer == "Google"
    assert certification.issued_date == "2024-05"
    assert warnings == ()


def test_parses_certificate_first_provider_second(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = CertificationParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = (
        "AWS Certified Cloud Practitioner - "
        "Amazon Web Services September 2024"
    )

    certifications, warnings = parser.parse(
        {
            "CERTIFICATIONS": (text,),
        }
    )

    assert len(certifications) == 1

    certification = certifications[0]

    assert certification.name == (
        "AWS Certified Cloud Practitioner"
    )
    assert certification.issuer == "Amazon Web Services"
    assert certification.issued_date == "2024-09"
    assert warnings == ()


def test_parses_unaccented_vietnamese_inline_certification(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = CertificationParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = (
        "Trung tam Dao tao ABC - "
        "Chung chi Quan ly du an 09/2025"
    )

    certifications, warnings = parser.parse(
        {
            "CERTIFICATIONS": (text,),
        }
    )

    assert len(certifications) == 1

    certification = certifications[0]

    assert certification.name == (
        "Chung chi Quan ly du an"
    )
    assert certification.issuer == (
        "Trung tam Dao tao ABC"
    )
    assert certification.issued_date == "2025-09"
    assert warnings == ()


def test_does_not_split_descriptive_hyphenated_certificate_title(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = CertificationParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = (
        "Data Engineering - "
        "Advanced Certificate May 2024"
    )

    certifications, _ = parser.parse(
        {
            "CERTIFICATIONS": (text,),
        }
    )

    assert len(certifications) == 1

    certification = certifications[0]

    assert certification.name == (
        "Data Engineering - Advanced Certificate"
    )
    assert certification.issuer is None
    assert certification.issued_date == "2024-05"


def test_does_not_treat_standard_version_year_as_issued_date(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = CertificationParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = "ISO 27001:2022 Lead Auditor"

    certifications, warnings = parser.parse(
        {
            "CERTIFICATIONS": (text,),
        }
    )

    assert len(certifications) == 1

    certification = certifications[0]

    assert certification.name == (
        "ISO 27001:2022 Lead Auditor"
    )
    assert certification.issuer is None
    assert certification.issued_date is None

    assert warnings == (
        "CERTIFICATION_PARTIALLY_PARSED",
    )


def test_does_not_split_semantic_separator_inside_exam_name(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = CertificationParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = (
        "TOEIC L&R - "
        "Listening and Reading September 2025"
    )

    certifications, _ = parser.parse(
        {
            "CERTIFICATIONS": (text,),
        }
    )

    assert len(certifications) == 1

    certification = certifications[0]

    assert certification.name == (
        "TOEIC L&R - Listening and Reading"
    )
    assert certification.issuer is None
    assert certification.issued_date == "2025-09"


def test_marks_expired_certification(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    skill_parser = _create_skill_parser(
        settings,
        taxonomy,
    )

    parser = CertificationParser(
        settings,
        skill_parser,
    )

    text = """
    Food Safety Certificate
    Issuer: Hospitality Training Center
    Issued: 2021-01
    Expires: 2025-01
    """

    certifications, _ = parser.parse(
        {
            "CERTIFICATIONS": (text,),
        },
        today=date(
            2026,
            7,
            31,
        ),
    )

    assert len(certifications) == 1
    assert certifications[0].expired is True


def test_parses_professional_license_without_losing_number(
        settings: Settings,
) -> None:
    parser = LicenseParser(settings)

    text = """
    Forklift Operator License
    Issuing Authority: Workplace Safety Authority
    License Number: FL-2023-9988
    Issued: 2023-01
    Expires: 2028-01
    Jurisdiction: Ho Chi Minh City
    """

    licenses, warnings = parser.parse(
        {
            "LICENSES": (text,),
        },
        today=date(
            2026,
            7,
            31,
        ),
    )

    assert len(licenses) == 1

    license_entry = licenses[0]

    assert (
            license_entry.name
            == "Forklift Operator License"
    )

    assert license_entry.issuing_authority == (
        "Workplace Safety Authority"
    )

    assert (
            license_entry.license_number
            == "FL-2023-9988"
    )

    assert license_entry.issued_date == "2023-01"
    assert license_entry.expiration_date == "2028-01"
    assert license_entry.expired is False

    assert (
            license_entry.jurisdiction
            == "Ho Chi Minh City"
    )

    assert warnings == ()


def test_parses_training_separately_from_certification(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    skill_parser = _create_skill_parser(
        settings,
        taxonomy,
    )

    parser = TrainingParser(
        settings,
        skill_parser,
    )

    text = """
    Advanced Excel for Finance
    Provider: Finance Training Center
    Completed: 2024-03
    Duration: 20 hours
    Financial reporting exercises using Microsoft Excel.
    """

    courses = parser.parse(
        {
            "TRAINING": (text,),
        }
    )

    assert len(courses) == 1

    course = courses[0]

    assert (
            course.name
            == "Advanced Excel for Finance"
    )

    assert (
            course.provider
            == "Finance Training Center"
    )

    assert course.completion_date == "2024-03"
    assert course.duration_text == "20 hours"

    assert (
            "Microsoft Excel"
            in course.related_skills
    )

    assert (
            "Lập báo cáo tài chính"
            in course.related_skills
    )


def test_parses_language_frameworks_and_scores(
        taxonomy: TaxonomyBundle,
) -> None:
    parser = LanguageParser(taxonomy)

    text = """
    English - IELTS 7.0
    Japanese - JLPT N2
    Chinese - HSK 5
    """

    languages = parser.parse(
        {
            "LANGUAGES": (text,),
        }
    )

    assert len(languages) == 3

    by_language = {
        language.language: language
        for language in languages
    }

    english = by_language["English"]

    assert english.framework == "IELTS"
    assert english.score == "7.0"

    japanese = by_language["Japanese"]

    assert japanese.framework == "JLPT"
    assert japanese.score == "N2"

    chinese = by_language["Chinese"]

    assert chinese.framework == "HSK"
    assert chinese.score == "5"


def test_normalizes_vietnamese_language_proficiency(
        taxonomy: TaxonomyBundle,
) -> None:
    parser = LanguageParser(taxonomy)

    languages = parser.parse(
        {
            "LANGUAGES": (
                "Tiếng Anh - Giao tiếp tốt",
            ),
        }
    )

    assert len(languages) == 1

    language = languages[0]

    assert language.language == "English"

    assert (
            language.normalized_proficiency
            == "UPPER_INTERMEDIATE"
    )

    assert language.proficiency_text is not None


def test_parses_career_preferences_only_from_preference_context(
        taxonomy: TaxonomyBundle,
) -> None:
    parser = CareerPreferenceParser(
        taxonomy
    )

    header = """
    Preferred Location: Ho Chi Minh City, Hanoi
    Preferred Work Mode: Remote, Hybrid
    Preferred Employment Type: Full-time, Contract
    Expected Salary: 30-35 million VND
    Availability: Available after 30 days
    """

    result = parser.parse(
        {
            "HEADER": (header,),
        }
    )

    assert result.preferred_locations == (
        "Hồ Chí Minh",
        "Hà Nội",
    )

    assert set(
        result.preferred_work_modes
    ) == {
               "REMOTE",
               "HYBRID",
           }

    assert set(
        result.preferred_employment_types
    ) == {
               "FULL_TIME",
               "CONTRACT",
           }

    assert (
            result.expected_salary_text
            == "30-35 million VND"
    )

    assert (
            result.availability_text
            == "Available after 30 days"
    )

    assert (
            "PREFERRED_LOCATION_NOT_DETECTED"
            not in result.warnings
    )


def test_does_not_use_previous_job_context_as_career_preference(
        taxonomy: TaxonomyBundle,
) -> None:
    parser = CareerPreferenceParser(
        taxonomy
    )

    work_text = """
    Warehouse Supervisor | ABC Logistics | Hanoi
    2021 - Present
    Employment Type: Full-time
    Work Mode: Onsite
    """

    result = parser.parse(
        {
            "WORK_EXPERIENCE": (
                work_text,
            ),
        }
    )

    assert result.preferred_locations == ()
    assert result.preferred_work_modes == ()
    assert result.preferred_employment_types == ()
    assert result.expected_salary_text is None
    assert result.availability_text is None

    assert result.warnings == (
        "PREFERRED_LOCATION_NOT_DETECTED",
    )


def test_parses_non_software_project_with_scoped_skills(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    skill_parser = _create_skill_parser(
        settings,
        taxonomy,
    )

    parser = ProjectParser(
        settings,
        skill_parser,
    )

    text = """
    Warehouse Optimization Initiative
    2023-01 - 2023-06
    Role: Project Lead
    Domain: Logistics Operations
    Description: Improved warehouse layout and stock movement.
    - Reviewed inventory management processes.
    - Prepared analysis using Microsoft Excel.
    - Used barcode scanners during process review.
    - Reduced picking time by 20 percent.
    """

    projects = parser.parse(
        {
            "PROJECTS": (text,),
        }
    )

    assert len(projects) == 1

    project = projects[0]

    assert (
            project.name
            == "Warehouse Optimization Initiative"
    )

    assert project.role == "Project Lead"

    assert (
            project.domain
            == "Logistics Operations"
    )

    assert project.start_date == "2023-01"
    assert project.end_date == "2023-06"
    assert project.current is False

    assert (
            "Inventory Management"
            in project.skills
    )

    assert (
            "Microsoft Excel"
            in project.tools
    )

    assert (
            "Barcode Scanner"
            in project.equipment
    )

    assert any(
        "Reduced picking time"
        in achievement
        for achievement in project.achievements
    )


def test_parses_award_as_independent_section() -> None:
    parser = AwardParser()

    text = """
    Employee of the Year
    Issuer: ABC Manufacturing
    Date: 2024
    Recognized for improving operational quality.
    """

    awards = parser.parse(
        {
            "AWARDS": (text,),
        }
    )

    assert len(awards) == 1

    award = awards[0]

    assert (
            award.name
            == "Employee of the Year"
    )

    assert (
            award.issuer
            == "ABC Manufacturing"
    )

    assert award.awarded_date == "2024"


def test_parses_publication_with_authors_and_url() -> None:
    parser = PublicationParser()

    text = """
    Warehouse Safety Improvement Study
    Authors: Jane Carter, John Smith
    Journal: Operations Research Review
    Date: 2025-04
    https://publications.example.com/warehouse-safety-study
    """

    publications = parser.parse(
        {
            "PUBLICATIONS": (text,),
        }
    )

    assert len(publications) == 1

    publication = publications[0]

    assert (
            publication.title
            == "Warehouse Safety Improvement Study"
    )

    assert publication.authors == [
        "Jane Carter",
        "John Smith",
    ]

    assert (
            publication.publisher
            == "Operations Research Review"
    )

    assert (
            publication.published_date
            == "2025-04"
    )

    assert publication.url == (
        "https://publications.example.com/warehouse-safety-study"
    )


def test_parses_volunteer_experience(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    skill_parser = _create_skill_parser(
        settings,
        taxonomy,
    )

    parser = VolunteerParser(
        skill_parser
    )

    text = """
    Organization: Community Support Center
    Role: Volunteer Coordinator
    2022 - 2023
    - Organized customer service training for volunteers.
    - Coordinated community activities.
    """

    experiences = parser.parse(
        {
            "VOLUNTEERING": (text,),
        }
    )

    assert len(experiences) == 1

    experience = experiences[0]

    assert experience.organization_name == (
        "Community Support Center"
    )

    assert (
            experience.role
            == "Volunteer Coordinator"
    )

    assert experience.start_date == "2022"
    assert experience.end_date == "2023"

    assert any(
        "Organized customer service training"
        in responsibility
        for responsibility
        in experience.responsibilities
    )

    assert (
            "Customer Service"
            in experience.skills
    )


def test_parses_professional_activity() -> None:
    parser = ActivityParser()

    text = """
    Organization: Vietnam Logistics Association
    Role: Committee Member
    2023 - Present
    Participated in professional logistics events.
    """

    activities = parser.parse(
        {
            "ACTIVITIES": (text,),
        }
    )

    assert len(activities) == 1

    activity = activities[0]

    assert activity.organization == (
        "Vietnam Logistics Association"
    )

    assert (
            activity.role
            == "Committee Member"
    )

    assert activity.start_date == "2023"
    assert activity.end_date is None


def test_parses_and_limits_interests() -> None:
    parser = InterestParser()

    text = """
    Reading, Photography, Cooking
    Community volunteering
    Supply chain research
    """

    interests = parser.parse(
        {
            "INTERESTS": (text,),
        }
    )

    assert interests == (
        "Reading",
        "Photography",
        "Cooking",
        "Community volunteering",
        "Supply chain research",
    )

def test_parses_inline_project_role_team_size_and_metadata(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    skill_parser = _create_skill_parser(
        settings,
        taxonomy,
    )

    parser = ProjectParser(
        settings,
        skill_parser,
    )

    text = """
    Blockchain-Integrated Car Marketplace Platform | Full-stack Developer
    GitHub: https://github.com/example/car-marketplace.git
    Link: https://car-marketplace.example.com
    Team Project - 3 members
    Tech Stack: React, Next.js, MongoDB
    • Developed frontend and backend marketplace workflows.
    • Built RESTful APIs for product management.
    """

    projects = parser.parse(
        {
            "PROJECTS": (text,),
        }
    )

    assert len(projects) == 1

    project = projects[0]

    assert project.name == (
        "Blockchain-Integrated Car Marketplace Platform"
    )
    assert project.role == "Full-stack Developer"
    assert project.team_size_text == "3 members"
    assert project.repository_url == (
        "https://github.com/example/car-marketplace.git"
    )
    assert project.project_url == (
        "https://car-marketplace.example.com"
    )
    assert "React" in project.skills
    assert "Next.js" in project.skills
    assert "MongoDB" in project.skills
    assert len(project.responsibilities) == 2

    assert project.description is None


def test_parses_vietnamese_inline_project_role_and_team_size(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    skill_parser = _create_skill_parser(
        settings,
        taxonomy,
    )

    parser = ProjectParser(
        settings,
        skill_parser,
    )

    text = """
    Hệ thống quản lý nhà hàng | Lập trình viên Full-stack
    Dự án nhóm - 4 thành viên
    Công nghệ sử dụng: React, Supabase
    • Xây dựng giao diện quản lý bàn và hóa đơn.
    """

    projects = parser.parse(
        {
            "PROJECTS": (text,),
        }
    )

    assert len(projects) == 1

    project = projects[0]

    assert project.name == "Hệ thống quản lý nhà hàng"
    assert project.role == "Lập trình viên Full-stack"
    assert project.team_size_text == "4 thành viên"
    assert "React" in project.skills
    assert "Supabase" in project.skills
    assert project.description is None


def test_does_not_split_project_title_when_pipe_suffix_is_not_role(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    skill_parser = _create_skill_parser(
        settings,
        taxonomy,
    )

    parser = ProjectParser(
        settings,
        skill_parser,
    )

    text = """
    Research | Development Platform
    Personal Project
    Tech Stack: React
    • Built a proof-of-concept platform.
    """

    projects = parser.parse(
        {
            "PROJECTS": (text,),
        }
    )

    assert len(projects) == 1

    project = projects[0]

    assert project.name == "Research | Development Platform"
    assert project.role is None
    assert project.description is None


def test_merges_wrapped_project_bullet_lines_without_polluting_description(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    skill_parser = _create_skill_parser(
        settings,
        taxonomy,
    )

    parser = ProjectParser(
        settings,
        skill_parser,
    )

    text = """
    Marketplace Platform | Full-stack Developer
    Tech Stack: React, MongoDB
    • Developed frontend and backend marketplace workflows,
    focusing on admin management and user-facing features.
    • Reduced page loading time by 20 percent through
    optimized data fetching.
    """

    projects = parser.parse(
        {
            "PROJECTS": (text,),
        }
    )

    assert len(projects) == 1

    project = projects[0]

    assert project.description is None
    assert project.responsibilities == [
        "Developed frontend and backend marketplace workflows, "
        "focusing on admin management and user-facing features."
    ]
    assert project.achievements == [
        "Reduced page loading time by 20 percent through "
        "optimized data fetching."
    ]


def test_does_not_classify_unmeasured_improvement_as_achievement(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ProjectParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = """
    Inventory Portal | Backend Developer
    • Implemented and improved inventory management modules.
    • Built 20 administrative screens for internal workflows.
    """

    projects = parser.parse(
        {
            "PROJECTS": (text,),
        }
    )

    assert len(projects) == 1

    project = projects[0]

    assert project.achievements == []
    assert project.responsibilities == [
        "Implemented and improved inventory management modules.",
        "Built 20 administrative screens for internal workflows.",
    ]


def test_classifies_quantified_and_recognized_project_results_as_achievements(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ProjectParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = """
    Document Processing Service | Software Engineer
    • Reduced average processing time by 35 percent.
    • Won first place in the university innovation contest.
    • Maintained the document upload workflow.
    """

    projects = parser.parse(
        {
            "PROJECTS": (text,),
        }
    )

    assert len(projects) == 1

    project = projects[0]

    assert project.achievements == [
        "Reduced average processing time by 35 percent.",
        "Won first place in the university innovation contest.",
    ]
    assert project.responsibilities == [
        "Maintained the document upload workflow."
    ]


def test_parses_unaccented_vietnamese_project_labels_and_standalone_role(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ProjectParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = """
    Ten du an: He thong theo doi don hang
    Vai tro: Lap trinh vien Backend
    Linh vuc: Logistics
    Du an nhom - 5 thanh vien
    Cong nghe su dung: Java, Spring Boot
    Mo ta: He thong ho tro theo doi trang thai giao hang.
    • Giam thoi gian xu ly tu 10 giay xuong 4 giay.
    """

    projects = parser.parse(
        {
            "PROJECTS": (text,),
        }
    )

    assert len(projects) == 1

    project = projects[0]

    assert project.name == "He thong theo doi don hang"
    assert project.role == "Lap trinh vien Backend"
    assert project.domain == "Logistics"
    assert project.team_size_text == "5 thanh vien"
    assert project.description == (
        "He thong ho tro theo doi trang thai giao hang."
    )
    assert project.achievements == [
        "Giam thoi gian xu ly tu 10 giay xuong 4 giay."
    ]


def test_merges_uppercase_acronym_bullet_continuation_only_when_previous_is_incomplete(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ProjectParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = """
    Identity Gateway | Backend Engineer
    • Integrated authentication with
    OAuth 2.0 and Google Identity services.
    • Documented deployment procedures.
    """

    projects = parser.parse(
        {
            "PROJECTS": (text,),
        }
    )

    assert len(projects) == 1

    project = projects[0]

    assert project.responsibilities == [
        "Integrated authentication with OAuth 2.0 and Google Identity services.",
        "Documented deployment procedures.",
    ]
    assert project.description is None


def test_preserves_hyphenated_project_name_without_guessing_inline_role(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ProjectParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = """
    AI-Powered Reading and Research Platform
    Role: Front-end Developer
    • Built accessible reading interfaces.
    """

    projects = parser.parse(
        {
            "PROJECTS": (text,),
        }
    )

    assert len(projects) == 1

    project = projects[0]

    assert project.name == (
        "AI-Powered Reading and Research Platform"
    )
    assert project.role == "Front-end Developer"


def test_parses_non_software_project_using_the_same_structural_rules(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ProjectParser(
        settings,
        _create_skill_parser(
            settings,
            taxonomy,
        ),
    )

    text = """
    Community Health Survey | Research Analyst
    Team size: 6 people
    Domain: Public Health
    • Designed the participant questionnaire.
    • Increased completed responses by 25 percent.
    """

    projects = parser.parse(
        {
            "PROJECTS": (text,),
        }
    )

    assert len(projects) == 1

    project = projects[0]

    assert project.name == "Community Health Survey"
    assert project.role == "Research Analyst"
    assert project.team_size_text == "6 people"
    assert project.domain == "Public Health"
    assert project.responsibilities == [
        "Designed the participant questionnaire."
    ]
    assert project.achievements == [
        "Increased completed responses by 25 percent."
    ]


def test_parses_unlabelled_field_inline_gpa_and_expected_graduation(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = EducationParser(
        settings,
        taxonomy,
    )

    text = """
    VNUHCM - University of Information Technology
    Information Technology GPA: 8.03 / 10
    Sep 2023 - Present Expected Graduation: 2027
    """

    result = parser.parse(
        {
            "EDUCATION": (text,),
        }
    )

    assert len(result.educations) == 1

    education = result.educations[0]

    assert education.institution_name == (
        "VNUHCM - University of Information Technology"
    )
    assert education.degree is None
    assert education.normalized_degree_level is None
    assert education.field_of_study == "Information Technology"
    assert education.grade == "8.03 / 10"
    assert education.start_date == "2023-09"
    assert education.end_date is None
    assert education.current is True
    assert education.description == (
        "Expected Graduation: 2027"
    )
    assert "EDUCATION_PARTIALLY_PARSED" not in result.warnings


def test_parses_vietnamese_institution_field_grade_and_expected_graduation(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = EducationParser(
        settings,
        taxonomy,
    )

    text = """
    Trường Đại học Công nghệ Thông tin
    Công nghệ thông tin ĐTB: 8,03 / 10
    09/2023 - Hiện tại Dự kiến tốt nghiệp: Tháng 6 năm 2027
    """

    result = parser.parse(
        {
            "EDUCATION": (text,),
        }
    )

    assert len(result.educations) == 1

    education = result.educations[0]

    assert education.institution_name == (
        "Trường Đại học Công nghệ Thông tin"
    )
    assert education.degree is None
    assert education.normalized_degree_level is None
    assert education.field_of_study == "Công nghệ thông tin"
    assert education.grade == "8,03 / 10"
    assert education.start_date == "2023-09"
    assert education.end_date is None
    assert education.current is True
    assert education.description == (
        "Dự kiến tốt nghiệp: Tháng 6 năm 2027"
    )
    assert "EDUCATION_PARTIALLY_PARSED" not in result.warnings


def test_parses_unaccented_vietnamese_education_labels(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = EducationParser(
        settings,
        taxonomy,
    )

    text = """
    Truong Dai hoc Bach khoa
    Nganh: Ky thuat phan mem
    Diem trung binh: 8.2/10
    09/2022 - Present Du kien tot nghiep: 06/2026
    """

    result = parser.parse(
        {
            "EDUCATION": (text,),
        }
    )

    assert len(result.educations) == 1

    education = result.educations[0]

    assert education.institution_name == (
        "Truong Dai hoc Bach khoa"
    )
    assert education.degree is None
    assert education.normalized_degree_level is None
    assert education.field_of_study == "Ky thuat phan mem"
    assert education.grade == "8.2/10"
    assert education.start_date == "2022-09"
    assert education.end_date is None
    assert education.current is True
    assert education.description == (
        "Du kien tot nghiep: 06/2026"
    )
    assert "EDUCATION_PARTIALLY_PARSED" not in result.warnings

def test_current_education_never_emits_end_date_for_expected_graduation(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = EducationParser(
        settings,
        taxonomy,
    )

    text = """
    Example University
    Major: Computer Science
    2024 - Present
    Expected Graduation: 2028
    """

    result = parser.parse(
        {
            "EDUCATION": (text,),
        }
    )

    assert len(result.educations) == 1

    education = result.educations[0]

    assert education.current is True
    assert education.end_date is None
    assert education.description == (
        "Expected Graduation: 2028"
    )


def test_does_not_treat_vietnamese_university_name_as_degree(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = EducationParser(
        settings,
        taxonomy,
    )

    text = """
    Trường Đại học Kinh tế
    Ngành: Kế toán
    2019 - 2023
    """

    result = parser.parse(
        {
            "EDUCATION": (text,),
        }
    )

    assert len(result.educations) == 1

    education = result.educations[0]

    assert education.institution_name == "Trường Đại học Kinh tế"
    assert education.degree is None
    assert education.normalized_degree_level is None
    assert education.field_of_study == "Kế toán"


def test_keeps_relevant_coursework_in_description_not_field_of_study(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = EducationParser(
        settings,
        taxonomy,
    )

    text = """
    University of Economics
    Relevant Coursework: Databases, Algorithms
    2018 - 2022
    """

    result = parser.parse(
        {
            "EDUCATION": (text,),
        }
    )

    assert len(result.educations) == 1

    education = result.educations[0]

    assert education.field_of_study is None
    assert education.description == (
        "Relevant Coursework: Databases, Algorithms"
    )
    assert "EDUCATION_PARTIALLY_PARSED" in result.warnings