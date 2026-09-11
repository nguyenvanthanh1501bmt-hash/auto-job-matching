package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CurrentMatch;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.EvidenceItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.GapItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.JobSnapshot;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.Section;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionType;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import com.autojob.modules.matching.contract.MatchingRunResult;
import com.autojob.modules.matching.domain.MatchResult;
import com.autojob.modules.matching.service.HybridMatchingService;
import com.autojob.modules.matching.service.MatchingPreconditionException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class CvTailoringAnalysisService {

    private final HybridMatchingService hybridMatchingService;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CvEvidenceService cvEvidenceService;
    private final CvSuggestionValidator suggestionValidator;
    private final CvTailoringAnalysisStore analysisStore;
    private final NormalizedJobRepository normalizedJobRepository;
    private final CvSuggestionService cvSuggestionService;

    public CvTailoringAnalysisService(
            HybridMatchingService hybridMatchingService,
            CandidateProfileRepository candidateProfileRepository,
            CvEvidenceService cvEvidenceService,
            CvSuggestionValidator suggestionValidator,
            CvTailoringAnalysisStore analysisStore,
            NormalizedJobRepository normalizedJobRepository,
            CvSuggestionService cvSuggestionService
    ) {
        this.hybridMatchingService =
                Objects.requireNonNull(
                        hybridMatchingService,
                        "hybridMatchingService must not be null"
                );

        this.candidateProfileRepository =
                Objects.requireNonNull(
                        candidateProfileRepository,
                        "candidateProfileRepository must not be null"
                );

        this.cvEvidenceService =
                Objects.requireNonNull(
                        cvEvidenceService,
                        "cvEvidenceService must not be null"
                );

        this.suggestionValidator =
                Objects.requireNonNull(
                        suggestionValidator,
                        "suggestionValidator must not be null"
                );

        this.analysisStore =
                Objects.requireNonNull(
                        analysisStore,
                        "analysisStore must not be null"
                );

        this.normalizedJobRepository =
                Objects.requireNonNull(
                        normalizedJobRepository,
                        "normalizedJobRepository must not be null"
                );

        this.cvSuggestionService =
                Objects.requireNonNull(
                        cvSuggestionService,
                        "cvSuggestionService must not be null"
                );
    }

    public CvTailoringAnalyzeResponse analyze(
            String candidateProfileId,
            String normalizedJobId,
            String ownerUserId
    ) {
        requireText(candidateProfileId, "candidateProfileId");
        requireText(normalizedJobId, "normalizedJobId");

        MatchingRunResult currentRun =
                loadCurrentMatchingRun(
                        candidateProfileId,
                        ownerUserId
                );

        MatchResult targetMatch =
                currentRun.results()
                        .stream()
                        .filter(Objects::nonNull)
                        .filter(
                                result ->
                                        normalizedJobId.equals(
                                                result.getNormalizedJobId()
                                        )
                        )
                        .findFirst()
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Selected job is not part of "
                                                + "the current matching result. "
                                                + "Run matching again before "
                                                + "tailoring this CV."
                                )
                        );

        CandidateProfile profile =
                candidateProfileRepository
                        .findById(candidateProfileId)
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Candidate profile changed while "
                                                + "CV tailoring analysis "
                                                + "was starting"
                                )
                        );

        if (ownerUserId == null
                || ownerUserId.isBlank()
                || !ownerUserId.equals(profile.getOwnerUserId())) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Candidate profile was not found"
            );
        }

        CvEvidenceService.EvidenceMap evidenceMap =
                cvEvidenceService.build(profile);

        NormalizedJob targetJob =
                normalizedJobRepository
                        .findById(normalizedJobId)
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.CONFLICT,
                                        "Selected job is no longer available. "
                                                + "Run matching again before "
                                                + "tailoring this CV."
                                )
                        );

        List<SuggestionItem> generatedSuggestions =
                new ArrayList<>(
                        buildEmphasizeSuggestions(
                                targetMatch,
                                evidenceMap
                        )
                );

        /*
         * Groq = primary.
         * Gemini = fallback.
         *
         * Provider order vẫn do Spring @Order quyết định.
         */
        generatedSuggestions.addAll(
                cvSuggestionService.generateRewrites(
                        profile,
                        targetJob,
                        targetMatch,
                        evidenceMap
                )
        );

        List<SuggestionItem> validatedSuggestions =
                suggestionValidator.validateAll(
                        profile,
                        evidenceMap,
                        generatedSuggestions
                );

        List<GapItem> gaps =
                buildGapWarnings(targetMatch);

        CvTailoringAnalysisStore.AnalysisContext context;

        /*
         * Fresh production matching results có generatedAt,
         * và NormalizedJob mới có revision metadata.
         *
         * Khi metadata tồn tại -> dùng strict context mới.
         *
         * Legacy documents / test fixtures cũ có thể thiếu cả
         * generatedAt lẫn job revision. Trong trường hợp đó,
         * giữ backward-compatible context thay vì trả 409.
         */
        boolean hasRunOrJobRevisionMetadata =
                targetMatch.getGeneratedAt() != null
                        || (
                        targetJob.getRawContentHash() != null
                                && !targetJob
                                .getRawContentHash()
                                .isBlank()
                )
                        || targetJob.getNormalizedAt() != null;

        if (hasRunOrJobRevisionMetadata) {
            context =
                    analysisStore.save(
                            ownerUserId,
                            profile,
                            normalizedJobId,
                            currentRun.candidateEmbeddingId(),
                            currentRun.rankingVersion(),
                            targetMatch.getGeneratedAt(),
                            targetJob.getRawContentHash(),
                            targetJob.getNormalizedAt(),
                            evidenceMap.items(),
                            validatedSuggestions
                    );
        } else {
            context =
                    analysisStore.save(
                            ownerUserId,
                            profile,
                            normalizedJobId,
                            currentRun.candidateEmbeddingId(),
                            currentRun.rankingVersion(),
                            evidenceMap.items(),
                            validatedSuggestions
                    );
        }

        return new CvTailoringAnalyzeResponse(
                context.analysisId(),
                context.expiresAt(),
                candidateProfileId,
                normalizedJobId,
                toJobSnapshot(targetMatch),
                toCurrentMatch(
                        currentRun,
                        targetMatch
                ),
                evidenceMap.items(),
                validatedSuggestions,
                gaps
        );
    }

    private MatchingRunResult loadCurrentMatchingRun(
            String candidateProfileId,
            String ownerUserId
    ) {
        try {
            return hybridMatchingService.getCurrent(
                    candidateProfileId,
                    ownerUserId
            );
        } catch (MatchingPreconditionException exception) {
            throw toHttpException(exception);
        }
    }

    private ResponseStatusException toHttpException(
            MatchingPreconditionException exception
    ) {
        HttpStatus status =
                switch (exception.getReason()) {
                    case AUTHENTICATION_REQUIRED ->
                            HttpStatus.UNAUTHORIZED;

                    case CANDIDATE_PROFILE_NOT_FOUND,
                         MATCH_RESULT_NOT_FOUND ->
                            HttpStatus.NOT_FOUND;

                    case READY_CANDIDATE_EMBEDDING_NOT_FOUND,
                         CANDIDATE_EMBEDDING_STALE,
                         CANDIDATE_EMBEDDING_INVALID ->
                            HttpStatus.CONFLICT;
                };

        return new ResponseStatusException(
                status,
                exception.getMessage(),
                exception
        );
    }

    private List<SuggestionItem> buildEmphasizeSuggestions(
            MatchResult targetMatch,
            CvEvidenceService.EvidenceMap evidenceMap
    ) {
        List<String> matchedSkills =
                safeList(targetMatch.getMatchedSkills());

        if (matchedSkills.isEmpty()) {
            return List.of();
        }

        Set<String> topLevelSkills =
                evidenceMap.topLevelSkillKeys();

        Set<String> emittedSkills =
                new LinkedHashSet<>();

        List<SuggestionItem> result =
                new ArrayList<>();

        for (String matchedSkill : matchedSkills) {
            String canonicalSkill =
                    cvEvidenceService.canonicalSkillKey(
                            matchedSkill
                    );

            if (canonicalSkill.isBlank()
                    || topLevelSkills.contains(canonicalSkill)
                    || !emittedSkills.add(canonicalSkill)) {

                continue;
            }

            List<EvidenceItem> supportingEvidence =
                    evidenceMap
                            .findSurfaceableProfessionalEvidence(
                                    canonicalSkill
                            );

            if (supportingEvidence.isEmpty()) {
                continue;
            }

            List<String> evidenceIds =
                    supportingEvidence
                            .stream()
                            .map(EvidenceItem::id)
                            .distinct()
                            .toList();

            result.add(
                    new SuggestionItem(
                            "emphasize-" + result.size(),
                            SuggestionType.EMPHASIZE,
                            Section.SKILLS,
                            "skills",
                            null,
                            matchedSkill,
                            buildEmphasizeReason(
                                    supportingEvidence
                            ),
                            List.of(matchedSkill),
                            evidenceIds
                    )
            );
        }

        return List.copyOf(result);
    }

    private String buildEmphasizeReason(
            List<EvidenceItem> evidence
    ) {
        boolean hasWorkEvidence =
                evidence.stream()
                        .anyMatch(
                                item ->
                                        item.section()
                                                == Section.WORK_EXPERIENCE
                        );

        boolean hasProjectEvidence =
                evidence.stream()
                        .anyMatch(
                                item ->
                                        item.section()
                                                == Section.PROJECT
                        );

        if (hasWorkEvidence && hasProjectEvidence) {
            return "This matched skill already has "
                    + "supporting evidence in Work Experience "
                    + "and Projects but is not surfaced in "
                    + "the top-level Skills section.";
        }

        if (hasWorkEvidence) {
            return "This matched skill already has "
                    + "supporting evidence in Work Experience "
                    + "but is not surfaced in the "
                    + "top-level Skills section.";
        }

        return "This matched skill already has "
                + "supporting evidence in Projects "
                + "but is not surfaced in the "
                + "top-level Skills section.";
    }

    private List<GapItem> buildGapWarnings(
            MatchResult targetMatch
    ) {
        List<String> missingSkills =
                safeList(targetMatch.getMissingSkills());

        if (missingSkills.isEmpty()) {
            return List.of();
        }

        Set<String> seen =
                new LinkedHashSet<>();

        List<GapItem> result =
                new ArrayList<>();

        for (String missingSkill : missingSkills) {
            if (missingSkill == null
                    || missingSkill.isBlank()) {

                continue;
            }

            String canonicalSkill =
                    cvEvidenceService.canonicalSkillKey(
                            missingSkill
                    );

            String dedupeKey =
                    canonicalSkill.isBlank()
                            ? missingSkill.trim()
                            : canonicalSkill;

            if (!seen.add(dedupeKey)) {
                continue;
            }

            result.add(
                    new GapItem(
                            "gap-" + result.size(),
                            SuggestionType.GAP_WARNING,
                            missingSkill,
                            "This skill is relevant to "
                                    + "the selected job, but the "
                                    + "current matching engine found "
                                    + "no supporting professional "
                                    + "skill evidence. It will not "
                                    + "be added automatically."
                    )
            );
        }

        return List.copyOf(result);
    }

    private JobSnapshot toJobSnapshot(
            MatchResult targetMatch
    ) {
        return new JobSnapshot(
                targetMatch.getNormalizedJobId(),
                targetMatch.getRank(),
                targetMatch.getJobTitle(),
                targetMatch.getCompanyName(),
                targetMatch.getLocationText(),
                targetMatch.getSalaryText(),
                targetMatch.getDetailUrl(),
                targetMatch.getApplyUrl()
        );
    }

    private CurrentMatch toCurrentMatch(
            MatchingRunResult currentRun,
            MatchResult targetMatch
    ) {
        return new CurrentMatch(
                currentRun.rankingVersion(),
                targetMatch.getFinalScore(),
                targetMatch.getSemanticScore(),
                targetMatch.getSkillScore(),
                targetMatch.getSeniorityScore(),
                targetMatch.getLocationScore(),
                targetMatch.getFreshnessScore(),
                Boolean.TRUE.equals(
                        targetMatch.getSkillKnown()
                ),
                Boolean.TRUE.equals(
                        targetMatch.getSeniorityKnown()
                ),
                Boolean.TRUE.equals(
                        targetMatch.getLocationKnown()
                ),
                Boolean.TRUE.equals(
                        targetMatch.getFreshnessKnown()
                ),
                targetMatch.getMatchedSkills(),
                targetMatch.getMissingSkills()
        );
    }

    private List<String> safeList(
            List<String> values
    ) {
        return values == null
                ? List.of()
                : values;
    }

    private void requireText(
            String value,
            String fieldName
    ) {
        if (value == null
                || value.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    fieldName + " must not be blank"
            );
        }
    }
}