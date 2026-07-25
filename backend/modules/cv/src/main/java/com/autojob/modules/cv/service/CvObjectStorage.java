package com.autojob.modules.cv.service;

import com.autojob.modules.cv.config.CvStorageProperties;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CvObjectStorage {

    private final MinioClient cvMinioClient;
    private final CvStorageProperties properties;

    public void put(
            String objectKey,
            String contentType,
            byte[] bytes
    ) {
        try {
            cvMinioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(
                                    properties.getBucket()
                            )
                            .object(objectKey)
                            .data(
                                    bytes,
                                    bytes.length
                            )
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception exception) {
            throw new CvUploadException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "CV_STORAGE_UPLOAD_FAILED",
                    "Cannot store CV in object storage",
                    exception
            );
        }
    }

    public void deleteQuietly(String objectKey) {
        try {
            cvMinioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(
                                    properties.getBucket()
                            )
                            .object(objectKey)
                            .build()
            );
        } catch (Exception ignored) {
            /*
             * Best-effort compensation nếu Mongo save lỗi.
             */
        }
    }
}