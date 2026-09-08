package com.autojob.modules.candidateembedding.service;

import com.autojob.common.embedding.client.EmbeddingClient;
import com.autojob.common.embedding.client.dto.EmbeddingResponse;
import com.autojob.common.embedding.config.EmbeddingProperties;
import com.autojob.common.embedding.service.EmbeddingTextHashCalculator;
import com.autojob.modules.candidateembedding.config.CandidateEmbeddingProperties;
import com.autojob.modules.candidateembedding.text.CandidateEmbeddingTextBuilder;
import com.autojob.modules.cv.domain.CandidateProfile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Sinh candidate embedding từ một CandidateProfile đã có trong memory.
 *
 * Service này không đọc/ghi CandidateEmbeddingRepository, vì vậy có thể dùng
 * cho các preview cần embedding tạm thời mà không tạo fake candidate record.
 */
@Service
public class CandidateEmbeddingGenerator {

    private static final double NORMALIZATION_TOLERANCE = 1.0e-3;

    private final CandidateEmbeddingTextBuilder textBuilder;
    private final EmbeddingClient embeddingClient;
    private final EmbeddingTextHashCalculator textHashCalculator;
    private final EmbeddingProperties embeddingProperties;
    private final CandidateEmbeddingProperties candidateProperties;

    public CandidateEmbeddingGenerator(
            CandidateEmbeddingTextBuilder textBuilder,
            EmbeddingClient embeddingClient,
            EmbeddingTextHashCalculator textHashCalculator,
            EmbeddingProperties embeddingProperties,
            CandidateEmbeddingProperties candidateProperties
    ) {
        this.textBuilder = Objects.requireNonNull(
                textBuilder,
                "textBuilder must not be null"
        );

        this.embeddingClient = Objects.requireNonNull(
                embeddingClient,
                "embeddingClient must not be null"
        );

        this.textHashCalculator = Objects.requireNonNull(
                textHashCalculator,
                "textHashCalculator must not be null"
        );

        this.embeddingProperties = Objects.requireNonNull(
                embeddingProperties,
                "embeddingProperties must not be null"
        );

        this.candidateProperties = Objects.requireNonNull(
                candidateProperties,
                "candidateProperties must not be null"
        );
    }

    public GeneratedCandidateEmbedding generate(
            CandidateProfile profile
    ) {
        Objects.requireNonNull(
                profile,
                "profile must not be null"
        );

        String text =
                textBuilder.build(
                        profile
                );

        validateEmbeddingText(
                text
        );

        String textHash =
                textHashCalculator.calculate(
                        text
                );

        EmbeddingResponse response =
                embeddingClient.embed(
                        text
                );

        validateEmbeddingResponse(
                response,
                textHash
        );

        return new GeneratedCandidateEmbedding(
                List.copyOf(
                        response.vector()
                ),
                response.dimension(),
                response.modelName(),
                response.modelRevision(),
                response.embeddingVersion(),
                configuredTextVersion(),
                response.textHash(),
                Boolean.TRUE.equals(
                        response.normalized()
                )
        );
    }

    private void validateEmbeddingText(
            String text
    ) {
        if (text == null
                || text.isBlank()) {

            throw new IllegalArgumentException(
                    "Candidate embedding text must not be blank"
            );
        }
    }

    private void validateEmbeddingResponse(
            EmbeddingResponse response,
            String expectedTextHash
    ) {
        if (response == null) {

            throw new IllegalStateException(
                    "Embedding response must not be null"
            );
        }

        if (!Objects.equals(
                response.textHash(),
                expectedTextHash
        )) {

            throw new IllegalStateException(
                    "Embedding response text hash mismatch"
            );
        }

        if (response.dimension() == null
                || response.dimension()
                != embeddingProperties
                .getExpectedDimension()) {

            throw new IllegalStateException(
                    "Embedding response dimension mismatch"
            );
        }

        if (!Boolean.TRUE.equals(
                response.normalized()
        )) {

            throw new IllegalStateException(
                    "Embedding response is not normalized"
            );
        }

        if (isBlank(
                response.modelName()
        )
                || isBlank(
                response.modelRevision()
        )
                || isBlank(
                response.embeddingVersion()
        )) {

            throw new IllegalStateException(
                    "Embedding response model metadata is incomplete"
            );
        }

        if (embeddingProperties
                .hasExpectedVersion()) {

            String expectedVersion =
                    embeddingProperties
                            .getExpectedVersion()
                            .trim();

            if (!expectedVersion.equals(
                    response.embeddingVersion()
            )) {

                throw new IllegalStateException(
                        "Embedding response embeddingVersion mismatch"
                );
            }
        }

        if (!isValidNormalizedVector(
                response.vector(),
                response.dimension()
        )) {

            throw new IllegalStateException(
                    "Embedding response vector is invalid"
            );
        }
    }

    private boolean isValidNormalizedVector(
            List<Double> vector,
            int dimension
    ) {
        if (vector == null
                || vector.size()
                != dimension) {

            return false;
        }

        double sumSquares =
                0.0d;

        for (Double value : vector) {

            if (value == null
                    || !Double.isFinite(
                    value
            )) {

                return false;
            }

            sumSquares +=
                    value * value;
        }

        double norm =
                Math.sqrt(
                        sumSquares
                );

        return Double.isFinite(
                norm
        )
                && norm > 0.0d
                && Math.abs(
                norm - 1.0d
        ) <= NORMALIZATION_TOLERANCE;
    }

    private String configuredTextVersion() {

        String value =
                candidateProperties
                        .getTextVersion();

        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    "candidate embedding textVersion must not be blank"
            );
        }

        return value.trim();
    }

    private boolean isBlank(
            String value
    ) {
        return value == null
                || value.isBlank();
    }

    public record GeneratedCandidateEmbedding(
            List<Double> vector,
            int dimension,
            String modelName,
            String modelRevision,
            String embeddingVersion,
            String textVersion,
            String textHash,
            boolean normalized
    ) {

        public GeneratedCandidateEmbedding {

            vector =
                    vector == null
                            ? List.of()
                            : List.copyOf(
                            vector
                    );
        }
    }
}