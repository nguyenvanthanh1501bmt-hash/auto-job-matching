package com.autojob.modules.jobnormalizer.service;

import com.autojob.common.events.JobNormalizedReadyEvent;
import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.repository.RawJobRepository;
import com.autojob.modules.jobnormalizer.config.NormalizationProperties;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.domain.NormalizedJobType;
import com.autojob.modules.jobnormalizer.domain.SeniorityLevel;
import com.autojob.modules.jobnormalizer.exception.RawJobNotFoundException;
import com.autojob.modules.jobnormalizer.normalization.ApplyInformationNormalizer;
import com.autojob.modules.jobnormalizer.normalization.DateNormalizer;
import com.autojob.modules.jobnormalizer.normalization.ExperienceNormalizationResult;
import com.autojob.modules.jobnormalizer.normalization.ExperienceNormalizer;
import com.autojob.modules.jobnormalizer.normalization.JobTypeNormalizer;
import com.autojob.modules.jobnormalizer.normalization.LocationNormalizer;
import com.autojob.modules.jobnormalizer.normalization.RawJobContentHasher;
import com.autojob.modules.jobnormalizer.normalization.SalaryNormalizationResult;
import com.autojob.modules.jobnormalizer.normalization.SalaryNormalizer;
import com.autojob.modules.jobnormalizer.normalization.SeniorityNormalizer;
import com.autojob.modules.jobnormalizer.normalization.SkillNormalizer;
import com.autojob.modules.jobnormalizer.normalization.TextNormalizer;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobNormalizationService {

    private final RawJobRepository rawJobRepository;
    private final NormalizedJobRepository normalizedJobRepository;

    private final TextNormalizer textNormalizer;
    private final SalaryNormalizer salaryNormalizer;
    private final SkillNormalizer skillNormalizer;
    private final LocationNormalizer locationNormalizer;
    private final ExperienceNormalizer experienceNormalizer;
    private final SeniorityNormalizer seniorityNormalizer;
    private final JobTypeNormalizer jobTypeNormalizer;
    private final DateNormalizer dateNormalizer;
    private final ApplyInformationNormalizer applyInformationNormalizer;
    private final RawJobContentHasher rawJobContentHasher;

    private final NormalizationProperties normalizationProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock normalizationClock;

    public NormalizedJob normalizeByRawJobId(String rawJobId) {
        validateRawJobId(rawJobId);

        long startedAtNanos = System.nanoTime();

        try {
            RawJob rawJob = rawJobRepository.findById(rawJobId)
                    .orElseThrow(
                            () -> new RawJobNotFoundException(rawJobId)
                    );

            String normalizationVersion =
                    textNormalizer.normalizeInline(
                            normalizationProperties.getVersion()
                    );

            if (normalizationVersion == null) {
                throw new IllegalStateException(
                        "Normalization version must not be blank"
                );
            }

            Instant normalizedAt =
                    Instant.now(normalizationClock);

            NormalizedJob normalizedCandidate = normalize(
                    rawJob,
                    normalizationVersion,
                    normalizedAt
            );

            NormalizedJob saved =
                    saveIdempotently(normalizedCandidate);

            eventPublisher.publishEvent(
                    new JobNormalizedReadyEvent(
                            saved.getId(),
                            saved.getRawJobId(),
                            saved.getSourceCode(),
                            saved.getNormalizationVersion(),
                            Instant.now(normalizationClock)
                    )
            );

            log.info(
                    "Normalized raw job rawJobId={}, normalizedJobId={}, "
                            + "sourceCode={}, normalizationVersion={}, "
                            + "durationMs={}, status=SUCCESS",
                    saved.getRawJobId(),
                    saved.getId(),
                    saved.getSourceCode(),
                    saved.getNormalizationVersion(),
                    elapsedMilliseconds(startedAtNanos)
            );

            return saved;
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to normalize raw job rawJobId={}, "
                            + "durationMs={}, status=FAILED, error={}",
                    rawJobId,
                    elapsedMilliseconds(startedAtNanos),
                    exception.getMessage(),
                    exception
            );

            throw exception;
        }
    }

    private NormalizedJob normalize(
            RawJob rawJob,
            String normalizationVersion,
            Instant normalizedAt
    ) {
        String title =
                textNormalizer.normalizeInline(rawJob.getTitle());

        String companyName =
                textNormalizer.normalizeInline(
                        rawJob.getCompanyName()
                );

        String salaryText =
                textNormalizer.normalizeInline(
                        rawJob.getSalaryText()
                );

        SalaryNormalizationResult salary =
                salaryNormalizer.normalize(
                        rawJob.getSalaryText()
                );

        String locationText =
                textNormalizer.normalizeInline(
                        rawJob.getLocationText()
                );

        List<String> locations =
                locationNormalizer.normalize(
                        rawJob.getLocationText()
                );

        List<String> skills =
                skillNormalizer.normalize(
                        rawJob.getSkills()
                );

        ExperienceNormalizationResult experience =
                experienceNormalizer.normalize(
                        rawJob.getExperienceText()
                );

        SeniorityLevel seniority =
                seniorityNormalizer.normalize(
                        rawJob.getSeniorityText(),
                        title,
                        experience
                );

        NormalizedJobType jobType =
                jobTypeNormalizer.normalize(
                        rawJob.getJobTypeText(),
                        title
                );

        String descriptionText =
                textNormalizer.normalizeMultiline(
                        rawJob.getDescriptionText()
                );

        String requirementsText =
                textNormalizer.normalizeMultiline(
                        rawJob.getRequirementsText()
                );

        String benefitsText =
                textNormalizer.normalizeMultiline(
                        rawJob.getBenefitsText()
                );

        String detailUrl =
                textNormalizer.normalizeInline(
                        rawJob.getDetailUrl()
                );

        ApplyInformationNormalizer.ApplyInformationResult
                applyInformation =
                applyInformationNormalizer.normalize(
                        rawJob.getApplyUrl(),
                        rawJob.getApplyType(),
                        detailUrl
                );

        return NormalizedJob.builder()
                .rawJobId(rawJob.getId())
                .sourceCode(
                        textNormalizer.normalizeInline(
                                rawJob.getSourceCode()
                        )
                )
                .sourceJobId(
                        textNormalizer.normalizeInline(
                                rawJob.getSourceJobId()
                        )
                )
                .sourceFingerprint(
                        textNormalizer.normalizeInline(
                                rawJob.getFingerprint()
                        )
                )
                .rawContentHash(
                        rawJobContentHasher.hash(rawJob)
                )
                .title(title)
                .companyName(companyName)
                .skills(skills)
                .locations(locations)
                .locationText(locationText)
                .salaryText(salaryText)
                .salaryMin(salary.min())
                .salaryMax(salary.max())
                .currency(salary.currency())
                .experienceMin(experience.min())
                .experienceMax(experience.max())
                .seniority(seniority)
                .jobType(jobType)
                .descriptionText(descriptionText)
                .requirementsText(requirementsText)
                .benefitsText(benefitsText)
                .detailUrl(detailUrl)
                .applyUrl(applyInformation.applyUrl())
                .applyType(applyInformation.applyType())

                /*
                 * Hôm nay chưa triển khai embedding.
                 */
                .embeddingText(null)

                .normalizationVersion(normalizationVersion)
                .postedAt(
                        dateNormalizer.normalizePostedAt(
                                rawJob.getPostedText()
                        )
                )
                .deadlineAt(
                        dateNormalizer.normalizeDeadlineAt(
                                rawJob.getDeadlineText()
                        )
                )
                .normalizedAt(normalizedAt)
                .build();
    }

    private NormalizedJob saveIdempotently(
            NormalizedJob candidate
    ) {
        return normalizedJobRepository
                .findByRawJobIdAndNormalizationVersion(
                        candidate.getRawJobId(),
                        candidate.getNormalizationVersion()
                )
                .map(existing ->
                        updateAndSave(existing, candidate))
                .orElseGet(() ->
                        insertSafely(candidate));
    }

    private NormalizedJob insertSafely(
            NormalizedJob candidate
    ) {
        try {
            return normalizedJobRepository.save(candidate);
        } catch (DuplicateKeyException exception) {
            NormalizedJob existing =
                    normalizedJobRepository
                            .findByRawJobIdAndNormalizationVersion(
                                    candidate.getRawJobId(),
                                    candidate
                                            .getNormalizationVersion()
                            )
                            .orElseThrow(() -> exception);

            return updateAndSave(existing, candidate);
        }
    }

    private NormalizedJob updateAndSave(
            NormalizedJob existing,
            NormalizedJob candidate
    ) {
        existing.setRawJobId(candidate.getRawJobId());
        existing.setSourceCode(candidate.getSourceCode());
        existing.setSourceJobId(candidate.getSourceJobId());
        existing.setSourceFingerprint(
                candidate.getSourceFingerprint()
        );
        existing.setRawContentHash(
                candidate.getRawContentHash()
        );

        existing.setTitle(candidate.getTitle());
        existing.setCompanyName(candidate.getCompanyName());
        existing.setSkills(candidate.getSkills());
        existing.setLocations(candidate.getLocations());
        existing.setLocationText(candidate.getLocationText());

        existing.setSalaryText(candidate.getSalaryText());
        existing.setSalaryMin(candidate.getSalaryMin());
        existing.setSalaryMax(candidate.getSalaryMax());
        existing.setCurrency(candidate.getCurrency());

        existing.setExperienceMin(
                candidate.getExperienceMin()
        );
        existing.setExperienceMax(
                candidate.getExperienceMax()
        );
        existing.setSeniority(candidate.getSeniority());
        existing.setJobType(candidate.getJobType());

        existing.setDescriptionText(
                candidate.getDescriptionText()
        );
        existing.setRequirementsText(
                candidate.getRequirementsText()
        );
        existing.setBenefitsText(
                candidate.getBenefitsText()
        );

        existing.setDetailUrl(candidate.getDetailUrl());
        existing.setApplyUrl(candidate.getApplyUrl());
        existing.setApplyType(candidate.getApplyType());

        existing.setEmbeddingText(null);

        existing.setNormalizationVersion(
                candidate.getNormalizationVersion()
        );
        existing.setPostedAt(candidate.getPostedAt());
        existing.setDeadlineAt(candidate.getDeadlineAt());
        existing.setNormalizedAt(
                candidate.getNormalizedAt()
        );

        return normalizedJobRepository.save(existing);
    }

    private void validateRawJobId(String rawJobId) {
        if (rawJobId == null || rawJobId.isBlank()) {
            throw new IllegalArgumentException(
                    "rawJobId must not be blank"
            );
        }
    }

    private long elapsedMilliseconds(
            long startedAtNanos
    ) {
        return (System.nanoTime() - startedAtNanos)
                / 1_000_000L;
    }
}