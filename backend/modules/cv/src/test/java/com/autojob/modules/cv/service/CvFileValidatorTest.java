package com.autojob.modules.cv.service;

import com.autojob.modules.cv.config.CvStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CvFileValidatorTest {

    private CvFileValidator validator;

    @BeforeEach
    void setUp() {
        CvStorageProperties properties =
                new CvStorageProperties();

        properties.setMaxFileSizeMb(10);

        validator = new CvFileValidator(
                properties
        );
    }

    @Test
    void shouldAcceptPdfStartingAtFirstByte() {
        byte[] source = pdfBytes(
                "%PDF-1.7\nbody"
        );

        CvFileValidator.ValidatedCvFile result =
                validator.validate(
                        pdfFile(source)
                );

        assertArrayEquals(
                source,
                result.bytes()
        );

        assertEquals(
                "application/pdf",
                result.contentType()
        );
    }

    @Test
    void shouldStripLeadingPdfWhitespaceBeforeSignature() {
        byte[] source = pdfBytes(
                "\n\r\t %PDF-1.7\nbody"
        );

        CvFileValidator.ValidatedCvFile result =
                validator.validate(
                        pdfFile(source)
                );

        assertArrayEquals(
                pdfBytes(
                        "%PDF-1.7\nbody"
                ),
                result.bytes()
        );
    }

    @Test
    void shouldStripUtf8BomAndWhitespaceBeforeSignature() {
        byte[] source = concat(
                new byte[]{
                        (byte) 0xEF,
                        (byte) 0xBB,
                        (byte) 0xBF
                },
                pdfBytes(
                        "\r\n %PDF-1.7\nbody"
                )
        );

        CvFileValidator.ValidatedCvFile result =
                validator.validate(
                        pdfFile(source)
                );

        assertArrayEquals(
                pdfBytes(
                        "%PDF-1.7\nbody"
                ),
                result.bytes()
        );
    }

    @Test
    void shouldRejectNonWhitespacePrefixBeforeSignature() {
        CvUploadException exception =
                assertThrows(
                        CvUploadException.class,
                        () -> validator.validate(
                                pdfFile(
                                        pdfBytes(
                                                "X%PDF-1.7\nbody"
                                        )
                                )
                        )
                );

        assertEquals(
                "CV_FILE_SIGNATURE_INVALID",
                exception.getCode()
        );

        assertEquals(
                "Invalid PDF signature",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectSignatureBeyondAllowedPrefix() {
        String prefix = " ".repeat(33);

        CvUploadException exception =
                assertThrows(
                        CvUploadException.class,
                        () -> validator.validate(
                                pdfFile(
                                        pdfBytes(
                                                prefix
                                                        + "%PDF-1.7\nbody"
                                        )
                                )
                        )
                );

        assertEquals(
                "CV_FILE_SIGNATURE_INVALID",
                exception.getCode()
        );
    }

    @Test
    void shouldRejectZipRenamedToPdf() {
        byte[] zipHeader = {
                0x50,
                0x4B,
                0x03,
                0x04,
                0x14,
                0x00
        };

        CvUploadException exception =
                assertThrows(
                        CvUploadException.class,
                        () -> validator.validate(
                                pdfFile(zipHeader)
                        )
                );

        assertEquals(
                "CV_FILE_SIGNATURE_INVALID",
                exception.getCode()
        );
    }

    private MockMultipartFile pdfFile(
            byte[] bytes
    ) {
        return new MockMultipartFile(
                "file",
                "candidate.pdf",
                "application/pdf",
                bytes
        );
    }

    private byte[] pdfBytes(
            String value
    ) {
        return value.getBytes(
                StandardCharsets.ISO_8859_1
        );
    }

    private byte[] concat(
            byte[] first,
            byte[] second
    ) {
        byte[] result =
                new byte[
                        first.length
                                + second.length
                        ];

        System.arraycopy(
                first,
                0,
                result,
                0,
                first.length
        );

        System.arraycopy(
                second,
                0,
                result,
                first.length,
                second.length
        );

        return result;
    }
}