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
        if not data.startswith(OLE_COMPOUND_FILE_SIGNATURE):
            raise CvUnsupportedFormatError(
                message=(
                    "The file extension or content type indicates DOC, "
                    "but the file is not a legacy Microsoft Word document"
                ),
                raw_cv_id=raw_cv_id,
            )

        executable = shutil.which("antiword")

        if executable is None:
            raise CvDocExtractionFailedError(
                message="The legacy DOC extractor is unavailable",
                raw_cv_id=raw_cv_id,
            )

        with tempfile.TemporaryDirectory(
                prefix="autojob-cv-doc-"
        ) as directory:
            directory_path = Path(directory)
            input_path = directory_path / "document.doc"

            input_path.write_bytes(data)
            os.chmod(input_path, 0o600)

            try:
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
                raise CvExtractionTimeoutError(
                    raw_cv_id=raw_cv_id
                ) from exception
            except OSError as exception:
                raise CvDocExtractionFailedError(
                    message="The legacy DOC extractor could not be started",
                    raw_cv_id=raw_cv_id,
                ) from exception

        if process.returncode != 0:
            raise CvDocExtractionFailedError(
                message="The legacy DOC document could not be extracted",
                raw_cv_id=raw_cv_id,
            )

        text = self._decode_output(process.stdout).strip()

        meaningful = "".join(
            character
            for character in text
            if character.isalnum()
        )

        if len(meaningful) < self._settings.min_text_characters:
            raise CvTextNotExtractableError(raw_cv_id=raw_cv_id)

        return ExtractionResult(
            text=text,
            warnings=("TEXT_LAYOUT_MAY_BE_LOST",),
            page_count=None,
        )

    @staticmethod
    def _decode_output(value: bytes) -> str:
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

        return value.decode(
            "utf-8",
            errors="replace",
        )

    @staticmethod
    def is_ready() -> bool:
        return shutil.which("antiword") is not None