from __future__ import annotations

import io
from dataclasses import dataclass
from typing import BinaryIO

from minio import Minio
from minio.error import S3Error

from app.config import Settings
from app.exceptions import (
    CvFileTooLargeError,
    CvInternalError,
    CvInvalidRequestError,
    CvObjectNotFoundError,
)


@dataclass(frozen=True, slots=True)
class StoredObject:
    # Đại diện cho object được đọc từ MinIO cùng metadata liên quan.
    data: bytes
    size: int
    content_type: str | None
    etag: str | None


class MinioStorage:
    # Cung cấp abstract để truy cập và kiểm tra file CV trên MinIO.
    def __init__(
            self,
            settings: Settings,
            client: Minio | None = None,
    ) -> None:
        self._settings = settings

        # Cho phép inject MinIO client để thuận tiện cho testing.
        self._client = client or Minio(
            endpoint=settings.minio_endpoint,
            access_key=settings.minio_access_key,
            secret_key=settings.minio_secret_key,
            secure=settings.minio_secure,
        )

    def assert_allowed_location(
            self,
            bucket: str,
            object_key: str,
            raw_cv_id: str | None = None,
    ) -> None:
        # Chỉ cho phép truy cập bucket được cấu hình cho CV parser.
        if bucket != self._settings.minio_bucket:
            raise CvInvalidRequestError(
                "The requested bucket is not allowed",
                raw_cv_id=raw_cv_id,
            )

        # Chặn object key tuyệt đối, path traversal và path segment không hợp lệ.
        if (
                object_key.startswith("/")
                or "\\" in object_key
                or any(
            segment in {"", ".", ".."}
            for segment in object_key.split("/")
        )
        ):
            raise CvInvalidRequestError(
                "The object key is invalid",
                raw_cv_id=raw_cv_id,
            )

        # Chặn các control character có thể gây ra hành vi không mong muốn.
        if any(ord(character) < 32 for character in object_key):
            raise CvInvalidRequestError(
                "The object key contains control characters",
                raw_cv_id=raw_cv_id,
            )

        # Chỉ cho phép object nằm trong các prefix đã được cấu hình.
        if not any(
                object_key.startswith(prefix)
                for prefix in self._settings.allowed_object_prefixes
        ):
            raise CvInvalidRequestError(
                "The object key prefix is not allowed",
                raw_cv_id=raw_cv_id,
            )

    def get_object(
            self,
            bucket: str,
            object_key: str,
            raw_cv_id: str | None = None,
    ) -> StoredObject:
        # Validate bucket và object key trước khi truy cập MinIO.
        self.assert_allowed_location(
            bucket=bucket,
            object_key=object_key,
            raw_cv_id=raw_cv_id,
        )

        try:
            # Lấy metadata trước để kiểm tra object có tồn tại
            # và kích thước có nằm trong giới hạn cho phép hay không.
            stat = self._client.stat_object(
                bucket_name=bucket,
                object_name=object_key,
            )
        except S3Error as exception:
            # Chuyển các lỗi object không tồn tại thành domain exception.
            if exception.code in {
                "NoSuchKey",
                "NoSuchObject",
                "NoSuchBucket",
                "NotFound",
            }:
                raise CvObjectNotFoundError(
                    raw_cv_id=raw_cv_id
                ) from exception

            # Các lỗi MinIO khác được chuyển thành lỗi nội bộ.
            raise CvInternalError(raw_cv_id=raw_cv_id) from exception

        # Metadata phải chứa kích thước hợp lệ.
        if stat.size is None or stat.size < 0:
            raise CvInternalError(raw_cv_id=raw_cv_id)

        # Từ chối file vượt quá giới hạn kích thước cấu hình.
        if stat.size > self._settings.max_object_size_bytes:
            raise CvFileTooLargeError(raw_cv_id=raw_cv_id)

        response = None

        try:
            # Mở stream để đọc nội dung object từ MinIO.
            response = self._client.get_object(
                bucket_name=bucket,
                object_name=object_key,
            )

            # Đọc dữ liệu theo giới hạn để tránh tải file quá lớn vào memory.
            data = self._read_limited(
                stream=response,
                maximum_bytes=self._settings.max_object_size_bytes,
                raw_cv_id=raw_cv_id,
            )
        except CvFileTooLargeError:
            # Giữ nguyên domain exception khi file vượt giới hạn.
            raise
        except S3Error as exception:
            # Chuyển lỗi object không tồn tại thành domain exception.
            if exception.code in {
                "NoSuchKey",
                "NoSuchObject",
                "NoSuchBucket",
                "NotFound",
            }:
                raise CvObjectNotFoundError(
                    raw_cv_id=raw_cv_id
                ) from exception

            # Các lỗi MinIO khác được chuyển thành lỗi nội bộ.
            raise CvInternalError(raw_cv_id=raw_cv_id) from exception
        except OSError as exception:
            # Chuyển lỗi I/O trong quá trình đọc stream thành lỗi nội bộ.
            raise CvInternalError(raw_cv_id=raw_cv_id) from exception
        finally:
            # Đảm bảo response và connection được giải phóng sau khi đọc.
            if response is not None:
                response.close()
                response.release_conn()

        # Kiểm tra dữ liệu thực tế có đúng kích thước metadata hay không.
        if len(data) != stat.size:
            raise CvInternalError(raw_cv_id=raw_cv_id)

        # Trả về nội dung object cùng metadata cần thiết cho parser.
        return StoredObject(
            data=data,
            size=len(data),
            content_type=stat.content_type,
            etag=stat.etag,
        )

    def check_readiness(self) -> bool:
        # Kiểm tra MinIO bucket có tồn tại và có thể truy cập hay không.
        try:
            if not self._client.bucket_exists(
                    self._settings.minio_bucket
            ):
                return False

            # Thực hiện một request đọc metadata để kiểm tra khả năng truy cập.
            iterator = self._client.list_objects(
                self._settings.minio_bucket,
                recursive=False,
            )
            next(iterator, None)
            return True
        except Exception:
            # Readiness chỉ cần trả về trạng thái DOWN thay vì làm service crash.
            return False

    @staticmethod
    def _read_limited(
            stream: BinaryIO,
            maximum_bytes: int,
            raw_cv_id: str | None,
    ) -> bytes:
        # Đọc stream theo từng chunk để giới hạn lượng dữ liệu đưa vào memory.
        buffer = io.BytesIO()
        total = 0

        while True:
            # Đọc tối đa 64 KiB mỗi lần và không vượt quá giới hạn file.
            chunk = stream.read(
                min(
                    64 * 1024,
                    maximum_bytes + 1 - total,
                    )
            )

            if not chunk:
                break

            total += len(chunk)

            # Phát hiện file vượt giới hạn ngay trong quá trình streaming.
            if total > maximum_bytes:
                raise CvFileTooLargeError(raw_cv_id=raw_cv_id)

            buffer.write(chunk)

        # Chuyển toàn bộ dữ liệu đã đọc thành bytes.
        return buffer.getvalue()