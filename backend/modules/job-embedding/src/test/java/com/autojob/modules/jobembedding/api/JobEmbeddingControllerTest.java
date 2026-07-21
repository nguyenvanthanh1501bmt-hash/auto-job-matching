package com.autojob.modules.jobembedding.api;

import com.autojob.modules.jobembedding.domain.JobEmbedding;
import com.autojob.modules.jobembedding.domain.JobEmbeddingStatus;
import com.autojob.modules.jobembedding.service.JobEmbeddingNotFoundException;
import com.autojob.modules.jobembedding.service.JobEmbeddingProcessingException;
import com.autojob.modules.jobembedding.service.JobEmbeddingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JobEmbeddingControllerTest {

    private JobEmbeddingService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(JobEmbeddingService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new JobEmbeddingController(service)
                )
                .setControllerAdvice(
                        new JobEmbeddingExceptionHandler()
                )
                .build();
    }

    @Test
    void shouldReturnLatestEmbeddingWithoutVector()
            throws Exception {
        when(service.getLatest("normalized-001"))
                .thenReturn(readyEmbedding());

        mockMvc.perform(
                        get(
                                "/api/job-embeddings/{normalizedJobId}",
                                "normalized-001"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.normalizedJobId")
                                .value("normalized-001")
                )
                .andExpect(
                        jsonPath("$.normalizationVersion")
                                .value("rule-v1")
                )
                .andExpect(
                        jsonPath("$.modelName")
                                .value("test-model")
                )
                .andExpect(
                        jsonPath("$.modelRevision")
                                .value("revision-1")
                )
                .andExpect(
                        jsonPath("$.embeddingVersion")
                                .value(
                                        "test-model@revision-1|prep-v1|l2"
                                )
                )
                .andExpect(
                        jsonPath("$.dimension").value(384)
                )
                .andExpect(
                        jsonPath("$.normalized").value(true)
                )
                .andExpect(
                        jsonPath("$.status").value("READY")
                )
                .andExpect(
                        jsonPath("$.qdrantCollection")
                                .value("job_vectors_v1")
                )
                .andExpect(
                        jsonPath("$.vector").doesNotExist()
                );
    }

    @Test
    void shouldRebuildWithForceTrue()
            throws Exception {
        when(service.embed("normalized-001", true))
                .thenReturn(readyEmbedding());

        mockMvc.perform(
                        post(
                                "/api/admin/job-embeddings/{normalizedJobId}/rebuild",
                                "normalized-001"
                        ).queryParam("force", "true")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status").value("READY")
                );

        verify(service).embed(
                "normalized-001",
                true
        );
    }

    @Test
    void shouldReturn404WhenEmbeddingDoesNotExist()
            throws Exception {
        when(service.getLatest("missing"))
                .thenThrow(
                        new JobEmbeddingNotFoundException(
                                "missing"
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/job-embeddings/{normalizedJobId}",
                                "missing"
                        )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.error")
                                .value(
                                        "JOB_EMBEDDING_NOT_FOUND"
                                )
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/api/job-embeddings/missing"
                                )
                );
    }

    @Test
    void shouldReturn503WhenRebuildFails()
            throws Exception {
        when(service.embed("normalized-001", true))
                .thenThrow(
                        new JobEmbeddingProcessingException(
                                "normalized-001",
                                "embedding unavailable",
                                new IllegalStateException(
                                        "embedding unavailable"
                                )
                        )
                );

        mockMvc.perform(
                        post(
                                "/api/admin/job-embeddings/{normalizedJobId}/rebuild",
                                "normalized-001"
                        ).queryParam("force", "true")
                )
                .andExpect(
                        status().isServiceUnavailable()
                )
                .andExpect(
                        jsonPath("$.error")
                                .value(
                                        "JOB_EMBEDDING_FAILED"
                                )
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "embedding unavailable"
                                )
                );
    }

    private JobEmbedding readyEmbedding() {
        return JobEmbedding.builder()
                .id("embedding-001")
                .normalizedJobId("normalized-001")
                .normalizationVersion("rule-v1")
                .modelName("test-model")
                .modelRevision("revision-1")
                .embeddingVersion(
                        "test-model@revision-1|prep-v1|l2"
                )
                .textHash("a".repeat(64))
                .dimension(384)
                .normalized(true)
                .qdrantCollection("job_vectors_v1")
                .qdrantPointId(
                        "13f4a274-a0f2-5d77-bf84-4c65bf870dac"
                )
                .status(JobEmbeddingStatus.READY)
                .embeddedAt(
                        Instant.parse(
                                "2026-07-21T04:00:00Z"
                        )
                )
                .createdAt(
                        Instant.parse(
                                "2026-07-21T03:59:00Z"
                        )
                )
                .updatedAt(
                        Instant.parse(
                                "2026-07-21T04:00:00Z"
                        )
                )
                .build();
    }
}