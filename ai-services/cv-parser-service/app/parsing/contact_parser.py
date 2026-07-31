from __future__ import annotations

import re
from dataclasses import dataclass
from urllib.parse import urlsplit

from app.config import Settings
from app.normalization.text_normalizer import normalize_for_matching
from app.normalization.value_normalizer import (
    clean_optional_text,
    stable_unique,
    validate_http_url,
)
from app.schemas import ContactInformation, LinkEntry
from app.taxonomy.taxonomy_loader import TaxonomyBundle


EMAIL_PATTERN = re.compile(
    r"(?<![\w.+-])"
    r"[A-Z0-9.!#$%&'*+/=?^_`{|}~-]{1,64}"
    r"@"
    r"(?:[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?\.)+"
    r"[A-Z]{2,63}"
    r"(?![\w.-])",
    re.IGNORECASE,
)

PHONE_CANDIDATE_PATTERN = re.compile(
    r"(?<!\w)(?:\+?\d[\d\s().-]{7,24}\d)(?!\w)"
)

URL_PATTERN = re.compile(
    r"(?<![@\w])(?:https?://|www\.)[^\s<>\[\]{}\"']{4,2000}",
    re.IGNORECASE,
)

LABELLED_ADDRESS_PATTERN = re.compile(
    r"^(?:address|location|địa\s*chỉ|nơi\s*ở)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

POSTAL_CODE_PATTERN = re.compile(
    r"^(?:postal\s*code|zip\s*code|postcode|mã\s*bưu\s*chính)"
    r"\s*[:：-]\s*(?P<value>[A-Z0-9][A-Z0-9 -]{2,14})$",
    re.IGNORECASE,
)

COUNTRY_CANONICALS = {
    "vietnam",
    "singapore",
}

SOCIAL_HOSTS = {
    "facebook.com",
    "www.facebook.com",
    "instagram.com",
    "www.instagram.com",
    "x.com",
    "www.x.com",
    "twitter.com",
    "www.twitter.com",
    "youtube.com",
    "www.youtube.com",
}


@dataclass(frozen=True, slots=True)
class ContactParseResult:
    contact: ContactInformation
    links: tuple[LinkEntry, ...]


class ContactParser:
    def __init__(
            self,
            settings: Settings,
            taxonomy: TaxonomyBundle,
    ) -> None:
        self._settings = settings
        self._locations = taxonomy.locations

    def parse(
            self,
            raw_text: str,
            section_texts: dict[str, tuple[str, ...]],
    ) -> ContactParseResult:
        contact_scope = self._build_contact_scope(
            raw_text,
            section_texts,
        )

        email = self._extract_email(contact_scope)
        phone = self._extract_phone(contact_scope)
        address_text = self._extract_labelled_address(contact_scope)
        postal_code = self._extract_postal_code(contact_scope)
        city, province_or_state, country = self._extract_locations(
            contact_scope
        )
        links = self._extract_links(raw_text)

        return ContactParseResult(
            contact=ContactInformation(
                email=email,
                phone=phone,
                addressText=address_text,
                city=city,
                provinceOrState=province_or_state,
                country=country,
                postalCode=postal_code,
            ),
            links=tuple(links),
        )

    @staticmethod
    def _build_contact_scope(
            raw_text: str,
            section_texts: dict[str, tuple[str, ...]],
    ) -> str:
        values: list[str] = []
        values.extend(section_texts.get("HEADER", ()))
        values.extend(section_texts.get("CONTACT", ()))

        if not values:
            values.append(
                "\n".join(raw_text.splitlines()[:20])
            )

        return "\n".join(values)

    @staticmethod
    def _extract_email(
            text: str,
    ) -> str | None:
        match = EMAIL_PATTERN.search(text)

        if match is None:
            return None

        return match.group(0).casefold()[:320]

    @staticmethod
    def _extract_phone(
            text: str,
    ) -> str | None:
        for match in PHONE_CANDIDATE_PATTERN.finditer(text):
            candidate = match.group(0).strip()
            normalized = ContactParser._normalize_phone(candidate)

            if normalized is not None:
                return normalized

        return None

    @staticmethod
    def _normalize_phone(
            value: str,
    ) -> str | None:
        has_leading_plus = value.lstrip().startswith("+")
        digits = "".join(
            character
            for character in value
            if character.isdigit()
        )

        if not 9 <= len(digits) <= 15:
            return None

        if has_leading_plus:
            return f"+{digits}"

        if digits.startswith("84") and len(digits) in {11, 12}:
            return f"+{digits}"

        return digits

    @staticmethod
    def _extract_labelled_address(
            text: str,
    ) -> str | None:
        for line in text.splitlines():
            match = LABELLED_ADDRESS_PATTERN.match(
                line.strip()
            )

            if match is None:
                continue

            value = clean_optional_text(
                match.group("value"),
                maximum_length=500,
            )

            if (
                    value is not None
                    and not EMAIL_PATTERN.search(value)
            ):
                return value

        return None

    @staticmethod
    def _extract_postal_code(
            text: str,
    ) -> str | None:
        for line in text.splitlines():
            match = POSTAL_CODE_PATTERN.match(
                line.strip()
            )

            if match is not None:
                return clean_optional_text(
                    match.group("value"),
                    maximum_length=20,
                )

        return None

    def _extract_locations(
            self,
            text: str,
    ) -> tuple[str | None, str | None, str | None]:
        normalized_text = normalize_for_matching(text)
        matches: list[str] = []

        for item in self._locations:
            if any(
                    self._contains_phrase(
                        normalized_text,
                        alias,
                    )
                    for alias in item.aliases
            ):
                matches.append(item.canonical)

        matches = stable_unique(
            matches,
            maximum_items=5,
        )

        country = next(
            (
                value
                for value in matches
                if value.casefold() in COUNTRY_CANONICALS
            ),
            None,
        )

        place_matches = [
            value
            for value in matches
            if value.casefold() not in COUNTRY_CANONICALS
        ]

        city = (
            place_matches[0]
            if place_matches
            else None
        )

        province_or_state = (
            place_matches[1]
            if len(place_matches) > 1
            else None
        )

        return city, province_or_state, country

    @staticmethod
    def _contains_phrase(
            normalized_text: str,
            raw_phrase: str,
    ) -> bool:
        phrase = normalize_for_matching(raw_phrase)

        if not phrase:
            return False

        pattern = re.compile(
            rf"(?<![\w]){re.escape(phrase)}(?![\w])",
            re.UNICODE,
        )

        return pattern.search(normalized_text) is not None

    def _extract_links(
            self,
            raw_text: str,
    ) -> list[LinkEntry]:
        result: list[LinkEntry] = []
        seen: set[str] = set()

        for match in URL_PATTERN.finditer(raw_text):
            validated = validate_http_url(
                match.group(0)
            )

            if validated is None or validated in seen:
                continue

            seen.add(validated)

            result.append(
                LinkEntry(
                    type=self._classify_link(validated),
                    url=validated,
                    label=self._link_label(validated),
                )
            )

            if len(result) >= self._settings.max_links:
                break

        return result

    @staticmethod
    def _classify_link(
            url: str,
    ) -> str:
        parsed = urlsplit(url)
        hostname = (
                parsed.hostname or ""
        ).casefold()
        path = parsed.path.casefold()

        if hostname.endswith("linkedin.com"):
            return "LINKEDIN"

        if hostname.endswith("github.com"):
            return "GITHUB"

        if hostname.endswith("behance.net"):
            return "BEHANCE"

        if hostname.endswith("dribbble.com"):
            return "DRIBBBLE"

        if hostname.endswith("stackoverflow.com"):
            return "STACK_OVERFLOW"

        if hostname in SOCIAL_HOSTS:
            return "SOCIAL_PROFILE"

        if any(
                token in hostname or token in path
                for token in (
                        "portfolio",
                        "works",
                        "gallery",
                )
        ):
            return "PORTFOLIO"

        if any(
                token in hostname
                for token in (
                        "researchgate.net",
                        "orcid.org",
                        "scholar.google",
                )
        ):
            return "PUBLICATION"

        return "PERSONAL_WEBSITE"

    @staticmethod
    def _link_label(
            url: str,
    ) -> str | None:
        hostname = (
                urlsplit(url).hostname or ""
        ).casefold()

        labels = (
            ("linkedin.com", "LinkedIn"),
            ("github.com", "GitHub"),
            ("behance.net", "Behance"),
            ("dribbble.com", "Dribbble"),
            ("stackoverflow.com", "Stack Overflow"),
            ("researchgate.net", "ResearchGate"),
            ("orcid.org", "ORCID"),
        )

        for suffix, label in labels:
            if hostname.endswith(suffix):
                return label

        return None