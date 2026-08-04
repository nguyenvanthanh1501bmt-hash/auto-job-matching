from __future__ import annotations

import re
from collections.abc import Mapping
from dataclasses import dataclass
from datetime import date
from urllib.parse import urlsplit

from app.config import Settings
from app.normalization.date_normalizer import (
    DateRange,
    DateValue,
    extract_date_range,
    is_expired,
    normalize_date_value,
)
from app.normalization.text_normalizer import normalize_for_matching
from app.normalization.value_normalizer import (
    clean_optional_text,
    stable_unique,
    validate_http_url,
)
from app.parsing.skill_parser import SkillParser
from app.schemas import (
    Award,
    Certification,
    LanguageSkill,
    LicenseEntry,
    ProfessionalActivity,
    ProjectExperience,
    Publication,
    TrainingCourse,
    VolunteerExperience,
)
from app.taxonomy.taxonomy_loader import TaxonomyBundle


BULLET_PATTERN = re.compile(
    r"^\s*(?:[-*•●▪◦‣∙·]|\d{1,2}[.)])\s*(?P<value>.+)$"
)

LIST_SEPARATOR_PATTERN = re.compile(
    r"\s*(?:[,;|•●▪◦‣∙·]|\s+/\s+)\s*"
)

INLINE_SEPARATOR_PATTERN = re.compile(
    r"\s+(?:[-–—|])\s+"
)

ACHIEVEMENT_RESULT_VERB_PATTERN = re.compile(
    r"(?:"
    r"\b(?:achieved|completed|delivered|exceeded|generated|grew|"
    r"improved|increased|reduced|saved|optimized|decreased|cut|"
    r"boosted|accelerated|shortened)\b|"
    r"(?:đạt|hoàn\s+thành|hoan\s+thanh|bàn\s+giao|ban\s+giao|"
    r"vượt|tăng|tang|giảm|giam|tiết\s+kiệm|tiet\s+kiem|"
    r"cải\s+thiện|cai\s+thien|tối\s+ưu|toi\s+uu|"
    r"rút\s+ngắn|rut\s+ngan)"
    r")",
    re.IGNORECASE,
)

ACHIEVEMENT_MEASURABLE_RESULT_PATTERN = re.compile(
    r"(?:"
    r"\b\d+(?:[.,]\d+)?\s*(?:%|percent|percentage\s+points?|x|times?)\b|"
    r"\b(?:by|within|under)\s+\d+(?:[.,]\d+)?\s*"
    r"(?:ms|milliseconds?|seconds?|minutes?|hours?|days?|weeks?|months?|"
    r"users?|customers?|requests?|transactions?|records?|items?|features?|"
    r"screens?|modules?|vnd|usd|dollars?)\b|"
    r"\bfrom\s+\d+(?:[.,]\d+)?(?:\s*\w+)?\s+to\s+"
    r"\d+(?:[.,]\d+)?(?:\s*\w+)?\b|"
    r"\btop\s+\d+\b|"
    r"\b(?:first|second|third)\s+place\b|"
    r"\b(?:ahead\s+of\s+schedule|under\s+budget)\b|"
    r"\b\d+(?:[.,]\d+)?\s*(?:phần\s+trăm|phan\s+tram|"
    r"giây|giay|phút|phut|giờ|gio|ngày|ngay|tuần|tuan|tháng|thang|"
    r"người\s+dùng|nguoi\s+dung|khách\s+hàng|khach\s+hang|"
    r"yêu\s+cầu|yeu\s+cau|giao\s+dịch|giao\s+dich)\b|"
    r"\btừ\s+\d+(?:[.,]\d+)?(?:\s*\w+)?\s+"
    r"(?:xuống|lên|đến)\s+\d+(?:[.,]\d+)?(?:\s*\w+)?\b|"
    r"\btu\s+\d+(?:[.,]\d+)?(?:\s*\w+)?\s+"
    r"(?:xuong|len|den)\s+\d+(?:[.,]\d+)?(?:\s*\w+)?\b|"
    r"\b(?:hạng|hang)\s+(?:nhất|nhat|nhì|nhi|ba|\d+)\b|"
    r"\b(?:trước\s+tiến\s+độ|truoc\s+tien\s+do|"
    r"dưới\s+ngân\s+sách|duoi\s+ngan\s+sach)\b"
    r")",
    re.IGNORECASE,
)

ACHIEVEMENT_RECOGNITION_PATTERN = re.compile(
    r"(?:"
    r"\b(?:won|awarded|recognized|ranked|selected\s+as)\b|"
    r"\breceived\s+(?:an?\s+)?(?:award|prize|recognition)\b|"
    r"(?:đạt\s+giải|dat\s+giai|giành\s+giải|gianh\s+giai|"
    r"được\s+trao|duoc\s+trao|được\s+công\s+nhận|"
    r"duoc\s+cong\s+nhan|xếp\s+hạng|xep\s+hang)"
    r")",
    re.IGNORECASE,
)

URL_PATTERN = re.compile(
    r"(?<![@\w])"
    r"(?:https?://|www\.)"
    r"[^\s<>\[\]{}\"']{4,2000}",
    re.IGNORECASE,
)

SINGLE_DATE_PATTERN = re.compile(
    r"(?P<value>"
    r"(?:0?[1-9]|1[0-2])[/.-](?:19|20)\d{2}"
    r"|(?:19|20)\d{2}[/.-](?:0?[1-9]|1[0-2])"
    r"|(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|"
    r"Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|"
    r"Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\.?\s+(?:19|20)\d{2}"
    r"|Tháng\s+(?:0?[1-9]|1[0-2])\s*[/.-]\s*(?:19|20)\d{2}"
    r"|(?:19|20)\d{2}"
    r")",
    re.IGNORECASE,
)

DURATION_PATTERN = re.compile(
    r"\b(?P<value>\d{1,4}(?:[.,]\d+)?)\s*"
    r"(?P<unit>"
    r"hours?|hrs?|days?|weeks?|months?|"
    r"giờ|ngày|tuần|tháng"
    r")\b",
    re.IGNORECASE,
)

TEAM_SIZE_PATTERN = re.compile(
    r"^(?:"
    r"team\s+size"
    r"|team(?:\s+project)?"
    r"|group\s+project"
    r"|project\s+team"
    r"|nhóm"
    r"|đội"
    r"|dự\s+án\s+nhóm"
    r"|du\s+an\s+nhom"
    r"|quy\s+mô\s+nhóm"
    r"|quy\s+mo\s+nhom"
    r"|số\s+thành\s+viên"
    r"|so\s+thanh\s+vien"
    r")"
    r"\s*[:：-]?\s*"
    r"(?P<value>"
    r"\d{1,4}(?:\s*(?:"
    r"members?|people|persons?|người|thành\s+viên|thanh\s+vien"
    r"))?"
    r")\s*$",
    re.IGNORECASE,
)

SCORE_PATTERN = re.compile(
    r"\b(?P<framework>IELTS|TOEIC|TOEFL|JLPT|HSK|CEFR)\s*"
    r"(?P<score>"
    r"[A-C][12]|N[1-5]|\d{1,4}(?:[.,]\d+)?"
    r")\b",
    re.IGNORECASE,
)

GENERIC_SCORE_PATTERN = re.compile(
    r"\b(?P<score>"
    r"\d(?:[.,]\d)?\s*/\s*\d(?:[.,]\d)?"
    r"|\d{2,4}\s*/\s*\d{2,4}"
    r")\b"
)

CREDENTIAL_ID_PATTERN = re.compile(
    r"^(?:"
    r"credential\s*(?:id|number)"
    r"|certificate\s*(?:id|number)"
    r"|credential"
    r"|mã\s+chứng\s+chỉ"
    r"|số\s+chứng\s+chỉ"
    r")\s*[:：#-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

LICENSE_NUMBER_PATTERN = re.compile(
    r"^(?:"
    r"license\s*(?:number|no\.?|id)"
    r"|registration\s*(?:number|no\.?)"
    r"|permit\s*(?:number|no\.?)"
    r"|số\s+giấy\s+phép"
    r"|số\s+chứng\s+chỉ"
    r"|số\s+bằng\s+lái"
    r")\s*[:：#-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PREFERENCE_LOCATION_PATTERN = re.compile(
    r"^(?:"
    r"preferred\s+locations?"
    r"|desired\s+locations?"
    r"|work\s+locations?"
    r"|location\s+preference"
    r"|địa\s+điểm\s+mong\s+muốn"
    r"|nơi\s+làm\s+việc\s+mong\s+muốn"
    r"|khu\s+vực\s+mong\s+muốn"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PREFERENCE_WORK_MODE_PATTERN = re.compile(
    r"^(?:"
    r"preferred\s+work\s+mode"
    r"|work\s+mode\s+preference"
    r"|work\s+arrangement"
    r"|hình\s+thức\s+làm\s+việc\s+mong\s+muốn"
    r"|phương\s+thức\s+làm\s+việc"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PREFERENCE_EMPLOYMENT_PATTERN = re.compile(
    r"^(?:"
    r"preferred\s+employment\s+types?"
    r"|employment\s+preference"
    r"|job\s+types?"
    r"|employment\s+types?"
    r"|loại\s+hình\s+công\s+việc"
    r"|hình\s+thức\s+công\s+việc"
    r"|loại\s+hình\s+làm\s+việc"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

EXPECTED_SALARY_PATTERN = re.compile(
    r"^(?:"
    r"expected\s+salary"
    r"|desired\s+salary"
    r"|salary\s+expectation"
    r"|expected\s+compensation"
    r"|mức\s+lương\s+mong\s+muốn"
    r"|thu\s+nhập\s+mong\s+muốn"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

AVAILABILITY_PATTERN = re.compile(
    r"^(?:"
    r"availability"
    r"|available\s+from"
    r"|available\s+after"
    r"|notice\s+period"
    r"|start\s+date"
    r"|thời\s+gian\s+có\s+thể\s+bắt\s+đầu"
    r"|có\s+thể\s+bắt\s+đầu"
    r"|thời\s+gian\s+báo\s+trước"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PROJECT_NAME_PATTERN = re.compile(
    r"^(?:project|project\s+name|name|dự\s+án|tên\s+dự\s+án|"
    r"du\s+an|ten\s+du\s+an)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PROJECT_ROLE_PATTERN = re.compile(
    r"^(?:role|position|project\s+role|vai\s+trò|vị\s+trí|"
    r"vai\s+tro|vi\s+tri)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PROJECT_DOMAIN_PATTERN = re.compile(
    r"^(?:domain|industry|field|sector|lĩnh\s+vực|ngành|"
    r"linh\s+vuc|nganh)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PROJECT_DESCRIPTION_PATTERN = re.compile(
    r"^(?:description|overview|summary|mô\s+tả|tổng\s+quan|"
    r"mo\s+ta|tong\s+quan)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PROJECT_INLINE_ROLE_PATTERN = re.compile(
    r"^(?P<name>.+?)\s*\|\s*(?P<role>[^|]+)$"
)

PROJECT_ROLE_HINT_PATTERN = re.compile(
    r"(?:"
    r"developer|engineer|designer|analyst|architect|tester|"
    r"quality\s+assurance|qa|lead|leader|manager|member|intern|"
    r"consultant|administrator|coordinator|specialist|owner|"
    r"front[-\s]?end|back[-\s]?end|full[-\s]?stack|devops|"
    r"lập\s+trình\s+viên|lap\s+trinh\s+vien|kỹ\s+sư|ky\s+su|"
    r"thiết\s+kế|thiet\s+ke|phân\s+tích|phan\s+tich|"
    r"kiểm\s+thử|kiem\s+thu|trưởng\s+nhóm|truong\s+nhom|"
    r"quản\s+lý|quan\s+ly|thành\s+viên|thanh\s+vien|"
    r"thực\s+tập\s+sinh|thuc\s+tap\s+sinh"
    r")",
    re.IGNORECASE,
)

PROJECT_TECH_STACK_PATTERN = re.compile(
    r"^(?:"
    r"tech\s+stack|technology\s+stack|technologies?|"
    r"technical\s+stack|tools?|"
    r"công\s+nghệ(?:\s+sử\s+dụng)?|"
    r"cong\s+nghe(?:\s+su\s+dung)?|"
    r"công\s+cụ(?:\s+sử\s+dụng)?|"
    r"cong\s+cu(?:\s+su\s+dung)?"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PROJECT_LINK_LABEL_PATTERN = re.compile(
    r"^(?:"
    r"github|gitlab|bitbucket|repository|repo|source\s+code|"
    r"repository\s+url|project\s+url|demo|website|link|"
    r"mã\s+nguồn|ma\s+nguon|liên\s+kết|lien\s+ket"
    r")\s*[:：-]?\s*$",
    re.IGNORECASE,
)

PROJECT_TYPE_PATTERN = re.compile(
    r"^(?:"
    r"personal\s+project|individual\s+project|academic\s+project|"
    r"school\s+project|course\s+project|"
    r"dự\s+án\s+cá\s+nhân|du\s+an\s+ca\s+nhan|"
    r"đồ\s+án\s+cá\s+nhân|do\s+an\s+ca\s+nhan|"
    r"dự\s+án\s+học\s+tập|du\s+an\s+hoc\s+tap|"
    r"đồ\s+án\s+môn\s+học|do\s+an\s+mon\s+hoc"
    r")\s*$",
    re.IGNORECASE,
)

CERT_NAME_PATTERN = re.compile(
    r"^(?:"
    r"certification|certificate|name|chứng\s+chỉ|chứng\s+nhận|tên"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

CERT_ISSUER_PATTERN = re.compile(
    r"^(?:"
    r"issuer|issued\s+by|provider|organization|organisation|"
    r"đơn\s+vị\s+cấp|tổ\s+chức\s+cấp"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

CERTIFICATION_NAME_HINT_PATTERN = re.compile(
    r"(?:"
    r"\b(?:certificate|certification|certified|credential|diploma|badge)\b|"
    r"(?:chứng\s+chỉ|chứng\s+nhận|chung\s+chi|chung\s+nhan)"
    r")",
    re.IGNORECASE,
)

CERTIFICATION_EXAM_PATTERN = re.compile(
    r"\b(?:"
    r"TOEIC|IELTS|TOEFL|JLPT|TOPIK|HSK|CEFR|PTE"
    r")\b",
    re.IGNORECASE,
)

CERTIFICATION_ISSUER_ORGANIZATION_PATTERN = re.compile(
    r"(?:"
    r"\b(?:services?|learning|training|education|technologies?|"
    r"solutions?|foundation|council|board|authority|group|"
    r"international|global|inc|ltd|llc|jsc)\b|"
    r"(?:đào\s+tạo|dao\s+tao|giáo\s+dục|giao\s+duc|"
    r"dịch\s+vụ|dich\s+vu|hội\s+đồng|hoi\s+dong|"
    r"tập\s+đoàn|tap\s+doan|trung\s+tâm|trung\s+tam)"
    r")",
    re.IGNORECASE,
)

CERTIFICATION_INLINE_SEPARATOR_PATTERN = re.compile(
    r"\s+(?:[-–—|])\s+"
)

TRAILING_CERTIFICATION_DATE_PATTERN = re.compile(
    r"(?P<value>"
    r"(?:0?[1-9]|1[0-2])[/.-](?:19|20)\d{2}"
    r"|(?:19|20)\d{2}[/.-](?:0?[1-9]|1[0-2])"
    r"|(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|"
    r"Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|"
    r"Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\.?\s+(?:19|20)\d{2}"
    r"|Tháng\s+(?:0?[1-9]|1[0-2])\s*(?:[/.-]|năm)\s*(?:19|20)\d{2}"
    r"|Thang\s+(?:0?[1-9]|1[0-2])\s*(?:[/.-]|nam)\s*(?:19|20)\d{2}"
    r")\s*$",
    re.IGNORECASE,
)

ISSUED_DATE_PATTERN = re.compile(
    r"^(?:"
    r"issued|issued\s+date|date\s+issued|cấp\s+ngày|ngày\s+cấp"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

EXPIRATION_DATE_PATTERN = re.compile(
    r"^(?:"
    r"expires?|expiration\s+date|valid\s+until|expiry|"
    r"hết\s+hạn|ngày\s+hết\s+hạn"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

LICENSE_NAME_PATTERN = re.compile(
    r"^(?:"
    r"license|permit|registration|name|giấy\s+phép|bằng\s+lái|"
    r"chứng\s+chỉ\s+hành\s+nghề|tên"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

LICENSE_AUTHORITY_PATTERN = re.compile(
    r"^(?:"
    r"issuing\s+authority|issued\s+by|authority|agency|"
    r"đơn\s+vị\s+cấp|cơ\s+quan\s+cấp"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

LICENSE_JURISDICTION_PATTERN = re.compile(
    r"^(?:"
    r"jurisdiction|state|province|country|phạm\s+vi|tỉnh|quốc\s+gia"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

TRAINING_NAME_PATTERN = re.compile(
    r"^(?:"
    r"course|training|course\s+name|name|khóa\s+học|đào\s+tạo|tên"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

TRAINING_PROVIDER_PATTERN = re.compile(
    r"^(?:"
    r"provider|training\s+provider|organization|organisation|institution|"
    r"đơn\s+vị\s+đào\s+tạo|cơ\s+sở\s+đào\s+tạo"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

TRAINING_DATE_PATTERN = re.compile(
    r"^(?:"
    r"completion\s+date|completed|date|hoàn\s+thành|ngày\s+hoàn\s+thành"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

TRAINING_DURATION_PATTERN = re.compile(
    r"^(?:duration|length|thời\s+lượng)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

AWARD_NAME_PATTERN = re.compile(
    r"^(?:"
    r"award|honou?r|recognition|name|giải\s+thưởng|"
    r"thành\s+tích|danh\s+hiệu|tên"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

AWARD_ISSUER_PATTERN = re.compile(
    r"^(?:"
    r"issuer|awarded\s+by|organization|organisation|"
    r"đơn\s+vị\s+trao|tổ\s+chức"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

AWARD_DATE_PATTERN = re.compile(
    r"^(?:awarded\s+date|date|year|ngày|năm)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PUBLICATION_TITLE_PATTERN = re.compile(
    r"^(?:"
    r"title|publication|paper|article|tên|tiêu\s+đề|bài\s+báo"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PUBLICATION_AUTHOR_PATTERN = re.compile(
    r"^(?:authors?|by|tác\s+giả)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PUBLICATION_PUBLISHER_PATTERN = re.compile(
    r"^(?:"
    r"publisher|journal|conference|published\s+in|"
    r"nhà\s+xuất\s+bản|tạp\s+chí|hội\s+nghị"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

PUBLICATION_DATE_PATTERN = re.compile(
    r"^(?:"
    r"published\s+date|date|year|ngày\s+công\s+bố|năm"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

VOLUNTEER_ORGANIZATION_PATTERN = re.compile(
    r"^(?:"
    r"organization|organisation|nonprofit|charity|đơn\s+vị|tổ\s+chức"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

VOLUNTEER_ROLE_PATTERN = re.compile(
    r"^(?:role|position|vai\s+trò|vị\s+trí)"
    r"\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

ACTIVITY_ORGANIZATION_PATTERN = re.compile(
    r"^(?:"
    r"organization|organisation|association|club|đơn\s+vị|"
    r"tổ\s+chức|câu\s+lạc\s+bộ|hiệp\s+hội"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

ACTIVITY_ROLE_PATTERN = re.compile(
    r"^(?:"
    r"role|position|title|vai\s+trò|vị\s+trí|chức\s+vụ"
    r")\s*[:：-]\s*(?P<value>.+)$",
    re.IGNORECASE,
)

MAX_DESCRIPTION_LENGTH = 5_000
MAX_ENTRY_LINES = 100
MAX_RESPONSIBILITIES = 50
MAX_ACHIEVEMENTS = 50
MAX_LANGUAGES = 30
MAX_AWARDS = 30
MAX_PUBLICATIONS = 30
MAX_VOLUNTEER_EXPERIENCES = 30
MAX_ACTIVITIES = 30
MAX_INTERESTS = 30

LANGUAGE_ALIASES: tuple[
    tuple[str, tuple[str, ...]],
    ...,
] = (
    (
        "English",
        (
            "english",
            "tiếng anh",
            "anh ngữ",
        ),
    ),
    (
        "Vietnamese",
        (
            "vietnamese",
            "tiếng việt",
            "việt ngữ",
        ),
    ),
    (
        "Japanese",
        (
            "japanese",
            "tiếng nhật",
            "nhật ngữ",
        ),
    ),
    (
        "Chinese",
        (
            "chinese",
            "mandarin",
            "tiếng trung",
            "tiếng hoa",
        ),
    ),
    (
        "Korean",
        (
            "korean",
            "tiếng hàn",
            "hàn ngữ",
        ),
    ),
    (
        "French",
        (
            "french",
            "tiếng pháp",
            "pháp ngữ",
        ),
    ),
    (
        "German",
        (
            "german",
            "tiếng đức",
            "đức ngữ",
        ),
    ),
    (
        "Spanish",
        (
            "spanish",
            "tiếng tây ban nha",
        ),
    ),
    (
        "Italian",
        (
            "italian",
            "tiếng ý",
        ),
    ),
    (
        "Russian",
        (
            "russian",
            "tiếng nga",
        ),
    ),
    (
        "Thai",
        (
            "thai",
            "tiếng thái",
        ),
    ),
    (
        "Indonesian",
        (
            "indonesian",
            "bahasa indonesia",
            "tiếng indonesia",
        ),
    ),
    (
        "Malay",
        (
            "malay",
            "bahasa melayu",
            "tiếng malaysia",
        ),
    ),
    (
        "Arabic",
        (
            "arabic",
            "tiếng ả rập",
        ),
    ),
    (
        "Portuguese",
        (
            "portuguese",
            "tiếng bồ đào nha",
        ),
    ),
)


@dataclass(frozen=True, slots=True)
class CareerPreferenceParseResult:
    preferred_locations: tuple[str, ...]
    preferred_work_modes: tuple[str, ...]
    preferred_employment_types: tuple[str, ...]
    expected_salary_text: str | None
    availability_text: str | None
    warnings: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class SecondarySectionParseResult:
    preferred_locations: tuple[str, ...]
    preferred_work_modes: tuple[str, ...]
    preferred_employment_types: tuple[str, ...]
    expected_salary_text: str | None
    availability_text: str | None

    projects: tuple[ProjectExperience, ...]
    certifications: tuple[Certification, ...]
    licenses: tuple[LicenseEntry, ...]
    training_courses: tuple[TrainingCourse, ...]
    languages: tuple[LanguageSkill, ...]
    awards: tuple[Award, ...]
    publications: tuple[Publication, ...]
    volunteer_experiences: tuple[VolunteerExperience, ...]
    activities: tuple[ProfessionalActivity, ...]
    interests: tuple[str, ...]

    warnings: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class _TextBlock:
    lines: tuple[str, ...]
    date_range: DateRange | None


class CareerPreferenceParser:
    def __init__(
            self,
            taxonomy: TaxonomyBundle,
    ) -> None:
        self._locations = taxonomy.locations
        self._employment_types = (
            taxonomy.preferences.employment_types
        )
        self._work_modes = (
            taxonomy.preferences.work_modes
        )

    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
    ) -> CareerPreferenceParseResult:
        scopes = (
            *section_texts.get("HEADER", ()),
            *section_texts.get("CONTACT", ()),
            *section_texts.get("OBJECTIVE", ()),
        )

        preferred_locations: list[str] = []
        preferred_work_modes: list[str] = []
        preferred_employment_types: list[str] = []

        expected_salary_text: str | None = None
        availability_text: str | None = None

        for scope in scopes:
            for raw_line in scope.splitlines():
                line = raw_line.strip()

                if not line:
                    continue

                match = PREFERENCE_LOCATION_PATTERN.match(
                    line
                )

                if match is not None:
                    preferred_locations.extend(
                        self._match_locations(
                            match.group("value")
                        )
                    )
                    continue

                match = PREFERENCE_WORK_MODE_PATTERN.match(
                    line
                )

                if match is not None:
                    preferred_work_modes.extend(
                        self._match_named_aliases(
                            match.group("value"),
                            self._work_modes,
                        )
                    )
                    continue

                match = PREFERENCE_EMPLOYMENT_PATTERN.match(
                    line
                )

                if match is not None:
                    preferred_employment_types.extend(
                        self._match_named_aliases(
                            match.group("value"),
                            self._employment_types,
                        )
                    )
                    continue

                match = EXPECTED_SALARY_PATTERN.match(
                    line
                )

                if (
                        match is not None
                        and expected_salary_text is None
                ):
                    expected_salary_text = (
                        clean_optional_text(
                            match.group("value"),
                            maximum_length=500,
                        )
                    )
                    continue

                match = AVAILABILITY_PATTERN.match(
                    line
                )

                if (
                        match is not None
                        and availability_text is None
                ):
                    availability_text = (
                        clean_optional_text(
                            match.group("value"),
                            maximum_length=500,
                        )
                    )

        preferred_locations = stable_unique(
            preferred_locations,
            maximum_items=20,
        )

        warnings: list[str] = []

        if not preferred_locations:
            warnings.append(
                "PREFERRED_LOCATION_NOT_DETECTED"
            )

        return CareerPreferenceParseResult(
            preferred_locations=tuple(
                preferred_locations
            ),
            preferred_work_modes=tuple(
                stable_unique(
                    preferred_work_modes,
                    maximum_items=4,
                )
            ),
            preferred_employment_types=tuple(
                stable_unique(
                    preferred_employment_types,
                    maximum_items=9,
                )
            ),
            expected_salary_text=(
                expected_salary_text
            ),
            availability_text=availability_text,
            warnings=tuple(warnings),
        )

    def _match_locations(
            self,
            value: str,
    ) -> list[str]:
        normalized = normalize_for_matching(
            value
        )

        result: list[str] = []

        for item in self._locations:
            for alias in sorted(
                    item.aliases,
                    key=len,
                    reverse=True,
            ):
                if _contains_phrase(
                        normalized,
                        alias,
                ):
                    result.append(
                        item.canonical
                    )
                    break

        return result

    @staticmethod
    def _match_named_aliases(
            value: str,
            mapping: Mapping[
                str,
                tuple[str, ...],
            ],
    ) -> list[str]:
        normalized = normalize_for_matching(
            value
        )

        result: list[str] = []

        for canonical, aliases in mapping.items():
            if any(
                    _contains_phrase(
                        normalized,
                        alias,
                    )
                    for alias in aliases
            ):
                result.append(canonical)

        return result


class ProjectParser:
    def __init__(
            self,
            settings: Settings,
            skill_parser: SkillParser,
    ) -> None:
        self._settings = settings
        self._skill_parser = skill_parser

    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
    ) -> tuple[ProjectExperience, ...]:
        result: list[ProjectExperience] = []

        for section in section_texts.get(
                "PROJECTS",
                (),
        ):
            for block in _split_blocks(section):
                project = self._parse_block(
                    block
                )

                if project is not None:
                    result.append(project)

                if (
                        len(result)
                        >= self._settings.max_projects
                ):
                    return tuple(result)

        return tuple(result)

    def _parse_block(
            self,
            block: _TextBlock,
    ) -> ProjectExperience | None:
        name: str | None = None
        role: str | None = None
        domain: str | None = None

        description_lines: list[str] = []
        bullet_items: list[str] = []
        responsibilities: list[str] = []
        achievements: list[str] = []

        team_size_text: str | None = None
        project_url: str | None = None
        repository_url: str | None = None

        cleaned_lines: list[str] = []

        for raw_line in block.lines:
            line = _remove_date_range(
                raw_line
            )

            if not line:
                continue

            cleaned_lines.append(line)

            bullet = BULLET_PATTERN.match(
                line
            )

            if bullet is not None:
                value = clean_optional_text(
                    bullet.group("value"),
                    maximum_length=1_000,
                )

                if value is None:
                    continue

                bullet_items.append(value)
                continue

            match = PROJECT_NAME_PATTERN.match(
                line
            )

            if match is not None:
                parsed_name, parsed_role = (
                    _split_project_name_and_role(
                        match.group("value")
                    )
                )

                name = name or parsed_name
                role = role or parsed_role
                continue

            match = PROJECT_ROLE_PATTERN.match(
                line
            )

            if match is not None:
                role = (
                        role
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = PROJECT_DOMAIN_PATTERN.match(
                line
            )

            if match is not None:
                domain = (
                        domain
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = (
                PROJECT_DESCRIPTION_PATTERN.match(
                    line
                )
            )

            if match is not None:
                value = _clean(
                    match.group("value"),
                    1_000,
                )

                if value is not None:
                    description_lines.append(
                        value
                    )

                continue

            team_match = TEAM_SIZE_PATTERN.match(
                line
            )

            if team_match is not None:
                if team_size_text is None:
                    team_size_text = _clean(
                        team_match.group("value"),
                        100,
                    )

                continue

            if PROJECT_TYPE_PATTERN.match(line):
                continue

            if PROJECT_TECH_STACK_PATTERN.match(
                    line
            ):
                continue

            urls = _extract_urls(line)

            for url in urls:
                hostname = (
                        urlsplit(url).hostname
                        or ""
                ).casefold()

                if hostname.endswith(
                        (
                                "github.com",
                                "gitlab.com",
                                "bitbucket.org",
                        )
                ):
                    repository_url = (
                            repository_url
                            or url
                    )
                else:
                    project_url = (
                            project_url
                            or url
                    )

            without_urls = _strip_urls(line)

            if (
                    urls
                    and PROJECT_LINK_LABEL_PATTERN.match(
                without_urls
            )
            ):
                continue

            if PROJECT_LINK_LABEL_PATTERN.match(line):
                continue

            if (
                    bullet_items
                    and _looks_like_wrapped_bullet_continuation(
                bullet_items[-1],
                line,
            )
            ):
                previous = bullet_items[-1]
                separator = (
                    ""
                    if previous.endswith("-")
                    else " "
                )
                merged = clean_optional_text(
                    f"{previous}{separator}{line}",
                    maximum_length=1_000,
                )

                if merged is not None:
                    bullet_items[-1] = merged

                continue

            if (
                    name is None
                    and _looks_like_title(line)
            ):
                parsed_name, parsed_role = (
                    _split_project_name_and_role(
                        without_urls
                    )
                )
                name = parsed_name
                role = role or parsed_role
                continue

            if without_urls:
                description_lines.append(
                    without_urls
                )

        for value in bullet_items:
            if _is_project_achievement(value):
                achievements.append(value)
            else:
                responsibilities.append(value)

        scoped_text = "\n".join(
            cleaned_lines
        )

        (
            skills,
            tools,
            equipment,
        ) = self._skill_parser.extract_grouped_names(
            scoped_text
        )

        has_useful_data = any(
            value is not None
            for value in (
                name,
                role,
                domain,
                block.date_range,
                project_url,
                repository_url,
            )
        ) or bool(
            responsibilities
            or achievements
            or description_lines
        )

        if not has_useful_data:
            return None

        return ProjectExperience(
            name=name,
            role=role,
            domain=domain,
            start_date=(
                block.date_range.start.value
                if block.date_range is not None
                else None
            ),
            end_date=(
                block.date_range.end.value
                if block.date_range is not None
                else None
            ),
            current=(
                block.date_range.end.current
                if block.date_range is not None
                else None
            ),
            description=_join_description(
                description_lines
            ),
            responsibilities=stable_unique(
                responsibilities,
                maximum_items=(
                    MAX_RESPONSIBILITIES
                ),
            ),
            achievements=stable_unique(
                achievements,
                maximum_items=MAX_ACHIEVEMENTS,
            ),
            skills=stable_unique(
                skills,
                maximum_items=100,
            ),
            tools=stable_unique(
                tools,
                maximum_items=100,
            ),
            equipment=stable_unique(
                equipment,
                maximum_items=100,
            ),
            team_size_text=team_size_text,
            project_url=project_url,
            repository_url=repository_url,
        )


class CertificationParser:
    def __init__(
            self,
            settings: Settings,
            skill_parser: SkillParser,
    ) -> None:
        self._settings = settings
        self._skill_parser = skill_parser

    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
            *,
            today: date | None = None,
    ) -> tuple[
        tuple[Certification, ...],
        tuple[str, ...],
    ]:
        result: list[Certification] = []
        warnings: list[str] = []

        for section in section_texts.get(
                "CERTIFICATIONS",
                (),
        ):
            for block in _split_list_entries(
                    section
            ):
                certification, partial = (
                    self._parse_block(
                        block,
                        today=today,
                    )
                )

                if certification is not None:
                    result.append(certification)

                if partial:
                    warnings.append(
                        "CERTIFICATION_PARTIALLY_PARSED"
                    )

                if (
                        len(result)
                        >= self._settings.max_certifications
                ):
                    return (
                        tuple(result),
                        tuple(
                            dict.fromkeys(
                                warnings
                            )
                        ),
                    )

        return (
            tuple(result),
            tuple(
                dict.fromkeys(warnings)
            ),
        )

    def _parse_block(
            self,
            block: _TextBlock,
            *,
            today: date | None,
    ) -> tuple[
        Certification | None,
        bool,
    ]:
        name: str | None = None
        issuer: str | None = None

        issued_date: str | None = None
        expiration_date: str | None = None
        expiration_value: DateValue | None = None

        credential_id: str | None = None
        credential_url: str | None = None

        raw_values: list[str] = []

        for raw_line in block.lines:
            line = _remove_date_range(
                raw_line
            )

            bullet = BULLET_PATTERN.match(
                line
            )

            if bullet is not None:
                line = bullet.group(
                    "value"
                ).strip()

            if not line:
                continue

            match = CERT_NAME_PATTERN.match(
                line
            )

            if match is not None:
                candidate_name, inline_date = (
                    _extract_trailing_certification_date(
                        match.group("value")
                    )
                )

                if candidate_name:
                    name = (
                            name
                            or _clean(
                        candidate_name,
                        500,
                    )
                    )
                    raw_values.append(candidate_name)

                if (
                        issued_date is None
                        and inline_date is not None
                ):
                    issued_date = inline_date.value

                continue

            match = CERT_ISSUER_PATTERN.match(
                line
            )

            if match is not None:
                issuer = (
                        issuer
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                raw_values.append(line)
                continue

            match = ISSUED_DATE_PATTERN.match(
                line
            )

            if match is not None:
                value = _first_certification_date(
                    match.group("value")
                )

                if value.value is not None:
                    issued_date = (
                            issued_date
                            or value.value
                    )
                    continue

            match = (
                EXPIRATION_DATE_PATTERN.match(
                    line
                )
            )

            if match is not None:
                value = _first_certification_date(
                    match.group("value")
                )

                if value.value is not None:
                    expiration_value = (
                            expiration_value
                            or value
                    )

                    expiration_date = (
                            expiration_date
                            or value.value
                    )
                    continue

            match = (
                CREDENTIAL_ID_PATTERN.match(
                    line
                )
            )

            if match is not None:
                credential_id = (
                        credential_id
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            urls = _extract_urls(line)

            if (
                    urls
                    and credential_url is None
            ):
                credential_url = urls[0]
                line = _strip_urls(line)

            line, inline_date = (
                _extract_trailing_certification_date(
                    line
                )
            )

            if (
                    issued_date is None
                    and inline_date is not None
            ):
                issued_date = inline_date.value

            if not line:
                continue

            raw_values.append(line)

            if name is None:
                (
                    inline_name,
                    inline_issuer,
                ) = _split_certification_name_and_issuer(
                    line
                )

                if inline_issuer is not None:
                    name = inline_name
                    issuer = issuer or inline_issuer
                    continue

            if (
                    name is None
                    and _looks_like_title(line)
            ):
                name = _clean(
                    line,
                    500,
                )
            elif (
                    issuer is None
                    and _looks_like_provider(line)
            ):
                issuer = _clean(
                    line,
                    500,
                )

        if block.date_range is not None:
            issued_date = (
                    issued_date
                    or block.date_range.start.value
            )

            expiration_date = (
                    expiration_date
                    or block.date_range.end.value
            )

            expiration_value = (
                    expiration_value
                    or block.date_range.end
            )
        elif issued_date is None:
            dates: list[DateValue] = []

            for raw_value in raw_values:
                candidate_date = (
                    _first_certification_date(
                        raw_value.strip(
                            " -–—|,;()"
                        )
                    )
                )

                if (
                        candidate_date.value is None
                        or any(
                    existing.value
                    == candidate_date.value
                    for existing in dates
                )
                ):
                    continue

                dates.append(candidate_date)

            if dates:
                issued_date = dates[0].value

            if len(dates) >= 2:
                expiration_value = dates[1]
                expiration_date = (
                    dates[1].value
                )

        if name is None:
            return None, bool(raw_values)

        related_skills = (
            self._skill_parser.extract_names(
                "\n".join(raw_values),
                maximum_items=30,
            )
        )

        return (
            Certification(
                name=name,
                issuer=issuer,
                issued_date=issued_date,
                expiration_date=expiration_date,
                expired=(
                    is_expired(
                        expiration_value,
                        today=today,
                    )
                    if expiration_value is not None
                    else None
                ),
                credential_id=credential_id,
                credential_url=credential_url,
                related_skills=related_skills,
            ),
            (
                    issuer is None
                    and issued_date is None
            ),
        )


class LicenseParser:
    def __init__(
            self,
            settings: Settings,
    ) -> None:
        self._settings = settings

    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
            *,
            today: date | None = None,
    ) -> tuple[
        tuple[LicenseEntry, ...],
        tuple[str, ...],
    ]:
        result: list[LicenseEntry] = []
        warnings: list[str] = []

        for section in section_texts.get(
                "LICENSES",
                (),
        ):
            for block in _split_list_entries(
                    section
            ):
                license_entry, partial = (
                    self._parse_block(
                        block,
                        today=today,
                    )
                )

                if license_entry is not None:
                    result.append(
                        license_entry
                    )

                if partial:
                    warnings.append(
                        "LICENSE_PARTIALLY_PARSED"
                    )

                if (
                        len(result)
                        >= self._settings.max_licenses
                ):
                    return (
                        tuple(result),
                        tuple(
                            dict.fromkeys(
                                warnings
                            )
                        ),
                    )

        return (
            tuple(result),
            tuple(
                dict.fromkeys(warnings)
            ),
        )

    @staticmethod
    def _parse_block(
            block: _TextBlock,
            *,
            today: date | None,
    ) -> tuple[
        LicenseEntry | None,
        bool,
    ]:
        name: str | None = None
        authority: str | None = None
        license_number: str | None = None
        jurisdiction: str | None = None

        issued_date: str | None = None
        expiration_date: str | None = None
        expiration_value: DateValue | None = None

        raw_values: list[str] = []

        for raw_line in block.lines:
            line = _remove_date_range(
                raw_line
            )

            bullet = BULLET_PATTERN.match(
                line
            )

            if bullet is not None:
                line = bullet.group(
                    "value"
                ).strip()

            if not line:
                continue

            raw_values.append(line)

            match = LICENSE_NAME_PATTERN.match(
                line
            )

            if match is not None:
                name = (
                        name
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = (
                LICENSE_AUTHORITY_PATTERN.match(
                    line
                )
            )

            if match is not None:
                authority = (
                        authority
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = (
                LICENSE_JURISDICTION_PATTERN.match(
                    line
                )
            )

            if match is not None:
                jurisdiction = (
                        jurisdiction
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = (
                LICENSE_NUMBER_PATTERN.match(
                    line
                )
            )

            if match is not None:
                license_number = (
                        license_number
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = ISSUED_DATE_PATTERN.match(
                line
            )

            if match is not None:
                value = _first_date(
                    match.group("value")
                )

                issued_date = (
                        issued_date
                        or value.value
                )
                continue

            match = (
                EXPIRATION_DATE_PATTERN.match(
                    line
                )
            )

            if match is not None:
                value = _first_date(
                    match.group("value")
                )

                if value.value is not None:
                    expiration_value = (
                            expiration_value
                            or value
                    )

                    expiration_date = (
                            expiration_date
                            or value.value
                    )

                continue

            if (
                    name is None
                    and _looks_like_title(line)
            ):
                name = _clean(
                    line,
                    500,
                )
            elif (
                    authority is None
                    and _looks_like_provider(line)
            ):
                authority = _clean(
                    line,
                    500,
                )

        if block.date_range is not None:
            issued_date = (
                    issued_date
                    or block.date_range.start.value
            )

            expiration_date = (
                    expiration_date
                    or block.date_range.end.value
            )

            expiration_value = (
                    expiration_value
                    or block.date_range.end
            )
        elif issued_date is None:
            dates = _all_dates(
                "\n".join(raw_values)
            )

            if dates:
                issued_date = dates[0].value

            if len(dates) >= 2:
                expiration_value = dates[1]
                expiration_date = (
                    dates[1].value
                )

        if name is None:
            return None, bool(raw_values)

        return (
            LicenseEntry(
                name=name,
                issuing_authority=authority,
                license_number=license_number,
                issued_date=issued_date,
                expiration_date=expiration_date,
                expired=(
                    is_expired(
                        expiration_value,
                        today=today,
                    )
                    if expiration_value is not None
                    else None
                ),
                jurisdiction=jurisdiction,
            ),
            (
                    authority is None
                    and issued_date is None
            ),
        )


class TrainingParser:
    def __init__(
            self,
            settings: Settings,
            skill_parser: SkillParser,
    ) -> None:
        self._settings = settings
        self._skill_parser = skill_parser

    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
    ) -> tuple[TrainingCourse, ...]:
        result: list[TrainingCourse] = []

        for section in section_texts.get(
                "TRAINING",
                (),
        ):
            for block in _split_list_entries(
                    section
            ):
                course = self._parse_block(
                    block
                )

                if course is not None:
                    result.append(course)

                if (
                        len(result)
                        >= self._settings.max_training_courses
                ):
                    return tuple(result)

        return tuple(result)

    def _parse_block(
            self,
            block: _TextBlock,
    ) -> TrainingCourse | None:
        name: str | None = None
        provider: str | None = None
        completion_date: str | None = None
        duration_text: str | None = None

        description_lines: list[str] = []
        raw_values: list[str] = []

        for raw_line in block.lines:
            line = _remove_date_range(
                raw_line
            )

            bullet = BULLET_PATTERN.match(
                line
            )

            if bullet is not None:
                line = bullet.group(
                    "value"
                ).strip()

            if not line:
                continue

            raw_values.append(line)

            match = TRAINING_NAME_PATTERN.match(
                line
            )

            if match is not None:
                name = (
                        name
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = (
                TRAINING_PROVIDER_PATTERN.match(
                    line
                )
            )

            if match is not None:
                provider = (
                        provider
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = TRAINING_DATE_PATTERN.match(
                line
            )

            if match is not None:
                value = _first_date(
                    match.group("value")
                )

                completion_date = (
                        completion_date
                        or value.value
                )
                continue

            match = (
                TRAINING_DURATION_PATTERN.match(
                    line
                )
            )

            if match is not None:
                duration_text = (
                        duration_text
                        or _clean(
                    match.group("value"),
                    200,
                )
                )
                continue

            duration_match = DURATION_PATTERN.search(
                line
            )

            if (
                    duration_match is not None
                    and duration_text is None
            ):
                duration_text = _clean(
                    duration_match.group(0),
                    200,
                )

            if (
                    name is None
                    and _looks_like_title(line)
            ):
                name = _clean(
                    line,
                    500,
                )
            elif (
                    provider is None
                    and _looks_like_provider(line)
            ):
                provider = _clean(
                    line,
                    500,
                )
            else:
                description_lines.append(
                    line
                )

        if block.date_range is not None:
            completion_date = (
                    completion_date
                    or block.date_range.end.value
            )
        elif completion_date is None:
            dates = _all_dates(
                "\n".join(raw_values)
            )

            if dates:
                completion_date = (
                    dates[-1].value
                )

        if name is None:
            return None

        return TrainingCourse(
            name=name,
            provider=provider,
            completion_date=completion_date,
            duration_text=duration_text,
            description=_join_description(
                description_lines
            ),
            related_skills=(
                self._skill_parser.extract_names(
                    "\n".join(raw_values),
                    maximum_items=30,
                )
            ),
        )


class LanguageParser:
    def __init__(
            self,
            taxonomy: TaxonomyBundle,
    ) -> None:
        self._levels = (
            taxonomy.language_levels
        )

    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
    ) -> tuple[LanguageSkill, ...]:
        result: list[LanguageSkill] = []
        seen: set[str] = set()

        for section in section_texts.get(
                "LANGUAGES",
                (),
        ):
            for line in _iter_list_values(
                    section
            ):
                language = self._parse_line(
                    line
                )

                if language is None:
                    continue

                key = language.language.casefold()

                if key in seen:
                    continue

                seen.add(key)
                result.append(language)

                if len(result) >= MAX_LANGUAGES:
                    return tuple(result)

        return tuple(result)

    def _parse_line(
            self,
            line: str,
    ) -> LanguageSkill | None:
        raw = _strip_bullet(line)

        if not raw:
            return None

        (
            canonical_language,
            language_span,
        ) = self._find_language(raw)

        if (
                canonical_language is None
                or language_span is None
        ):
            parts = INLINE_SEPARATOR_PATTERN.split(
                raw,
                maxsplit=1,
            )

            if (
                    len(parts) != 2
                    or not _looks_like_language_name(
                parts[0]
            )
            ):
                return None

            canonical_language = _clean(
                parts[0],
                200,
            )

            language_span = (
                0,
                len(parts[0]),
            )

        if canonical_language is None:
            return None

        remainder = (
                raw[:language_span[0]]
                + " "
                + raw[language_span[1]:]
        ).strip(
            " -–—|:：,;()"
        )

        framework: str | None = None
        score: str | None = None

        framework_match = SCORE_PATTERN.search(
            raw
        )

        if framework_match is not None:
            framework = framework_match.group(
                "framework"
            ).upper()

            score = (
                framework_match.group(
                    "score"
                )
                .upper()
                .replace(",", ".")
            )
        else:
            generic_score = (
                GENERIC_SCORE_PATTERN.search(
                    remainder
                )
            )

            if generic_score is not None:
                score = (
                    generic_score.group(
                        "score"
                    )
                    .replace(",", ".")
                )

        (
            proficiency_text,
            normalized_proficiency,
        ) = self._match_proficiency(
            remainder
        )

        if (
                proficiency_text is None
                and framework_match is not None
        ):
            proficiency_text = (
                framework_match.group(0)
            )

        return LanguageSkill(
            language=canonical_language,
            proficiency_text=_clean(
                proficiency_text,
                500,
            ),
            normalized_proficiency=(
                normalized_proficiency
            ),
            framework=framework,
            score=score,
        )

    @staticmethod
    def _find_language(
            value: str,
    ) -> tuple[
        str | None,
        tuple[int, int] | None,
    ]:
        normalized = normalize_for_matching(
            value
        )

        for canonical, aliases in LANGUAGE_ALIASES:
            for alias in sorted(
                    aliases,
                    key=len,
                    reverse=True,
            ):
                normalized_alias = (
                    normalize_for_matching(
                        alias
                    )
                )

                normalized_match = re.search(
                    rf"(?<![\w])"
                    rf"{re.escape(normalized_alias)}"
                    rf"(?![\w])",
                    normalized,
                    re.UNICODE,
                )

                if normalized_match is None:
                    continue

                raw_match = re.search(
                    rf"(?<![\w])"
                    rf"{re.escape(alias)}"
                    rf"(?![\w])",
                    value,
                    re.IGNORECASE
                    | re.UNICODE,
                    )

                if raw_match is not None:
                    return (
                        canonical,
                        raw_match.span(),
                    )

                return (
                    canonical,
                    (
                        0,
                        len(alias),
                    ),
                )

        return None, None

    def _match_proficiency(
            self,
            value: str,
    ) -> tuple[str | None, str]:
        normalized_value = (
            normalize_for_matching(value)
        )

        candidates: list[
            tuple[int, str, str]
        ] = []

        for item in self._levels:
            if item.canonical == "UNKNOWN":
                continue

            for alias in item.aliases:
                normalized_alias = (
                    normalize_for_matching(
                        alias
                    )
                )

                match = re.search(
                    rf"(?<![\w])"
                    rf"{re.escape(normalized_alias)}"
                    rf"(?![\w])",
                    normalized_value,
                    re.UNICODE,
                )

                if match is not None:
                    candidates.append(
                        (
                            -len(
                                normalized_alias
                            ),
                            alias,
                            item.canonical,
                        )
                    )

        if not candidates:
            return None, "UNKNOWN"

        candidates.sort()

        (
            _,
            raw_alias,
            canonical,
        ) = candidates[0]

        return raw_alias, canonical


class AwardParser:
    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
    ) -> tuple[Award, ...]:
        result: list[Award] = []

        for section in section_texts.get(
                "AWARDS",
                (),
        ):
            for block in _split_list_entries(
                    section
            ):
                award = self._parse_block(
                    block
                )

                if award is not None:
                    result.append(award)

                if len(result) >= MAX_AWARDS:
                    return tuple(result)

        return tuple(result)

    @staticmethod
    def _parse_block(
            block: _TextBlock,
    ) -> Award | None:
        name: str | None = None
        issuer: str | None = None
        awarded_date: str | None = None

        description_lines: list[str] = []

        for raw_line in block.lines:
            line = _remove_date_range(
                _strip_bullet(raw_line)
            )

            if not line:
                continue

            match = AWARD_NAME_PATTERN.match(
                line
            )

            if match is not None:
                name = (
                        name
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = AWARD_ISSUER_PATTERN.match(
                line
            )

            if match is not None:
                issuer = (
                        issuer
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = AWARD_DATE_PATTERN.match(
                line
            )

            if match is not None:
                awarded_date = (
                        awarded_date
                        or _first_date(
                    match.group("value")
                ).value
                )
                continue

            if (
                    name is None
                    and _looks_like_title(line)
            ):
                name = _clean(
                    line,
                    500,
                )
            elif (
                    issuer is None
                    and _looks_like_provider(line)
            ):
                issuer = _clean(
                    line,
                    500,
                )
            else:
                description_lines.append(
                    line
                )

        if awarded_date is None:
            if block.date_range is not None:
                awarded_date = (
                    block.date_range.end.value
                )
            else:
                dates = _all_dates(
                    "\n".join(block.lines)
                )

                if dates:
                    awarded_date = (
                        dates[-1].value
                    )

        if name is None:
            return None

        return Award(
            name=name,
            issuer=issuer,
            awarded_date=awarded_date,
            description=_join_description(
                description_lines
            ),
        )


class PublicationParser:
    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
    ) -> tuple[Publication, ...]:
        result: list[Publication] = []

        for section in section_texts.get(
                "PUBLICATIONS",
                (),
        ):
            for block in _split_list_entries(
                    section
            ):
                publication = self._parse_block(
                    block
                )

                if publication is not None:
                    result.append(publication)

                if (
                        len(result)
                        >= MAX_PUBLICATIONS
                ):
                    return tuple(result)

        return tuple(result)

    @staticmethod
    def _parse_block(
            block: _TextBlock,
    ) -> Publication | None:
        title: str | None = None
        authors: list[str] = []
        publisher: str | None = None
        published_date: str | None = None
        url: str | None = None

        description_lines: list[str] = []

        for raw_line in block.lines:
            line = _remove_date_range(
                _strip_bullet(raw_line)
            )

            if not line:
                continue

            urls = _extract_urls(line)

            if urls and url is None:
                url = urls[0]
                line = _strip_urls(line)

            match = (
                PUBLICATION_TITLE_PATTERN.match(
                    line
                )
            )

            if match is not None:
                title = (
                        title
                        or _clean(
                    match.group("value"),
                    1_000,
                )
                )
                continue

            match = (
                PUBLICATION_AUTHOR_PATTERN.match(
                    line
                )
            )

            if match is not None:
                authors.extend(
                    LIST_SEPARATOR_PATTERN.split(
                        match.group("value")
                    )
                )
                continue

            match = (
                PUBLICATION_PUBLISHER_PATTERN.match(
                    line
                )
            )

            if match is not None:
                publisher = (
                        publisher
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = (
                PUBLICATION_DATE_PATTERN.match(
                    line
                )
            )

            if match is not None:
                published_date = (
                        published_date
                        or _first_date(
                    match.group("value")
                ).value
                )
                continue

            if (
                    title is None
                    and _looks_like_title(line)
            ):
                title = _clean(
                    line,
                    1_000,
                )
            elif (
                    publisher is None
                    and _looks_like_provider(line)
            ):
                publisher = _clean(
                    line,
                    500,
                )
            else:
                description_lines.append(
                    line
                )

        if published_date is None:
            if block.date_range is not None:
                published_date = (
                    block.date_range.end.value
                )
            else:
                dates = _all_dates(
                    "\n".join(block.lines)
                )

                if dates:
                    published_date = (
                        dates[-1].value
                    )

        if title is None:
            return None

        return Publication(
            title=title,
            authors=stable_unique(
                authors,
                maximum_items=50,
            ),
            publisher=publisher,
            published_date=published_date,
            url=url,
            description=_join_description(
                description_lines
            ),
        )


class VolunteerParser:
    def __init__(
            self,
            skill_parser: SkillParser,
    ) -> None:
        self._skill_parser = skill_parser

    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
    ) -> tuple[VolunteerExperience, ...]:
        result: list[
            VolunteerExperience
        ] = []

        for section in section_texts.get(
                "VOLUNTEERING",
                (),
        ):
            for block in _split_blocks(
                    section
            ):
                item = self._parse_block(
                    block
                )

                if item is not None:
                    result.append(item)

                if (
                        len(result)
                        >= MAX_VOLUNTEER_EXPERIENCES
                ):
                    return tuple(result)

        return tuple(result)

    def _parse_block(
            self,
            block: _TextBlock,
    ) -> VolunteerExperience | None:
        organization: str | None = None
        role: str | None = None

        description_lines: list[str] = []
        responsibilities: list[str] = []
        all_lines: list[str] = []

        for raw_line in block.lines:
            line = _remove_date_range(
                raw_line
            )

            if not line:
                continue

            all_lines.append(line)

            bullet = BULLET_PATTERN.match(
                line
            )

            if bullet is not None:
                value = _clean(
                    bullet.group("value"),
                    1_000,
                )

                if value is not None:
                    responsibilities.append(
                        value
                    )

                continue

            match = (
                VOLUNTEER_ORGANIZATION_PATTERN.match(
                    line
                )
            )

            if match is not None:
                organization = (
                        organization
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = VOLUNTEER_ROLE_PATTERN.match(
                line
            )

            if match is not None:
                role = (
                        role
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            if (
                    role is None
                    and _looks_like_title(line)
            ):
                role = _clean(
                    line,
                    500,
                )
            elif (
                    organization is None
                    and _looks_like_provider(line)
            ):
                organization = _clean(
                    line,
                    500,
                )
            else:
                description_lines.append(
                    line
                )

        if not any(
                (
                        organization,
                        role,
                        responsibilities,
                        description_lines,
                )
        ):
            return None

        return VolunteerExperience(
            organization_name=organization,
            role=role,
            start_date=(
                block.date_range.start.value
                if block.date_range is not None
                else None
            ),
            end_date=(
                block.date_range.end.value
                if block.date_range is not None
                else None
            ),
            description=_join_description(
                description_lines
            ),
            responsibilities=stable_unique(
                responsibilities,
                maximum_items=(
                    MAX_RESPONSIBILITIES
                ),
            ),
            skills=(
                self._skill_parser.extract_names(
                    "\n".join(all_lines),
                    maximum_items=50,
                )
            ),
        )


class ActivityParser:
    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
    ) -> tuple[ProfessionalActivity, ...]:
        result: list[
            ProfessionalActivity
        ] = []

        for section in section_texts.get(
                "ACTIVITIES",
                (),
        ):
            for block in _split_blocks(
                    section
            ):
                item = self._parse_block(
                    block
                )

                if item is not None:
                    result.append(item)

                if len(result) >= MAX_ACTIVITIES:
                    return tuple(result)

        return tuple(result)

    @staticmethod
    def _parse_block(
            block: _TextBlock,
    ) -> ProfessionalActivity | None:
        name: str | None = None
        organization: str | None = None
        role: str | None = None

        description_lines: list[str] = []

        for raw_line in block.lines:
            line = _remove_date_range(
                _strip_bullet(raw_line)
            )

            if not line:
                continue

            match = (
                ACTIVITY_ORGANIZATION_PATTERN.match(
                    line
                )
            )

            if match is not None:
                organization = (
                        organization
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            match = ACTIVITY_ROLE_PATTERN.match(
                line
            )

            if match is not None:
                role = (
                        role
                        or _clean(
                    match.group("value"),
                    500,
                )
                )
                continue

            if (
                    name is None
                    and _looks_like_title(line)
            ):
                name = _clean(
                    line,
                    500,
                )
            elif (
                    organization is None
                    and _looks_like_provider(line)
            ):
                organization = _clean(
                    line,
                    500,
                )
            else:
                description_lines.append(
                    line
                )

        if not any(
                (
                        name,
                        organization,
                        role,
                        description_lines,
                )
        ):
            return None

        return ProfessionalActivity(
            name=name,
            organization=organization,
            role=role,
            start_date=(
                block.date_range.start.value
                if block.date_range is not None
                else None
            ),
            end_date=(
                block.date_range.end.value
                if block.date_range is not None
                else None
            ),
            description=_join_description(
                description_lines
            ),
        )


class InterestParser:
    @staticmethod
    def parse(
            section_texts: dict[str, tuple[str, ...]],
    ) -> tuple[str, ...]:
        values: list[str] = []

        for section in section_texts.get(
                "INTERESTS",
                (),
        ):
            for raw_line in section.splitlines():
                line = _strip_bullet(
                    raw_line
                )

                if not line:
                    continue

                parts = (
                    LIST_SEPARATOR_PATTERN.split(
                        line
                    )
                )

                if (
                        len(parts) == 1
                        and len(line.split()) > 12
                ):
                    continue

                for part in parts:
                    cleaned = _clean(
                        part,
                        200,
                    )

                    if (
                            cleaned is not None
                            and 1
                            <= len(cleaned.split())
                            <= 10
                    ):
                        values.append(cleaned)

        return tuple(
            stable_unique(
                values,
                maximum_items=MAX_INTERESTS,
            )
        )


class SecondarySectionParsers:
    def __init__(
            self,
            settings: Settings,
            taxonomy: TaxonomyBundle,
            skill_parser: SkillParser,
    ) -> None:
        self._preferences = (
            CareerPreferenceParser(
                taxonomy
            )
        )

        self._projects = ProjectParser(
            settings,
            skill_parser,
        )

        self._certifications = (
            CertificationParser(
                settings,
                skill_parser,
            )
        )

        self._licenses = LicenseParser(
            settings
        )

        self._training = TrainingParser(
            settings,
            skill_parser,
        )

        self._languages = LanguageParser(
            taxonomy
        )

        self._awards = AwardParser()
        self._publications = PublicationParser()

        self._volunteering = VolunteerParser(
            skill_parser
        )

        self._activities = ActivityParser()
        self._interests = InterestParser()

    def parse(
            self,
            section_texts: dict[str, tuple[str, ...]],
            *,
            today: date | None = None,
    ) -> SecondarySectionParseResult:
        preferences = self._preferences.parse(
            section_texts
        )

        (
            certifications,
            certification_warnings,
        ) = self._certifications.parse(
            section_texts,
            today=today,
        )

        (
            licenses,
            license_warnings,
        ) = self._licenses.parse(
            section_texts,
            today=today,
        )

        warnings = tuple(
            dict.fromkeys(
                (
                    *preferences.warnings,
                    *certification_warnings,
                    *license_warnings,
                )
            )
        )

        return SecondarySectionParseResult(
            preferred_locations=(
                preferences.preferred_locations
            ),
            preferred_work_modes=(
                preferences.preferred_work_modes
            ),
            preferred_employment_types=(
                preferences.preferred_employment_types
            ),
            expected_salary_text=(
                preferences.expected_salary_text
            ),
            availability_text=(
                preferences.availability_text
            ),
            projects=self._projects.parse(
                section_texts
            ),
            certifications=certifications,
            licenses=licenses,
            training_courses=self._training.parse(
                section_texts
            ),
            languages=self._languages.parse(
                section_texts
            ),
            awards=self._awards.parse(
                section_texts
            ),
            publications=self._publications.parse(
                section_texts
            ),
            volunteer_experiences=(
                self._volunteering.parse(
                    section_texts
                )
            ),
            activities=self._activities.parse(
                section_texts
            ),
            interests=self._interests.parse(
                section_texts
            ),
            warnings=warnings,
        )


def _split_blocks(
        section_text: str,
) -> list[_TextBlock]:
    lines = section_text.splitlines()

    anchors: list[
        tuple[int, DateRange]
    ] = []

    for index, line in enumerate(lines):
        date_range = extract_date_range(
            line
        )

        if date_range is not None:
            anchors.append(
                (
                    index,
                    date_range,
                )
            )

    if anchors:
        starts: list[int] = []

        for anchor_index, _ in anchors:
            start = anchor_index
            inspected = 0
            cursor = anchor_index - 1

            while (
                    cursor >= 0
                    and inspected < 2
            ):
                candidate = lines[
                    cursor
                ].strip()

                if (
                        not candidate
                        or BULLET_PATTERN.match(
                    candidate
                )
                ):
                    break

                if (
                        extract_date_range(
                            candidate
                        )
                        is not None
                ):
                    break

                if not _looks_like_title(
                        candidate
                ):
                    break

                start = cursor
                inspected += 1
                cursor -= 1

            starts.append(start)

        result: list[_TextBlock] = []

        for index, (
                _,
                date_range,
        ) in enumerate(anchors):
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
                    _TextBlock(
                        lines=block_lines[
                            :MAX_ENTRY_LINES
                        ],
                        date_range=date_range,
                    )
                )

        return result

    paragraphs = _paragraphs(lines)

    if len(paragraphs) > 1:
        return [
            _TextBlock(
                lines=item,
                date_range=None,
            )
            for item in paragraphs
        ]

    non_empty = [
        line.strip()
        for line in lines
        if line.strip()
    ]

    if not non_empty:
        return []

    if (
            BULLET_PATTERN.match(
                non_empty[0]
            )
            is None
    ):
        return [
            _TextBlock(
                lines=tuple(
                    non_empty[
                        :MAX_ENTRY_LINES
                    ]
                ),
                date_range=None,
            )
        ]

    return [
        _TextBlock(
            lines=(line,),
            date_range=None,
        )
        for line in non_empty
    ]


def _split_list_entries(
        section_text: str,
) -> list[_TextBlock]:
    paragraphs = _paragraphs(
        section_text.splitlines()
    )

    if len(paragraphs) > 1:
        return [
            _TextBlock(
                lines=paragraph,
                date_range=(
                    _first_date_range(
                        paragraph
                    )
                ),
            )
            for paragraph in paragraphs
        ]

    lines = [
        line.strip()
        for line in section_text.splitlines()
        if line.strip()
    ]

    if not lines:
        return []

    result: list[_TextBlock] = []
    current: list[str] = []

    def flush_current() -> None:
        if not current:
            return

        current_tuple = tuple(
            current[:MAX_ENTRY_LINES]
        )

        result.append(
            _TextBlock(
                lines=current_tuple,
                date_range=(
                    _first_date_range(
                        current_tuple
                    )
                ),
            )
        )

        current.clear()

    for raw_line in lines:
        bullet_match = BULLET_PATTERN.match(
            raw_line
        )

        content = (
            bullet_match.group(
                "value"
            ).strip()
            if bullet_match is not None
            else raw_line
        )

        if not current:
            current.append(raw_line)
            continue

        current_has_metadata = any(
            _is_metadata_line(value)
            for value in current[1:]
        )

        current_first_line = _strip_bullet(
            current[0]
        )

        current_starts_like_entry = (
            _looks_like_list_entry_start(
                current_first_line
            )
        )

        line_starts_like_entry = (
            _looks_like_list_entry_start(
                content
            )
        )

        if _is_metadata_line(raw_line):
            current.append(raw_line)
            continue

        if bullet_match is not None:
            if (
                    line_starts_like_entry
                    and current_starts_like_entry
            ):
                flush_current()
                current.append(raw_line)
            else:
                current.append(raw_line)

            continue

        if (
                current_has_metadata
                and line_starts_like_entry
        ):
            flush_current()
            current.append(raw_line)
            continue

        if (
                not current_has_metadata
                and current_starts_like_entry
                and line_starts_like_entry
        ):
            flush_current()
            current.append(raw_line)
            continue

        current.append(raw_line)

    flush_current()

    return result


def _looks_like_list_entry_start(
        value: str,
) -> bool:
    cleaned = _strip_bullet(
        value
    ).strip()

    if not _looks_like_title(cleaned):
        return False

    explicit_name_patterns = (
        CERT_NAME_PATTERN,
        LICENSE_NAME_PATTERN,
        TRAINING_NAME_PATTERN,
        AWARD_NAME_PATTERN,
        PUBLICATION_TITLE_PATTERN,
    )

    if any(
            pattern.match(cleaned) is not None
            for pattern in explicit_name_patterns
    ):
        return True

    normalized = normalize_for_matching(
        cleaned
    )

    entry_keywords = (
        "certificate",
        "certification",
        "license",
        "permit",
        "registration",
        "training",
        "course",
        "workshop",
        "program",
        "programme",
        "award",
        "honor",
        "honour",
        "publication",
        "research",
        "study",
        "article",
        "paper",
        "chứng chỉ",
        "chứng nhận",
        "giấy phép",
        "bằng lái",
        "khóa học",
        "đào tạo",
        "bồi dưỡng",
        "giải thưởng",
        "danh hiệu",
        "nghiên cứu",
        "bài báo",
    )

    if any(
            _contains_phrase(
                normalized,
                keyword,
            )
            for keyword in entry_keywords
    ):
        return True

    words = [
        word.strip(
            ".,:;()[]{}"
        )
        for word in cleaned.split()
        if any(
            character.isalpha()
            for character in word
        )
    ]

    if not words or len(words) > 12:
        return False

    title_like_words = sum(
        1
        for word in words
        if (
                word.isupper()
                or word[:1].isupper()
        )
    )

    title_case_ratio = (
            title_like_words
            / len(words)
    )

    return title_case_ratio >= 0.65


def _is_metadata_line(
        value: str,
) -> bool:
    patterns = (
        CERT_ISSUER_PATTERN,
        ISSUED_DATE_PATTERN,
        EXPIRATION_DATE_PATTERN,
        CREDENTIAL_ID_PATTERN,
        LICENSE_AUTHORITY_PATTERN,
        LICENSE_JURISDICTION_PATTERN,
        LICENSE_NUMBER_PATTERN,
        TRAINING_PROVIDER_PATTERN,
        TRAINING_DATE_PATTERN,
        TRAINING_DURATION_PATTERN,
        AWARD_ISSUER_PATTERN,
        AWARD_DATE_PATTERN,
        PUBLICATION_AUTHOR_PATTERN,
        PUBLICATION_PUBLISHER_PATTERN,
        PUBLICATION_DATE_PATTERN,
    )

    if any(
            pattern.match(value) is not None
            for pattern in patterns
    ):
        return True

    if URL_PATTERN.search(value) is not None:
        return True

    stripped = value.strip(
        " -–—|,;()"
    )

    return (
            SINGLE_DATE_PATTERN.fullmatch(
                stripped
            )
            is not None
    )


def _paragraphs(
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


def _iter_list_values(
        section_text: str,
):
    for raw_line in section_text.splitlines():
        line = raw_line.strip()

        if not line:
            continue

        bullet = BULLET_PATTERN.match(
            line
        )

        if bullet is not None:
            yield bullet.group(
                "value"
            ).strip()
            continue

        yield line


def _first_date_range(
        lines: tuple[str, ...],
) -> DateRange | None:
    for line in lines:
        value = extract_date_range(
            line
        )

        if value is not None:
            return value

    return None


def _first_date(
        value: str,
) -> DateValue:
    for match in SINGLE_DATE_PATTERN.finditer(
            value
    ):
        normalized = normalize_date_value(
            match.group("value")
        )

        if normalized.value is not None:
            return normalized

    return DateValue(
        value=None,
        year=None,
        month=None,
        precision=None,
        current=False,
    )


def _all_dates(
        value: str,
) -> list[DateValue]:
    result: list[DateValue] = []
    seen: set[str] = set()

    for match in SINGLE_DATE_PATTERN.finditer(
            value
    ):
        normalized = normalize_date_value(
            match.group("value")
        )

        if (
                normalized.value is None
                or normalized.value in seen
        ):
            continue

        seen.add(normalized.value)
        result.append(normalized)

    return result


def _extract_urls(
        value: str,
) -> list[str]:
    result: list[str] = []

    for match in URL_PATTERN.finditer(value):
        normalized = validate_http_url(
            match.group(0)
        )

        if normalized is not None:
            result.append(normalized)

    return stable_unique(
        result,
        maximum_items=10,
    )


def _strip_urls(
        value: str,
) -> str:
    return URL_PATTERN.sub(
        "",
        value,
    ).strip(
        " -–—|,;()"
    )


def _strip_bullet(
        value: str,
) -> str:
    match = BULLET_PATTERN.match(
        value.strip()
    )

    if match is not None:
        return match.group(
            "value"
        ).strip()

    return value.strip()


def _remove_date_range(
        value: str,
) -> str:
    date_range = extract_date_range(
        value
    )

    if date_range is None:
        return value.strip()

    return (
            value[:date_range.start_index]
            + " "
            + value[date_range.end_index:]
    ).strip(
        " -–—|,;"
    )


def _contains_phrase(
        normalized_text: str,
        raw_phrase: str,
) -> bool:
    phrase = normalize_for_matching(
        raw_phrase
    )

    if not phrase:
        return False

    return (
            re.search(
                rf"(?<![\w])"
                rf"{re.escape(phrase)}"
                rf"(?![\w])",
                normalized_text,
                re.UNICODE,
            )
            is not None
    )


def _clean(
        value: str | None,
        maximum_length: int,
) -> str | None:
    return clean_optional_text(
        value,
        maximum_length=maximum_length,
    )


def _join_description(
        values: list[str],
) -> str | None:
    return clean_optional_text(
        "\n".join(values),
        maximum_length=(
            MAX_DESCRIPTION_LENGTH
        ),
    )


def _is_project_achievement(
        value: str,
) -> bool:
    if ACHIEVEMENT_RECOGNITION_PATTERN.search(
            value
    ):
        return True

    return (
            ACHIEVEMENT_RESULT_VERB_PATTERN.search(
                value
            )
            is not None
            and ACHIEVEMENT_MEASURABLE_RESULT_PATTERN.search(
        value
    )
            is not None
    )


def _looks_like_wrapped_bullet_continuation(
        previous_value: str,
        value: str,
) -> bool:
    previous = previous_value.strip()
    cleaned = value.strip()

    if (
            not previous
            or not cleaned
            or len(cleaned) > 1_000
            or BULLET_PATTERN.match(cleaned)
            or extract_date_range(cleaned) is not None
            or PROJECT_NAME_PATTERN.match(cleaned)
            or PROJECT_ROLE_PATTERN.match(cleaned)
            or PROJECT_DOMAIN_PATTERN.match(cleaned)
            or PROJECT_DESCRIPTION_PATTERN.match(cleaned)
            or TEAM_SIZE_PATTERN.match(cleaned)
            or PROJECT_TYPE_PATTERN.match(cleaned)
            or PROJECT_TECH_STACK_PATTERN.match(cleaned)
            or PROJECT_LINK_LABEL_PATTERN.match(cleaned)
    ):
        return False

    _, inline_role = _split_project_name_and_role(
        cleaned
    )

    if inline_role is not None:
        return False

    first_alpha: str | None = None

    for character in cleaned:
        if character.isalpha():
            first_alpha = character
            break

        if character.isdigit():
            break

    if (
            first_alpha is not None
            and first_alpha.islower()
    ):
        return True

    previous_normalized = normalize_for_matching(
        previous
    )

    continuation_suffix_pattern = re.compile(
        r"(?:"
        r"[,;:/(-]$|"
        r"\b(?:and|or|with|using|through|including|for|to|by|of|"
        r"in|on|from|into|via|such\s+as|"
        r"và|va|hoặc|hoac|với|voi|bằng|bang|qua|gồm|gom|"
        r"bao\s+gồm|bao\s+gom|cho|để|de|của|cua|trong|từ|tu)"
        r"$"
        r")",
        re.IGNORECASE,
    )

    if continuation_suffix_pattern.search(
            previous_normalized
    ) is None:
        return False

    return (
            re.match(
                r"^(?:[A-ZĐ]{2,}(?:[./+#-][A-Z0-9Đ]+)*|"
                r"[A-ZĐ][A-Za-zÀ-ỹ0-9]*(?:\s+[A-Z0-9][^.!?]{0,80})?)",
                cleaned,
            )
            is not None
    )


def _split_project_name_and_role(
        value: str,
) -> tuple[str | None, str | None]:
    cleaned = _clean(
        value,
        500,
    )

    if cleaned is None:
        return None, None

    match = PROJECT_INLINE_ROLE_PATTERN.match(
        cleaned
    )

    if match is None:
        return cleaned, None

    candidate_name = _clean(
        match.group("name"),
        500,
    )
    candidate_role = _clean(
        match.group("role"),
        500,
    )

    if (
            candidate_name is None
            or candidate_role is None
            or PROJECT_ROLE_HINT_PATTERN.search(
        candidate_role
    ) is None
    ):
        return cleaned, None

    return candidate_name, candidate_role


def _first_certification_date(
        value: str,
) -> DateValue:
    normalized = normalize_date_value(
        value.strip()
    )

    if normalized.value is not None:
        return normalized

    vietnamese_match = re.fullmatch(
        r"(?:tháng|thang)\s+"
        r"(?P<month>0?[1-9]|1[0-2])\s+"
        r"(?:năm|nam)\s+"
        r"(?P<year>(?:19|20)\d{2})",
        value.strip(),
        re.IGNORECASE,
    )

    if vietnamese_match is not None:
        return normalize_date_value(
            f"{vietnamese_match.group('month')}/"
            f"{vietnamese_match.group('year')}"
        )

    return DateValue(
        value=None,
        year=None,
        month=None,
        precision=None,
        current=False,
    )


def _extract_trailing_certification_date(
        value: str,
) -> tuple[str, DateValue | None]:
    match = TRAILING_CERTIFICATION_DATE_PATTERN.search(
        value
    )

    if match is None:
        return value.strip(), None

    normalized = _first_certification_date(
        match.group("value")
    )

    if normalized.value is None:
        return value.strip(), None

    residue = value[:match.start()].strip(
        " -–—|,;()"
    )

    return residue, normalized


def _looks_like_certification_name(
        value: str,
) -> bool:
    return (
            CERTIFICATION_NAME_HINT_PATTERN.search(
                value
            )
            is not None
            or CERTIFICATION_EXAM_PATTERN.search(
        value
    )
            is not None
    )


def _looks_like_certification_issuer_candidate(
        value: str,
) -> bool:
    cleaned = value.strip()

    if (
            not _looks_like_title(cleaned)
            or _looks_like_certification_name(cleaned)
    ):
        return False

    if _looks_like_provider(cleaned):
        return True

    if CERTIFICATION_ISSUER_ORGANIZATION_PATTERN.search(
            cleaned
    ):
        return True

    words = [
        word.strip(".,:;()[]{}")
        for word in cleaned.split()
        if word.strip(".,:;()[]{}")
    ]

    if len(words) == 1:
        return bool(
            re.fullmatch(
                r"[A-Za-zÀ-ỹ][A-Za-zÀ-ỹ0-9.&+'-]{1,40}",
                words[0],
            )
        )

    return any(
        word.isupper()
        and 2 <= len(word) <= 12
        for word in words
    )


def _split_certification_name_and_issuer(
        value: str,
) -> tuple[str | None, str | None]:
    cleaned = _clean(
        value,
        500,
    )

    if cleaned is None:
        return None, None

    parts = CERTIFICATION_INLINE_SEPARATOR_PATTERN.split(
        cleaned
    )

    if len(parts) != 2:
        return cleaned, None

    left = _clean(parts[0], 500)
    right = _clean(parts[1], 500)

    if left is None or right is None:
        return cleaned, None

    left_is_name = _looks_like_certification_name(
        left
    )
    right_is_name = _looks_like_certification_name(
        right
    )

    left_is_issuer = (
        _looks_like_certification_issuer_candidate(
            left
        )
    )
    right_is_issuer = (
        _looks_like_certification_issuer_candidate(
            right
        )
    )

    if (
            right_is_name
            and left_is_issuer
            and not left_is_name
    ):
        return right, left

    if (
            left_is_name
            and right_is_issuer
            and not right_is_name
    ):
        return left, right

    return cleaned, None

def _looks_like_title(
        value: str,
) -> bool:
    cleaned = value.strip()

    if (
            not cleaned
            or len(cleaned) > 250
            or len(cleaned.split()) > 20
    ):
        return False

    if cleaned.endswith(
            (
                    ".",
                    "!",
                    "?",
            )
    ):
        return False

    if URL_PATTERN.search(cleaned):
        cleaned = _strip_urls(cleaned)

    return bool(cleaned)


def _looks_like_provider(
        value: str,
) -> bool:
    normalized = normalize_for_matching(
        value
    )

    provider_terms = (
        "university",
        "college",
        "institute",
        "academy",
        "association",
        "organization",
        "organisation",
        "company",
        "department",
        "ministry",
        "hospital",
        "school",
        "center",
        "centre",
        "trường",
        "học viện",
        "viện",
        "hiệp hội",
        "tổ chức",
        "công ty",
        "bộ",
        "sở",
        "bệnh viện",
        "trung tâm",
    )

    return any(
        _contains_phrase(
            normalized,
            term,
        )
        for term in provider_terms
    )


def _looks_like_language_name(
        value: str,
) -> bool:
    cleaned = value.strip()

    if not 1 <= len(cleaned.split()) <= 4:
        return False

    return (
            sum(
                character.isalpha()
                for character in cleaned
            )
            >= 3
    )