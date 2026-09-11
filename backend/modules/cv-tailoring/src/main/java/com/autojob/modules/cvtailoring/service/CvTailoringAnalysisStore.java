package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CvTailoringAnalysisStore {

    /*
     * MVP:
     *
     * Không persist TailoredCvDraft.
     * Analysis chỉ sống trong memory.
     */
    private static final Duration TTL =
            Duration.ofMinutes(
                    30
            );

    private final Map<String, AnalysisContext>
            contexts =
            new ConcurrentHashMap<>();

    private final Clock clock;

    public CvTailoringAnalysisStore(
            Clock clock
    ) {
        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock must not be null"
                );
    }

    /*
     * Backward-compatible save path used by older tests/callers.
     *
     * New production code should use the overload that also binds
     * the analysis to the matching-run timestamp and job revision.
     */
    public AnalysisContext save(
            String ownerUserId,
            CandidateProfile profile,
            String normalizedJobId,
            String candidateEmbeddingId,
            String rankingVersion,
            List<EvidenceItem> evidence,
            List<SuggestionItem> suggestions
    ) {
        return save(
                ownerUserId,
                profile,
                normalizedJobId,
                candidateEmbeddingId,
                rankingVersion,
                null,
                null,
                null,
                evidence,
                suggestions
        );
    }

    public AnalysisContext save(
            String ownerUserId,
            CandidateProfile profile,
            String normalizedJobId,
            String candidateEmbeddingId,
            String rankingVersion,
            Instant matchingGeneratedAt,
            String jobRawContentHash,
            Instant jobNormalizedAt,
            List<EvidenceItem> evidence,
            List<SuggestionItem> suggestions
    ) {
        requireText(
                ownerUserId,
                "ownerUserId"
        );

        Objects.requireNonNull(
                profile,
                "profile must not be null"
        );

        requireText(
                profile.getId(),
                "profile.id"
        );

        requireText(
                normalizedJobId,
                "normalizedJobId"
        );

        requireText(
                candidateEmbeddingId,
                "candidateEmbeddingId"
        );

        requireText(
                rankingVersion,
                "rankingVersion"
        );

        removeExpired();

        Instant createdAt =
                Instant.now(
                        clock
                );

        String analysisId =
                UUID
                        .randomUUID()
                        .toString();

        AnalysisContext context =
                new AnalysisContext(
                        analysisId,
                        ownerUserId,
                        profile.getId(),
                        normalizedJobId,
                        candidateEmbeddingId,
                        rankingVersion,
                        matchingGeneratedAt,
                        jobRawContentHash,
                        jobNormalizedAt,
                        profile.getUpdatedAt(),
                        profile.getParserVersion(),
                        profile.getSourceSha256(),
                        createdAt,
                        createdAt.plus(
                                TTL
                        ),
                        /*
                         * Preview rebuilds evidence from the current
                         * CandidateProfile before revalidation, so the
                         * analysis session does not need to retain CV
                         * evidence text/PII in memory. Keep the field
                         * only for backward binary/source compatibility.
                         */
                        List.of(),
                        suggestions
                );

        contexts.put(
                analysisId,
                context
        );

        return context;
    }

    public AnalysisContext require(
            String analysisId,
            String ownerUserId,
            String candidateProfileId,
            String normalizedJobId
    ) {
        requireText(
                analysisId,
                "analysisId"
        );

        requireText(
                ownerUserId,
                "ownerUserId"
        );

        requireText(
                candidateProfileId,
                "candidateProfileId"
        );

        requireText(
                normalizedJobId,
                "normalizedJobId"
        );

        removeExpired();

        AnalysisContext context =
                contexts.get(
                        analysisId
                );

        /*
         * Trả cùng NOT_FOUND cho:
         *
         * - analysis không tồn tại
         * - hết hạn
         * - sai user
         * - sai candidate
         * - sai job
         *
         * Không leak existence của analysis khác.
         */
        if (context == null
                || !ownerUserId.equals(
                context.ownerUserId()
        )
                || !candidateProfileId.equals(
                context.candidateProfileId()
        )
                || !normalizedJobId.equals(
                context.normalizedJobId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "CV tailoring analysis was not found "
                            + "or has expired"
            );
        }

        return context;
    }

    public void assertProfileUnchanged(
            AnalysisContext context,
            CandidateProfile profile
    ) {
        Objects.requireNonNull(
                context,
                "context must not be null"
        );

        Objects.requireNonNull(
                profile,
                "profile must not be null"
        );

        boolean unchanged =
                Objects.equals(
                        context.candidateProfileId(),
                        profile.getId()
                )
                        && Objects.equals(
                        context.profileUpdatedAt(),
                        profile.getUpdatedAt()
                )
                        && Objects.equals(
                        context.parserVersion(),
                        profile.getParserVersion()
                )
                        && Objects.equals(
                        context.sourceSha256(),
                        profile.getSourceSha256()
                );

        if (!unchanged) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Candidate profile changed after "
                            + "CV tailoring analysis. "
                            + "Analyze again before applying suggestions."
            );
        }
    }

    public void assertJobUnchanged(
            AnalysisContext context,
            NormalizedJob job
    ) {
        Objects.requireNonNull(
                context,
                "context must not be null"
        );

        Objects.requireNonNull(
                job,
                "job must not be null"
        );

        if (!Objects.equals(
                context.normalizedJobId(),
                job.getId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Selected job changed after CV tailoring analysis. "
                            + "Analyze again before previewing."
            );
        }

        /*
         * Legacy contexts created before job-revision binding do not
         * have either value. Keep them readable instead of turning a
         * rolling deployment into an immediate hard failure.
         */
        boolean hasRevisionSnapshot =
                context.jobRawContentHash() != null
                        || context.jobNormalizedAt() != null;

        if (!hasRevisionSnapshot) {
            return;
        }

        boolean unchanged =
                Objects.equals(
                        context.jobRawContentHash(),
                        job.getRawContentHash()
                )
                        && Objects.equals(
                        context.jobNormalizedAt(),
                        job.getNormalizedAt()
                );

        if (!unchanged) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Selected job changed after CV tailoring analysis. "
                            + "Analyze again before previewing."
            );
        }
    }

    private void removeExpired() {
        Instant now =
                Instant.now(
                        clock
                );

        contexts
                .entrySet()
                .removeIf(
                        entry ->
                                !entry
                                        .getValue()
                                        .expiresAt()
                                        .isAfter(
                                                now
                                        )
                );
    }

    private void requireText(
            String value,
            String fieldName
    ) {
        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    fieldName
                            + " must not be blank"
            );
        }
    }

    public record AnalysisContext(
            String analysisId,
            String ownerUserId,
            String candidateProfileId,
            String normalizedJobId,

            /*
             * Bind analysis với matching run tại thời điểm Analyze.
             *
             * candidateEmbeddingId + rankingVersion alone are not
             * enough because a force matching run can regenerate the
             * same logical key. matchingGeneratedAt closes that gap
             * for newly created analyses.
             */
            String candidateEmbeddingId,
            String rankingVersion,
            Instant matchingGeneratedAt,

            /*
             * Bind AI suggestions với exact revision của JD mà model
             * đã đọc. Nếu normalized job thay đổi, preview phải yêu
             * cầu Analyze lại thay vì áp suggestion cũ lên JD mới.
             */
            String jobRawContentHash,
            Instant jobNormalizedAt,

            /*
             * Candidate version snapshot.
             */
            Instant profileUpdatedAt,
            String parserVersion,
            String sourceSha256,

            Instant createdAt,
            Instant expiresAt,

            List<EvidenceItem> evidence,
            List<SuggestionItem> suggestions
    ) {

        /*
         * Backward-compatible constructor for existing tests and any
         * in-process callers compiled against the previous shape.
         */
        public AnalysisContext(
                String analysisId,
                String ownerUserId,
                String candidateProfileId,
                String normalizedJobId,
                String candidateEmbeddingId,
                String rankingVersion,
                Instant profileUpdatedAt,
                String parserVersion,
                String sourceSha256,
                Instant createdAt,
                Instant expiresAt,
                List<EvidenceItem> evidence,
                List<SuggestionItem> suggestions
        ) {
            this(
                    analysisId,
                    ownerUserId,
                    candidateProfileId,
                    normalizedJobId,
                    candidateEmbeddingId,
                    rankingVersion,
                    null,
                    null,
                    null,
                    profileUpdatedAt,
                    parserVersion,
                    sourceSha256,
                    createdAt,
                    expiresAt,
                    evidence,
                    suggestions
            );
        }

        public AnalysisContext {
            evidence =
                    evidence == null
                            ? List.of()
                            : List.copyOf(
                            evidence
                    );

            suggestions =
                    suggestions == null
                            ? List.of()
                            : List.copyOf(
                            suggestions
                    );
        }
    }
}