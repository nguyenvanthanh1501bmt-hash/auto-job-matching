package com.autojob.modules.jobembedding.service;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Component
public class JobEmbeddingPointIdFactory {

    private static final UUID NAMESPACE = UUID.fromString(
            "754e01b4-5c42-5eeb-8ef6-553ff86e932d"
    );

    public String create(
            String normalizedJobId,
            String embeddingVersion
    ) {
        requireText(normalizedJobId, "normalizedJobId");
        requireText(embeddingVersion, "embeddingVersion");

        String name = normalizedJobId
                + ":"
                + embeddingVersion;

        return uuidV5(
                NAMESPACE,
                name
        ).toString();
    }

    private UUID uuidV5(
            UUID namespace,
            String name
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-1");

            digest.update(uuidToBytes(namespace));
            digest.update(
                    name.getBytes(StandardCharsets.UTF_8)
            );

            byte[] hash = digest.digest();

            hash[6] &= 0x0f;
            hash[6] |= 0x50;

            hash[8] &= 0x3f;
            hash[8] |= (byte) 0x80;

            ByteBuffer buffer = ByteBuffer.wrap(hash);

            return new UUID(
                    buffer.getLong(),
                    buffer.getLong()
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-1 algorithm is unavailable",
                    exception
            );
        }
    }

    private byte[] uuidToBytes(UUID uuid) {
        return ByteBuffer.allocate(16)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    private void requireText(
            String value,
            String fieldName
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    fieldName + " must not be blank"
            );
        }
    }
}