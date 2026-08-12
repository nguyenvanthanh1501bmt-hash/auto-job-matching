from __future__ import annotations

import math
import statistics

import fitz

from app.config import Settings
from app.exceptions import (
    CvCorruptFileError,
    CvTextNotExtractableError,
    CvUnsupportedFormatError,
)
from app.extraction.base import DocumentExtractor, ExtractionResult


class PdfExtractor(DocumentExtractor):
    def __init__(
            self,
            settings: Settings,
    ) -> None:
        self._settings = settings

    def extract(
            self,
            data: bytes,
            raw_cv_id: str | None = None,
    ) -> ExtractionResult:
        # Kiểm tra magic bytes để xác nhận file thực sự là PDF.
        if not data.startswith(b"%PDF-"):
            raise CvUnsupportedFormatError(
                message=(
                    "The file extension or content type indicates PDF, "
                    "but the file signature is not PDF"
                ),
                raw_cv_id=raw_cv_id,
            )

        try:
            # Mở PDF trực tiếp từ bytes trong memory.
            document = fitz.open(
                stream=data,
                filetype="pdf",
            )
        except (fitz.FileDataError, RuntimeError, ValueError) as exception:
            raise CvCorruptFileError(
                message="The PDF document is corrupt or malformed",
                raw_cv_id=raw_cv_id,
            ) from exception

        try:
            # Không hỗ trợ PDF yêu cầu password.
            if document.needs_pass:
                raise CvCorruptFileError(
                    message="Password-protected PDF documents are not supported",
                    raw_cv_id=raw_cv_id,
                )

            page_count = document.page_count

            # PDF không có page thì không thể extract CV.
            if page_count <= 0:
                raise CvTextNotExtractableError(raw_cv_id=raw_cv_id)

            # Giới hạn số trang để tránh file quá lớn gây tốn tài nguyên.
            if page_count > self._settings.max_pdf_pages:
                raise CvCorruptFileError(
                    message="The PDF document contains too many pages",
                    raw_cv_id=raw_cv_id,
                )

            page_texts: list[str] = []
            multi_column_pages = 0

            # Extract text từ từng page và kiểm tra layout.
            for page_index in range(page_count):
                page = document.load_page(page_index)

                page_texts.append(
                    page.get_text("text", sort=True)
                )

                if self._looks_multi_column(page):
                    multi_column_pages += 1

            # Ghép text của tất cả page thành một chuỗi.
            text = "\n\n".join(page_texts).strip()

            # Chỉ tính các ký tự có ý nghĩa để xác định PDF có text hay không.
            if len(self._meaningful_characters(text)) < (
                    self._settings.min_text_characters
            ):
                raise CvTextNotExtractableError(
                    raw_cv_id=raw_cv_id
                )

            warnings: list[str] = []

            # Nếu ít nhất khoảng 1/3 số page có dấu hiệu 2 cột,
            # cảnh báo rằng thứ tự text có thể bị thay đổi khi extract.
            if multi_column_pages >= max(
                    1,
                    math.ceil(page_count / 3),
            ):
                warnings.extend(
                    [
                        "MULTI_COLUMN_LAYOUT_SUSPECTED",
                        "TEXT_LAYOUT_MAY_BE_LOST",
                    ]
                )

            return ExtractionResult(
                text=text,
                warnings=tuple(dict.fromkeys(warnings)),
                page_count=page_count,
            )

        except CvCorruptFileError:
            raise
        except CvTextNotExtractableError:
            raise

        # Chuyển các lỗi xử lý PDF của PyMuPDF thành lỗi nghiệp vụ.
        except (
                fitz.FileDataError,
                RuntimeError,
                ValueError,
        ) as exception:
            raise CvCorruptFileError(
                message="The PDF document could not be processed",
                raw_cv_id=raw_cv_id,
            ) from exception

        finally:
            # Luôn đóng document kể cả khi extraction xảy ra lỗi.
            document.close()

    @staticmethod
    def _meaningful_characters(text: str) -> str:
        # Chỉ giữ lại chữ và số để kiểm tra lượng text thực tế.
        return "".join(
            character
            for character in text
            if character.isalnum()
        )

    @staticmethod
    def _looks_multi_column(page: fitz.Page) -> bool:
        # Lấy các text block trên page để phân tích vị trí.
        blocks = page.get_text("blocks")

        # Quá ít block thì chưa đủ dữ liệu để nhận diện 2 cột.
        if len(blocks) < 6:
            return False

        page_width = float(page.rect.width)

        if page_width <= 0:
            return False

        centers: list[float] = []

        for block in blocks:
            x0 = float(block[0])
            x1 = float(block[2])
            text = str(block[4]).strip()

            # Bỏ qua các block quá ngắn vì thường không đại diện
            # cho nội dung chính của cột.
            if len(text) < 10:
                continue

            # Lấy tọa độ trung tâm theo chiều ngang của text block.
            centers.append((x0 + x1) / 2.0)

        if len(centers) < 6:
            return False

        # Phân loại block nằm về phía trái/phải của trang.
        left_centers = [
            center
            for center in centers
            if center < page_width * 0.46
        ]

        right_centers = [
            center
            for center in centers
            if center > page_width * 0.54
        ]

        # Cần có đủ block ở cả hai phía mới xem xét là 2 cột.
        if len(left_centers) < 2 or len(right_centers) < 2:
            return False

        # Median giúp giảm ảnh hưởng của các block nằm lệch vị trí.
        left_median = statistics.median(left_centers)
        right_median = statistics.median(right_centers)

        # Nếu khoảng cách giữa hai nhóm đủ lớn -> nghi ngờ layout 2 cột.
        return right_median - left_median > page_width * 0.25