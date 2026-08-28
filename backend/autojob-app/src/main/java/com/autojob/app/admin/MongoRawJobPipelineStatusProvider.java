package com.autojob.app.admin;

import com.autojob.modules.jobcrawler.controller.RawJobPipelineStatusProvider;
import com.autojob.modules.jobembedding.domain.JobEmbedding;
import com.autojob.modules.jobembedding.domain.JobEmbeddingStatus;
import com.autojob.modules.jobembedding.repository.JobEmbeddingRepository;
import com.autojob.modules.jobnormalizer.config.NormalizationProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MongoRawJobPipelineStatusProvider
        implements RawJobPipelineStatusProvider {

    private final NormalizedJobRepository normalizedJobRepository;
    private final JobEmbeddingRepository jobEmbeddingRepository;
    private final NormalizationProperties normalizationProperties;

    @Override
    public Map<String, RawJobPipelineStatus> getStatuses(
            List<String> rawJobIds
    ) {
        List<String> ids = sanitizeIds(rawJobIds);

        if (ids.isEmpty()) {
            return Map.of();
        }

        String activeNormalizationVersion =
                requireActiveVersion();

        List<NormalizedJob> normalizedJobs =
                normalizedJobRepository.findAllByRawJobIdIn(
                        ids
                );

        Map<String, NormalizedJob> selectedNormalizedByRawJobId =
                selectNormalizedJobs(
                        normalizedJobs,
                        activeNormalizationVersion
                );

        List<String> normalizedJobIds =
                selectedNormalizedByRawJobId
                        .values()
                        .stream()
                        .map(NormalizedJob::getId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();

        Map<String, JobEmbedding> latestEmbeddingByNormalizedJobId =
                selectLatestEmbeddings(
                        normalizedJobIds
                );

        Map<String, RawJobPipelineStatus> result =
                new LinkedHashMap<>();

        for (String rawJobId : ids) {
            NormalizedJob normalizedJob =
                    selectedNormalizedByRawJobId.get(
                            rawJobId
                    );

            if (normalizedJob == null) {
                result.put(
                        rawJobId,
                        RawJobPipelineStatus.empty()
                );

                continue;
            }

            PipelineStageStatus normalizationStatus =
                    Objects.equals(
                            activeNormalizationVersion,
                            normalizedJob.getNormalizationVersion()
                    )
                            ? PipelineStageStatus.READY
                            : PipelineStageStatus.OUTDATED;

            JobEmbedding embedding =
                    latestEmbeddingByNormalizedJobId.get(
                            normalizedJob.getId()
                    );

            result.put(
                    rawJobId,
                    toPipelineStatus(
                            normalizedJob,
                            normalizationStatus,
                            embedding
                    )
            );
        }

        return Map.copyOf(result);
    }

    private Map<String, NormalizedJob> selectNormalizedJobs(
            List<NormalizedJob> normalizedJobs,
            String activeNormalizationVersion
    ) {
        Comparator<NormalizedJob> comparator =
                Comparator
                        .comparing(
                                NormalizedJob::getNormalizedAt,
                                Comparator.nullsFirst(
                                        Comparator.naturalOrder()
                                )
                        )
                        .thenComparing(
                                NormalizedJob::getId,
                                Comparator.nullsFirst(
                                        Comparator.naturalOrder()
                                )
                        );

        Map<String, List<NormalizedJob>> jobsByRawJobId =
                normalizedJobs
                        .stream()
                        .filter(
                                job ->
                                        job.getRawJobId()
                                                != null
                        )
                        .collect(
                                Collectors.groupingBy(
                                        NormalizedJob::getRawJobId
                                )
                        );

        Map<String, NormalizedJob> selected =
                new HashMap<>();

        jobsByRawJobId.forEach(
                (rawJobId, jobs) -> {
                    /*
                     * Luôn ưu tiên document của version đang active.
                     * Nếu chưa có mới trả bản gần nhất để admin biết
                     * pipeline đang OUTDATED và cần renormalize.
                     */
                    NormalizedJob candidate =
                            jobs
                                    .stream()
                                    .filter(
                                            job ->
                                                    Objects.equals(
                                                            activeNormalizationVersion,
                                                            job.getNormalizationVersion()
                                                    )
                                    )
                                    .max(comparator)
                                    .orElseGet(
                                            () ->
                                                    jobs
                                                            .stream()
                                                            .max(
                                                                    comparator
                                                            )
                                                            .orElse(
                                                                    null
                                                            )
                                    );

                    if (candidate != null) {
                        selected.put(
                                rawJobId,
                                candidate
                        );
                    }
                }
        );

        return selected;
    }

    private Map<String, JobEmbedding> selectLatestEmbeddings(
            Collection<String> normalizedJobIds
    ) {
        if (normalizedJobIds.isEmpty()) {
            return Map.of();
        }

        Comparator<JobEmbedding> comparator =
                Comparator
                        .comparing(
                                JobEmbedding::getUpdatedAt,
                                Comparator.nullsFirst(
                                        Comparator.naturalOrder()
                                )
                        )
                        .thenComparing(
                                JobEmbedding::getId,
                                Comparator.nullsFirst(
                                        Comparator.naturalOrder()
                                )
                        );

        return jobEmbeddingRepository
                .findAllByNormalizedJobIdIn(
                        normalizedJobIds
                )
                .stream()
                .filter(
                        embedding ->
                                embedding.getNormalizedJobId()
                                        != null
                )
                .collect(
                        Collectors.toMap(
                                JobEmbedding::getNormalizedJobId,
                                Function.identity(),
                                (left, right) ->
                                        comparator.compare(
                                                left,
                                                right
                                        ) >= 0
                                                ? left
                                                : right,
                                HashMap::new
                        )
                );
    }

    private RawJobPipelineStatus toPipelineStatus(
            NormalizedJob normalizedJob,
            PipelineStageStatus normalizationStatus,
            JobEmbedding embedding
    ) {
        if (embedding == null) {
            return new RawJobPipelineStatus(
                    normalizationStatus,
                    normalizedJob.getId(),
                    normalizedJob.getNormalizationVersion(),
                    normalizedJob.getNormalizedAt(),

                    PipelineStageStatus.NOT_CREATED,
                    null,
                    null,
                    null,
                    null
            );
        }

        return new RawJobPipelineStatus(
                normalizationStatus,
                normalizedJob.getId(),
                normalizedJob.getNormalizationVersion(),
                normalizedJob.getNormalizedAt(),

                mapEmbeddingStatus(
                        embedding.getStatus()
                ),
                embedding.getId(),
                embedding.getEmbeddingVersion(),
                embedding.getEmbeddedAt(),
                embedding.getLastError()
        );
    }

    private PipelineStageStatus mapEmbeddingStatus(
            JobEmbeddingStatus status
    ) {
        if (status == null) {
            return PipelineStageStatus.NOT_CREATED;
        }

        return switch (status) {
            case PROCESSING ->
                    PipelineStageStatus.PROCESSING;

            case READY ->
                    PipelineStageStatus.READY;

            case FAILED ->
                    PipelineStageStatus.FAILED;
        };
    }

    private List<String> sanitizeIds(
            List<String> rawJobIds
    ) {
        if (
                rawJobIds == null ||
                rawJobIds.isEmpty()
        ) {
            return List.of();
        }

        return rawJobIds
                .stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(
                        value ->
                                !value.isEmpty()
                )
                .distinct()
                .toList();
    }

    private String requireActiveVersion() {
        String version =
                normalizationProperties.getVersion();

        if (
                version == null ||
                version.isBlank()
        ) {
            throw new IllegalStateException(
                    "Active normalization version must not be blank"
            );
        }

        return version.trim();
    }
}