package com.autojob.modules.jobembedding.api;

import com.autojob.modules.jobembedding.domain.JobEmbedding;
import com.autojob.modules.jobembedding.domain.JobEmbeddingStatus;
import com.autojob.modules.jobembedding.service.JobEmbeddingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
public class JobEmbeddingController {

    private final JobEmbeddingService jobEmbeddingService;

    @GetMapping("/api/job-embeddings/{normalizedJobId}")
    public JobEmbeddingResponse getLatest(
            @PathVariable("normalizedJobId")
            String normalizedJobId
    ) {
        return JobEmbeddingResponse.from(
                jobEmbeddingService.getLatest(normalizedJobId)
        );
    }

    @PostMapping(
            "/api/admin/job-embeddings/{normalizedJobId}/rebuild"
    )
    public JobEmbeddingResponse rebuild(
            @PathVariable("normalizedJobId")
            String normalizedJobId,
            @RequestParam(
                    name = "force",
                    defaultValue = "false"
            )
            boolean force
    ) {
        return JobEmbeddingResponse.from(
                jobEmbeddingService.embed(
                        normalizedJobId,
                        force
                )
        );
    }

    public record JobEmbeddingResponse(
            String normalizedJobId,
            String normalizationVersion,
            String modelName,
            String modelRevision,
            String embeddingVersion,
            String textHash,
            Integer dimension,
            Boolean normalized,
            JobEmbeddingStatus status,
            String qdrantCollection,
            String qdrantPointId,
            Instant embeddedAt,
            String lastError
    ) {
        public static JobEmbeddingResponse from(
                JobEmbedding embedding
        ) {
            return new JobEmbeddingResponse(
                    embedding.getNormalizedJobId(),
                    embedding.getNormalizationVersion(),
                    embedding.getModelName(),
                    embedding.getModelRevision(),
                    embedding.getEmbeddingVersion(),
                    embedding.getTextHash(),
                    embedding.getDimension(),
                    embedding.getNormalized(),
                    embedding.getStatus(),
                    embedding.getQdrantCollection(),
                    embedding.getQdrantPointId(),
                    embedding.getEmbeddedAt(),
                    embedding.getLastError()
            );
        }
    }
}