from __future__ import annotations

import io
import zipfile
from collections.abc import Iterable

from docx import Document
from docx.document import Document as DocumentObject
from docx.opc.exceptions import PackageNotFoundError
from docx.table import _Cell, Table
from docx.text.paragraph import Paragraph

from app.config import Settings
from app.exceptions import (
    CvCorruptFileError,
    CvTextNotExtractableError,
    CvUnsupportedFormatError,
)
from app.extraction.base import DocumentExtractor, ExtractionResult


DOCX_REQUIRED_ENTRY = "word/document.xml"

ZIP_LOCAL_FILE_HEADER = b"PK\x03\x04"
ZIP_EMPTY_ARCHIVE = b"PK\x05\x06"
ZIP_SPANNED_ARCHIVE = b"PK\x07\x08"


class DocxExtractor(DocumentExtractor):
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
        if not data.startswith(
                (
                        ZIP_LOCAL_FILE_HEADER,
                        ZIP_EMPTY_ARCHIVE,
                        ZIP_SPANNED_ARCHIVE,
                )
        ):
            raise CvUnsupportedFormatError(
                message=(
                    "The file extension or content type indicates DOCX, "
                    "but the file is not an Office Open XML archive"
                ),
                raw_cv_id=raw_cv_id,
            )

        self._validate_archive(
            data=data,
            raw_cv_id=raw_cv_id,
        )

        try:
            document = Document(io.BytesIO(data))
        except (
                PackageNotFoundError,
                KeyError,
                ValueError,
                zipfile.BadZipFile,
                OSError,
        ) as exception:
            raise CvCorruptFileError(
                message="The DOCX document is corrupt or malformed",
                raw_cv_id=raw_cv_id,
            ) from exception

        text_parts: list[str] = []

        for block in self._iter_block_items(document):
            if isinstance(block, Paragraph):
                text = block.text.strip()
                if text:
                    text_parts.append(text)
                continue

            if isinstance(block, Table):
                text_parts.extend(self._extract_table(block))

        for section in document.sections:
            header_text = self._container_text(section.header)
            footer_text = self._container_text(section.footer)

            if header_text:
                text_parts.append(header_text)

            if footer_text:
                text_parts.append(footer_text)

        text = "\n".join(text_parts).strip()

        meaningful = "".join(
            character
            for character in text
            if character.isalnum()
        )

        if len(meaningful) < self._settings.min_text_characters:
            raise CvTextNotExtractableError(raw_cv_id=raw_cv_id)

        return ExtractionResult(
            text=text,
            warnings=(),
            page_count=None,
        )

    def _validate_archive(
            self,
            data: bytes,
            raw_cv_id: str | None,
    ) -> None:
        try:
            with zipfile.ZipFile(io.BytesIO(data)) as archive:
                entries = archive.infolist()

                if len(entries) > self._settings.max_docx_entries:
                    raise CvCorruptFileError(
                        message="The DOCX archive contains too many entries",
                        raw_cv_id=raw_cv_id,
                    )

                names = {entry.filename for entry in entries}

                if DOCX_REQUIRED_ENTRY not in names:
                    raise CvCorruptFileError(
                        message=(
                            "The Office archive does not contain "
                            "a DOCX document"
                        ),
                        raw_cv_id=raw_cv_id,
                    )

                total_uncompressed = 0

                for entry in entries:
                    if entry.flag_bits & 0x1:
                        raise CvCorruptFileError(
                            message=(
                                "Encrypted DOCX documents are not supported"
                            ),
                            raw_cv_id=raw_cv_id,
                        )

                    if self._is_unsafe_archive_path(entry.filename):
                        raise CvCorruptFileError(
                            message=(
                                "The DOCX archive contains an unsafe entry"
                            ),
                            raw_cv_id=raw_cv_id,
                        )

                    total_uncompressed += entry.file_size

                    if (
                            total_uncompressed
                            > self._settings.max_docx_uncompressed_bytes
                    ):
                        raise CvCorruptFileError(
                            message=(
                                "The DOCX archive exceeds the configured "
                                "uncompressed size limit"
                            ),
                            raw_cv_id=raw_cv_id,
                        )

                    if (
                            entry.compress_size > 0
                            and entry.file_size > 10 * 1024 * 1024
                            and entry.file_size / entry.compress_size > 200
                    ):
                        raise CvCorruptFileError(
                            message=(
                                "The DOCX archive has a suspicious "
                                "compression ratio"
                            ),
                            raw_cv_id=raw_cv_id,
                        )
        except CvCorruptFileError:
            raise
        except zipfile.BadZipFile as exception:
            raise CvCorruptFileError(
                message="The DOCX document is corrupt or malformed",
                raw_cv_id=raw_cv_id,
            ) from exception

    @staticmethod
    def _is_unsafe_archive_path(filename: str) -> bool:
        normalized = filename.replace("\\", "/")

        if normalized.startswith("/"):
            return True

        return any(
            segment == ".."
            for segment in normalized.split("/")
        )

    @staticmethod
    def _iter_block_items(
            parent: DocumentObject | _Cell,
    ) -> Iterable[Paragraph | Table]:
        parent_element = parent.element.body

        for child in parent_element.iterchildren():
            tag = child.tag

            if tag.endswith("}p"):
                yield Paragraph(child, parent)
            elif tag.endswith("}tbl"):
                yield Table(child, parent)

    @staticmethod
    def _extract_table(table: Table) -> list[str]:
        rows: list[str] = []

        for row in table.rows:
            cells: list[str] = []
            seen_cells: set[int] = set()

            for cell in row.cells:
                cell_identity = id(cell._tc)

                if cell_identity in seen_cells:
                    continue

                seen_cells.add(cell_identity)

                value = " ".join(
                    paragraph.text.strip()
                    for paragraph in cell.paragraphs
                    if paragraph.text.strip()
                )

                if value:
                    cells.append(value)

            if cells:
                rows.append(" | ".join(cells))

        return rows

    @staticmethod
    def _container_text(container) -> str:
        values = [
            paragraph.text.strip()
            for paragraph in container.paragraphs
            if paragraph.text.strip()
        ]

        return "\n".join(values)