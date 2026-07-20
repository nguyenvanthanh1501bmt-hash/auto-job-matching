package com.autojob.modules.jobnormalizer.service;

import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.repository.RawJobRepository;
import com.autojob.modules.jobcrawler.domain.RawPayloadPurgeResult;
import com.autojob.modules.jobnormalizer.domain.NormalizationAction;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobRenormalizationBatchServiceTest {

    @Mock
    private RawJobRepository rawJobRepository;

    @Mock
    private JobNormalizationService jobNormalizationService;

    @Test
    void shouldIsolateFailuresAndCountPurgeFailureSeparately() {
        JobRenormalizationBatchService service =
                new JobRenormalizationBatchService(
                        rawJobRepository,
                        jobNormalizationService
                );

        RawJob rawOne = RawJob.builder()
                .id("raw-1")
                .build();

        RawJob rawTwo = RawJob.builder()
                .id("raw-2")
                .build();

        RawJob rawThree = RawJob.builder()
                .id("raw-3")
                .build();

        PageRequest pageRequest = PageRequest.of(
                0,
                3,
                Sort.by(Sort.Direction.ASC, "id")
        );

        when(rawJobRepository.findBySourceCode(
                "MOCK",
                pageRequest
        )).thenReturn(new PageImpl<>(
                List.of(rawOne, rawTwo, rawThree),
                pageRequest,
                3
        ));

        when(jobNormalizationService.getNormalizationVersion())
                .thenReturn("rule-v1");

        when(jobNormalizationService.normalizeByRawJobId(
                "raw-1",
                false
        )).thenReturn(runResult(
                "raw-1",
                NormalizationAction.CREATED,
                false
        ));

        when(jobNormalizationService.normalizeByRawJobId(
                "raw-2",
                false
        )).thenThrow(
                new IllegalStateException("normalize failed")
        );

        when(jobNormalizationService.normalizeByRawJobId(
                "raw-3",
                false
        )).thenReturn(runResult(
                "raw-3",
                NormalizationAction.UNCHANGED,
                true
        ));

        JobRenormalizationBatchService.RenormalizationBatchResponse
                response = service.renormalize(
                new JobRenormalizationBatchService
                        .RenormalizationBatchRequest(
                        "mock",
                        0,
                        3,
                        false
                )
        );

        assertThat(response.sourceCode()).isEqualTo("MOCK");
        assertThat(response.processed()).isEqualTo(3);
        assertThat(response.created()).isEqualTo(1);
        assertThat(response.updated()).isZero();
        assertThat(response.unchanged()).isEqualTo(1);
        assertThat(response.failed()).isEqualTo(1);
        assertThat(response.rawPayloadPurged()).isEqualTo(1);
        assertThat(response.purgeFailed()).isEqualTo(1);
        assertThat(response.failures()).hasSize(1);

        assertThat(response.failures().getFirst().rawJobId())
                .isEqualTo("raw-2");

        verify(jobNormalizationService)
                .normalizeByRawJobId("raw-3", false);
    }

    @Test
    void shouldUseDefaultsAndAllSourcesWhenRequestIsNull() {
        JobRenormalizationBatchService service =
                new JobRenormalizationBatchService(
                        rawJobRepository,
                        jobNormalizationService
                );

        PageRequest pageRequest = PageRequest.of(
                0,
                100,
                Sort.by(Sort.Direction.ASC, "id")
        );

        when(rawJobRepository.findAll(pageRequest))
                .thenReturn(new PageImpl<>(
                        List.of(),
                        pageRequest,
                        0
                ));

        when(jobNormalizationService.getNormalizationVersion())
                .thenReturn("rule-v1");

        JobRenormalizationBatchService.RenormalizationBatchResponse
                response = service.renormalize(null);

        assertThat(response.sourceCode()).isNull();
        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(100);
        assertThat(response.force()).isFalse();
    }

    private NormalizationRunResult runResult(
            String rawJobId,
            NormalizationAction action,
            boolean purgeFailed
    ) {
        NormalizedJob normalizedJob = NormalizedJob.builder()
                .id("normalized-" + rawJobId)
                .rawJobId(rawJobId)
                .normalizationVersion("rule-v1")
                .build();

        if (purgeFailed) {
            return new NormalizationRunResult(
                    new NormalizationExecution(
                            normalizedJob,
                            action
                    ),
                    null,
                    "purge failed"
            );
        }

        return new NormalizationRunResult(
                new NormalizationExecution(
                        normalizedJob,
                        action
                ),
                new RawPayloadPurgeResult(
                        rawJobId,
                        1,
                        1,
                        Instant.parse(
                                "2026-07-12T03:00:00Z"
                        )
                ),
                null
        );
    }
}