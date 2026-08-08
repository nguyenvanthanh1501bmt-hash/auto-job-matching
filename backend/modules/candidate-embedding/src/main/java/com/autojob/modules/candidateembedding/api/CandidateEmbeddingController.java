package com.autojob.modules.candidateembedding.api;

import com.autojob.modules.candidateembedding.domain.CandidateEmbedding;
import com.autojob.modules.candidateembedding.domain.CandidateEmbeddingStatus;
import com.autojob.modules.candidateembedding.service.CandidateEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
public class CandidateEmbeddingController {

    private final CandidateEmbeddingService candidateEmbeddingService;

    @GetMapping("/api/admin/candidate-embeddings/{candidateProfileId}")
    public CandidateEmbeddingResponse getLatest(
            @PathVariable("candidateProfileId") String candidateProfileId
    ) {
        return CandidateEmbeddingResponse.from(
                candidateEmbeddingService.getLatest(candidateProfileId)
        );
    }

    @PostMapping(
            "/api/admin/candidate-embeddings/{candidateProfileId}/rebuild"
    )
    public CandidateEmbeddingResponse rebuild(
            @PathVariable("candidateProfileId") String candidateProfileId,
            @RequestParam(name = "force", defaultValue = "false")
            boolean force
    ) {
        return CandidateEmbeddingResponse.from(
                candidateEmbeddingService.embed(
                        candidateProfileId,
                        force
                )
        );
    }

    public record CandidateEmbeddingResponse(
            String candidateProfileId,
            String rawCvId,
            String parserVersion,
            String textVersion,

            String modelName,
            String modelRevision,
            String embeddingVersion,
            String textHash,

            Integer dimension,
            Boolean normalized,
            CandidateEmbeddingStatus status,

            Instant embeddedAt,
            String lastError
    ) {

        public static CandidateEmbeddingResponse from(
                CandidateEmbedding embedding
        ) {
            return new CandidateEmbeddingResponse(
                    embedding.getCandidateProfileId(),
                    embedding.getRawCvId(),
                    embedding.getParserVersion(),
                    embedding.getTextVersion(),

                    embedding.getModelName(),
                    embedding.getModelRevision(),
                    embedding.getEmbeddingVersion(),
                    embedding.getTextHash(),

                    embedding.getDimension(),
                    embedding.getNormalized(),
                    embedding.getStatus(),

                    embedding.getEmbeddedAt(),
                    embedding.getLastError()
            );
        }
    }
}