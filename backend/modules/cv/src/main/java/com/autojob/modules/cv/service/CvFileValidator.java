package com.autojob.modules.cv.service;

import com.autojob.modules.cv.config.CvStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
@RequiredArgsConstructor
public class CvFileValidator {

    private static final byte[] PDF_SIGNATURE = {
            0x25, 0x50, 0x44, 0x46, 0x2D
    };

    private static final byte[] UTF_8_BOM = {
            (byte) 0xEF,
            (byte) 0xBB,
            (byte) 0xBF
    };

    private static final int MAX_PDF_PREFIX_BYTES = 32;

    private static final byte[] OLE_SIGNATURE = {
            (byte) 0xD0,
            (byte) 0xCF,
            0x11,
            (byte) 0xE0,
            (byte) 0xA1,
            (byte) 0xB1,
            0x1A,
            (byte) 0xE1
    };

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of(
                    "pdf",
                    "doc",
                    "docx"
            );

    private final CvStorageProperties properties;

    public ValidatedCvFile validate(
            MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw badRequest(
                    "CV_FILE_EMPTY",
                    "CV file must not be empty"
            );
        }

        long maxBytes =
                properties.getMaxFileSizeMb()
                        * 1024L
                        * 1024L;

        if (file.getSize() > maxBytes) {
            throw new CvUploadException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "CV_FILE_TOO_LARGE",
                    "CV file exceeds "
                            + properties.getMaxFileSizeMb()
                            + " MB"
            );
        }

        String originalFilename =
                file.getOriginalFilename();

        if (originalFilename == null
                || originalFilename.isBlank()) {
            throw badRequest(
                    "CV_FILENAME_MISSING",
                    "CV filename is missing"
            );
        }

        String extension =
                extensionOf(originalFilename);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw badRequest(
                    "CV_FILE_TYPE_NOT_ALLOWED",
                    "Only PDF, DOC and DOCX files are allowed"
            );
        }

        byte[] bytes;

        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new CvUploadException(
                    HttpStatus.BAD_REQUEST,
                    "CV_FILE_READ_FAILED",
                    "Cannot read uploaded CV file",
                    exception
            );
        }

        String detectedContentType =
                switch (extension) {
                    case "pdf" -> {
                        bytes = normalizePdf(bytes);

                        yield "application/pdf";
                    }

                    case "doc" -> {
                        requireSignature(
                                bytes,
                                OLE_SIGNATURE,
                                "Invalid DOC signature"
                        );

                        yield "application/msword";
                    }

                    case "docx" -> {
                        requireDocx(bytes);

                        yield "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                    }

                    default -> throw badRequest(
                            "CV_FILE_TYPE_NOT_ALLOWED",
                            "Unsupported CV file type"
                    );
                };

        return new ValidatedCvFile(
                sanitizeFilename(
                        originalFilename
                ),
                extension,
                detectedContentType,
                bytes
        );
    }

    private byte[] normalizePdf(
            byte[] bytes
    ) {
        int signatureOffset =
                findPdfSignatureOffset(bytes);

        if (signatureOffset < 0) {
            throw badRequest(
                    "CV_FILE_SIGNATURE_INVALID",
                    "Invalid PDF signature"
            );
        }

        if (signatureOffset == 0) {
            return bytes;
        }

        return Arrays.copyOfRange(
                bytes,
                signatureOffset,
                bytes.length
        );
    }

    private int findPdfSignatureOffset(
            byte[] bytes
    ) {
        if (bytes.length < PDF_SIGNATURE.length) {
            return -1;
        }

        int offset = 0;

        if (startsWith(
                bytes,
                UTF_8_BOM,
                0
        )) {
            offset = UTF_8_BOM.length;
        }

        while (offset < bytes.length
                && offset < MAX_PDF_PREFIX_BYTES
                && isAllowedPdfPrefixByte(
                bytes[offset]
        )) {
            offset++;
        }

        return startsWith(
                bytes,
                PDF_SIGNATURE,
                offset
        )
                ? offset
                : -1;
    }

    private boolean isAllowedPdfPrefixByte(
            byte value
    ) {
        return value == 0x00
                || value == 0x09
                || value == 0x0A
                || value == 0x0C
                || value == 0x0D
                || value == 0x20;
    }

    private boolean startsWith(
            byte[] bytes,
            byte[] signature,
            int offset
    ) {
        if (offset < 0
                || bytes.length - offset
                < signature.length) {
            return false;
        }

        for (int index = 0;
             index < signature.length;
             index++) {
            if (bytes[offset + index]
                    != signature[index]) {
                return false;
            }
        }

        return true;
    }

    private void requireDocx(
            byte[] bytes
    ) {
        boolean hasContentTypes = false;
        boolean hasWordDocument = false;

        try (ZipInputStream zip =
                     new ZipInputStream(
                             new ByteArrayInputStream(bytes)
                     )) {
            ZipEntry entry;
            int inspectedEntries = 0;

            while ((entry = zip.getNextEntry()) != null
                    && inspectedEntries++ < 200) {
                String name = entry.getName();

                if ("[Content_Types].xml".equals(name)) {
                    hasContentTypes = true;
                }

                if ("word/document.xml".equals(name)) {
                    hasWordDocument = true;
                }

                if (hasContentTypes
                        && hasWordDocument) {
                    return;
                }
            }
        } catch (IOException exception) {
            throw badRequest(
                    "CV_FILE_SIGNATURE_INVALID",
                    "Invalid DOCX file"
            );
        }

        if (!hasContentTypes || !hasWordDocument) {
            throw badRequest(
                    "CV_FILE_SIGNATURE_INVALID",
                    "Invalid DOCX structure"
            );
        }
    }

    private void requireSignature(
            byte[] bytes,
            byte[] signature,
            String message
    ) {
        if (!startsWith(
                bytes,
                signature,
                0
        )) {
            throw badRequest(
                    "CV_FILE_SIGNATURE_INVALID",
                    message
            );
        }
    }

    private String extensionOf(
            String filename
    ) {
        int dotIndex = filename.lastIndexOf('.');

        if (dotIndex < 0
                || dotIndex == filename.length() - 1) {
            return "";
        }

        return filename
                .substring(dotIndex + 1)
                .toLowerCase(Locale.ROOT);
    }

    private String sanitizeFilename(
            String filename
    ) {
        String normalized =
                filename.replace('\\', '/');

        String baseName = normalized
                .substring(
                        normalized.lastIndexOf('/') + 1
                )
                .replaceAll(
                        "[^a-zA-Z0-9._-]",
                        "_"
                );

        if (baseName.isBlank()) {
            return "cv";
        }

        return baseName.length() <= 180
                ? baseName
                : baseName.substring(
                baseName.length() - 180
        );
    }

    private CvUploadException badRequest(
            String code,
            String message
    ) {
        return new CvUploadException(
                HttpStatus.BAD_REQUEST,
                code,
                message
        );
    }

    public record ValidatedCvFile(
            String safeFilename,
            String extension,
            String contentType,
            byte[] bytes
    ) {
    }
}