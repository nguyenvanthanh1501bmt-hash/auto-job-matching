package com.autojob.modules.jobembedding.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JobEmbeddingPointIdFactoryTest {

    private final JobEmbeddingPointIdFactory factory =
            new JobEmbeddingPointIdFactory();

    @Test
    void shouldCreateDeterministicUuid() {
        String first = factory.create(
                "normalized-001",
                "model@revision|prep-v1|l2"
        );

        String second = factory.create(
                "normalized-001",
                "model@revision|prep-v1|l2"
        );

        assertThat(first).isEqualTo(second);
        assertThatCodeIsUuid(first);
    }

    @Test
    void shouldChangeIdForDifferentVersion() {
        String first = factory.create(
                "normalized-001",
                "model@revision-1|prep-v1|l2"
        );

        String second = factory.create(
                "normalized-001",
                "model@revision-2|prep-v1|l2"
        );

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldChangeIdForDifferentJob() {
        String first = factory.create(
                "normalized-001",
                "model@revision|prep-v1|l2"
        );

        String second = factory.create(
                "normalized-002",
                "model@revision|prep-v1|l2"
        );

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldRejectBlankInput() {
        assertThatThrownBy(
                () -> factory.create(
                        " ",
                        "model@revision|prep-v1|l2"
                )
        ).isInstanceOf(
                IllegalArgumentException.class
        );
    }

    private void assertThatCodeIsUuid(String value) {
        UUID uuid = UUID.fromString(value);

        assertThat(uuid.version()).isEqualTo(5);
        assertThat(uuid.variant()).isEqualTo(2);
    }
}