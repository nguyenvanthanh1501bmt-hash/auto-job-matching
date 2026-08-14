from __future__ import annotations

from app.config import Settings
from app.parsing.contact_parser import ContactParser
from app.parsing.identity_parser import IdentityParser
from app.taxonomy.taxonomy_loader import TaxonomyBundle


def test_parses_vietnamese_name_headline_summary_and_objective(
        taxonomy: TaxonomyBundle,
) -> None:
    parser = IdentityParser(taxonomy)

    raw_text = """
    NGUYỄN THỊ MINH AN
    Kế toán trưởng
    minhan@example.com

    TÓM TẮT NGHỀ NGHIỆP
    Chuyên gia kế toán có kinh nghiệm về báo cáo tài chính,
    thuế và kiểm toán.

    MỤC TIÊU NGHỀ NGHIỆP
    Mong muốn làm việc tại doanh nghiệp sản xuất ổn định.
    """

    result = parser.parse(
        raw_text,
        {
            "HEADER": (
                "NGUYỄN THỊ MINH AN\n"
                "Kế toán trưởng\n"
                "minhan@example.com",
            ),
            "SUMMARY": (
                "Chuyên gia kế toán có kinh nghiệm về báo cáo "
                "tài chính, thuế và kiểm toán.",
            ),
            "OBJECTIVE": (
                "Mong muốn làm việc tại doanh nghiệp sản xuất ổn định.",
            ),
        },
    )

    assert result.full_name == "NGUYỄN THỊ MINH AN"
    assert result.headline == "Kế toán trưởng"

    assert result.professional_summary == (
        "Chuyên gia kế toán có kinh nghiệm về báo cáo "
        "tài chính, thuế và kiểm toán."
    )

    assert result.career_objective == (
        "Mong muốn làm việc tại doanh nghiệp sản xuất ổn định."
    )

    assert result.target_job_titles == (
        "Kế toán trưởng",
    )

    assert "FULL_NAME_NOT_DETECTED" not in result.warnings
    assert "HEADLINE_NOT_DETECTED" not in result.warnings


def test_parses_explicit_target_role_and_industries(
        taxonomy: TaxonomyBundle,
) -> None:
    parser = IdentityParser(taxonomy)

    header = """
    EMILY JOHNSON
    Registered Nurse
    Target Role: Registered Nurse
    Target Industries: Healthcare, Hospital Operations
    emily.johnson@example.com
    """

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
            "SUMMARY": (
                "Registered nurse experienced in patient care.",
            ),
            "OBJECTIVE": (
                "Seeking a clinical nursing position.",
            ),
        },
    )

    assert result.full_name == "EMILY JOHNSON"
    assert result.headline == "Registered Nurse"

    assert result.target_job_titles == (
        "Registered Nurse",
    )

    assert result.target_industries == (
        "Healthcare",
        "Hospital Operations",
    )


def test_does_not_invent_full_name_when_header_is_uncertain(
        taxonomy: TaxonomyBundle,
) -> None:
    parser = IdentityParser(taxonomy)

    header = """
    CURRICULUM VITAE
    candidate@example.com
    +84 901 234 567
    """

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert result.full_name is None
    assert "FULL_NAME_NOT_DETECTED" in result.warnings


def test_extracts_vietnamese_contact_and_location(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    header = """
    NGUYỄN THỊ MINH AN
    Email: MINHAN.Accounting@Example.COM
    Điện thoại: 0901 234 567
    Địa chỉ: 12 Nguyễn Huệ, Thành phố Hồ Chí Minh, Việt Nam
    Mã bưu chính: 700000
    """

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert (
            result.contact.email
            == "minhan.accounting@example.com"
    )

    assert result.contact.phone == "0901234567"

    assert result.contact.address_text == (
        "12 Nguyễn Huệ, Thành phố Hồ Chí Minh, Việt Nam"
    )

    assert result.contact.city == "Hồ Chí Minh"
    assert result.contact.country == "Vietnam"
    assert result.contact.postal_code == "700000"


def test_preserves_reasonable_international_phone(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    header = """
    JANE CARTER
    jane.carter@example.com
    Phone: +44 (7700) 900-123
    London
    """

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert result.contact.phone == "+447700900123"
    assert result.contact.city == "London"


def test_extracts_and_classifies_links_without_calling_them(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    raw_text = """
    ALEX MORGAN
    alex@example.com

    LinkedIn:
    https://www.linkedin.com/in/alex-morgan

    GitHub:
    https://github.com/alex-morgan

    Portfolio:
    https://portfolio.example.com/works

    Behance:
    https://www.behance.net/alexdesigner

    Invalid local link:
    http://localhost/private
    """

    result = parser.parse(
        raw_text,
        {
            "HEADER": (
                "ALEX MORGAN\nalex@example.com",
            ),
        },
    )

    links_by_type = {
        link.type: link.url
        for link in result.links
    }

    assert links_by_type["LINKEDIN"] == (
        "https://www.linkedin.com/in/alex-morgan"
    )

    assert links_by_type["GITHUB"] == (
        "https://github.com/alex-morgan"
    )

    assert links_by_type["PORTFOLIO"] == (
        "https://portfolio.example.com/works"
    )

    assert links_by_type["BEHANCE"] == (
        "https://www.behance.net/alexdesigner"
    )

    assert all(
        "localhost" not in link.url
        for link in result.links
    )


def test_does_not_invent_address_or_country_from_name(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    header = """
    NGUYEN VAN NAM
    nam.nguyen@example.com
    0901234567
    """

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert result.contact.address_text is None
    assert result.contact.city is None
    assert result.contact.province_or_state is None
    assert result.contact.country is None
    assert result.contact.postal_code is None


def test_does_not_use_generic_section_heading_as_headline(
        taxonomy: TaxonomyBundle,
) -> None:
    parser = IdentityParser(taxonomy)

    header = """
    NGUYEN VAN AN
    PROFILE
    candidate@example.com
    """

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
            "SUMMARY": (
                "Experienced professional in business operations.",
            ),
        },
    )

    assert result.full_name == "NGUYEN VAN AN"
    assert result.headline is None
    assert "HEADLINE_NOT_DETECTED" in result.warnings

def test_extracts_address_from_single_line_contact_fields(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    header = (
        "GitHub: https://github.com/example "
        "Phone: 0769480948 "
        "Email: candidate@example.com "
        "Address: Thu Duc, Ho Chi Minh City"
    )

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert result.contact.address_text == (
        "Thu Duc, Ho Chi Minh City"
    )
    assert result.contact.city == (
        "Hồ Chí Minh"
    )


def test_stops_inline_address_at_next_contact_label(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    header = (
        "Address: Da Nang, Vietnam "
        "Phone: 0901 234 567 "
        "Email: candidate@example.com"
    )

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert result.contact.address_text == (
        "Da Nang, Vietnam"
    )
    assert result.contact.phone == (
        "0901234567"
    )


def test_extracts_unaccented_vietnamese_address_label(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    header = (
        "NGUYEN VAN AN\n"
        "Dia chi lien he: Quan 1, TP.HCM\n"
        "Email: candidate@example.com"
    )

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert result.contact.address_text == (
        "Quan 1, TP.HCM"
    )
    assert result.contact.city == (
        "Hồ Chí Minh"
    )


def test_extracts_address_value_from_following_line(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    header = """
    JANE CARTER
    Mailing Address:
    221B Baker Street, London
    Phone: +44 (7700) 900-123
    """

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert result.contact.address_text == (
        "221B Baker Street, London"
    )
    assert result.contact.city == "London"


def test_does_not_treat_project_location_as_contact_address(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    header = """
    ALEX MORGAN
    Project Location: Remote
    alex@example.com
    """

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert result.contact.address_text is None


def test_does_not_use_preferred_location_as_contact_location(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    header = """
    ALEX MORGAN
    Preferred Location: Singapore
    alex@example.com
    """

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert result.contact.address_text is None
    assert result.contact.city is None
    assert (
            result.contact.province_or_state
            is None
    )
    assert result.contact.country is None


def test_prefers_labelled_address_for_location_normalization(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    header = (
        "Current Location: Da Nang, Vietnam "
        "Preferred Location: Singapore"
    )

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert result.contact.address_text == (
        "Da Nang, Vietnam"
    )
    assert result.contact.city == "Đà Nẵng"
    assert result.contact.country == "Vietnam"

def test_location_kind_metadata_classifies_region_without_hardcoded_name(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    header = "Address: Toronto, Ontario"

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert result.contact.city == "Toronto"
    assert (
            result.contact.province_or_state
            == "Ontario"
    )
    assert result.contact.country is None


def test_location_kind_metadata_classifies_country_without_code_change(
        settings: Settings,
        taxonomy: TaxonomyBundle,
) -> None:
    parser = ContactParser(
        settings,
        taxonomy,
    )

    header = "Address: Singapore"

    result = parser.parse(
        header,
        {
            "HEADER": (header,),
        },
    )

    assert result.contact.city is None
    assert (
            result.contact.province_or_state
            is None
    )
    assert result.contact.country == "Singapore"