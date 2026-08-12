from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path

from app.config import Settings
from app.exceptions import (
    CvDocExtractionFailedError,
    CvExtractionTimeoutError,
    CvTextNotExtractableError,
    CvUnsupportedFormatError,
)
from app.extraction.base import DocumentExtractor, ExtractionResult


# Magic bytes của file Microsoft Word DOC dạng OLE Compound File.
OLE_COMPOUND_FILE_SIGNATURE = bytes.fromhex(
    "D0CF11E0A1B11AE1"
)


class DocExtractor(DocumentExtractor):
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
        # Kiểm tra file có đúng định dạng DOC legacy hay không.
        if not data.startswith(OLE_COMPOUND_FILE_SIGNATURE):
            raise CvUnsupportedFormatError(
                message=(
                    "The file extension or content type indicates DOC, "
                    "but the file is not a legacy Microsoft Word document"
                ),
                raw_cv_id=raw_cv_id,
            )

        # Kiểm tra antiword đã được cài đặt hay chưa.
        # antiword là công cụ dùng đọc file doc cũ
        executable = shutil.which("antiword")

        if executable is None:
            raise CvDocExtractionFailedError(
                message="The legacy DOC extractor is unavailable",
                raw_cv_id=raw_cv_id,
            )

        # Tạo thư mục tạm để lưu file DOC trong quá trình xử lý.
        with tempfile.TemporaryDirectory(
                prefix="autojob-cv-doc-"
        ) as directory:
            directory_path = Path(directory)
            input_path = directory_path / "document.doc"

            # Ghi dữ liệu CV ra file tạm.
            input_path.write_bytes(data)

            # Chỉ cho phép owner đọc và ghi file.
            os.chmod(input_path, 0o600)

            try:
                # Chạy antiword để extract text từ file DOC.
                process = subprocess.run(
                    [
                        executable,
                        "-m",
                        "UTF-8.txt",
                        str(input_path),
                    ],
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    timeout=self._settings.doc_command_timeout_seconds,
                    check=False,
                    shell=False,
                    cwd=directory,
                    env={
                        "PATH": os.environ.get(
                            "PATH",
                            "/usr/local/bin:/usr/bin:/bin",
                        ),
                        "LANG": "C.UTF-8",
                        "LC_ALL": "C.UTF-8",
                        "HOME": directory,
                    },
                )

            except subprocess.TimeoutExpired as exception:
                # antiword chạy quá thời gian cho phép.
                raise CvExtractionTimeoutError(
                    raw_cv_id=raw_cv_id
                ) from exception

            except OSError as exception:
                # Không thể khởi chạy antiword.
                raise CvDocExtractionFailedError(
                    message="The legacy DOC extractor could not be started",
                    raw_cv_id=raw_cv_id,
                ) from exception

        # Kiểm tra antiword có extract thành công hay không.
        if process.returncode != 0:
            raise CvDocExtractionFailedError(
                message="The legacy DOC document could not be extracted",
                raw_cv_id=raw_cv_id,
            )

        # Decode output của antiword thành text.
        text = self._decode_output(process.stdout).strip()

        # Chỉ tính chữ và số để kiểm tra lượng text thực tế.
        meaningful = "".join(
            character
            for character in text
            if character.isalnum()
        )

        # Nếu text quá ít thì xem như không thể extract nội dung CV.
        if len(meaningful) < self._settings.min_text_characters:
            raise CvTextNotExtractableError(
                raw_cv_id=raw_cv_id
            )

        # DOC legacy có thể làm mất layout trong quá trình extract.
        return ExtractionResult(
            text=text,
            warnings=("TEXT_LAYOUT_MAY_BE_LOST",),
            page_count=None,
        )

    @staticmethod
    def _decode_output(value: bytes) -> str:
        # Thử các encoding phổ biến để decode output từ antiword.
        for encoding in (
                "utf-8",
                "utf-8-sig",
                "cp1252",
                "latin-1",
        ):
            try:
                return value.decode(encoding)
            except UnicodeDecodeError:
                continue

        # Nếu tất cả encoding đều thất bại thì thay ký tự lỗi.
        return value.decode(
            "utf-8",
            errors="replace",
        )

    @staticmethod
    def is_ready() -> bool:
        # Kiểm tra antiword có sẵn trên hệ thống hay không.
        return shutil.which("antiword") is not None