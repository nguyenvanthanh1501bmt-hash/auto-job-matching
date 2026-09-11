package com.autojob.modules.cvtailoring.service;

import com.autojob.modules.cv.domain.CandidateProfile;
import com.autojob.modules.cv.repository.CandidateProfileRepository;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.repository.NormalizedJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CvTailoringDraftService {

    private final CvTailoringAnalysisStore
            analysisStore;

    private final CandidateProfileRepository
            candidateProfileRepository;

    private final CvEvidenceService
            cvEvidenceService;

    private final CvSuggestionValidator
            suggestionValidator;

    private final CvSuggestionApplier
            suggestionApplier;

    private final NormalizedJobRepository
            normalizedJobRepository;

    /*
     * Backward-compatible constructor for existing unit tests.
     * Production Spring wiring uses the @Autowired constructor below
     * so job-revision validation is always enabled at runtime.
     */
    public CvTailoringDraftService(
            CvTailoringAnalysisStore analysisStore,
            CandidateProfileRepository candidateProfileRepository,
            CvEvidenceService cvEvidenceService,
            CvSuggestionValidator suggestionValidator,
            CvSuggestionApplier suggestionApplier
    ) {
        this(
                analysisStore,
                candidateProfileRepository,
                cvEvidenceService,
                suggestionValidator,
                suggestionApplier,
                null
        );
    }

    @Autowired
    public CvTailoringDraftService(
            CvTailoringAnalysisStore analysisStore,
            CandidateProfileRepository candidateProfileRepository,
            CvEvidenceService cvEvidenceService,
            CvSuggestionValidator suggestionValidator,
            CvSuggestionApplier suggestionApplier,
            NormalizedJobRepository normalizedJobRepository
    ) {
        this.analysisStore =
                Objects.requireNonNull(
                        analysisStore,
                        "analysisStore must not be null"
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

        this.suggestionApplier =
                Objects.requireNonNull(
                        suggestionApplier,
                        "suggestionApplier must not be null"
                );

        this.normalizedJobRepository =
                normalizedJobRepository;
    }

    public TemporaryDraft createTemporaryDraft(
            String analysisId,
            String candidateProfileId,
            String normalizedJobId,
            String ownerUserId,
            List<String> acceptedSuggestionIds
    ) {
        /*
         * 1. analysisId phải thuộc đúng:
         *
         * user
         * candidate
         * job
         */
        CvTailoringAnalysisStore.AnalysisContext context =
                analysisStore.require(
                        analysisId,
                        ownerUserId,
                        candidateProfileId,
                        normalizedJobId
                );

        /*
         * 2. Luôn load CandidateProfile thật từ DB.
         */
        CandidateProfile profile =
                candidateProfileRepository
                        .findById(
                                candidateProfileId
                        )
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND,
                                                "Candidate profile was not found"
                                        )
                        );

        /*
         * Defense-in-depth ownership check.
         */
        if (!ownerUserId.equals(
                profile.getOwnerUserId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Candidate profile was not found"
            );
        }

        /*
         * 3. Nếu CV đã được parse/update lại sau Analyze
         * thì sourceId/evidenceIds cũ không còn đáng tin.
         */
        analysisStore
                .assertProfileUnchanged(
                        context,
                        profile
                );

        /*
         * Suggestions were generated from one exact normalized-job
         * revision. Do not let the client preview stale suggestions
         * against a JD that was re-normalized after Analyze.
         *
         * The null branch only exists for the backward-compatible
         * constructor used by older unit tests. Spring production
         * wiring always provides the repository.
         */
        if (normalizedJobRepository != null) {
            NormalizedJob currentJob =
                    normalizedJobRepository
                            .findById(
                                    normalizedJobId
                            )
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.CONFLICT,
                                                    "Selected job is no longer available. Analyze again before previewing."
                                            )
                            );

            analysisStore
                    .assertJobUnchanged(
                            context,
                            currentJob
                    );
        }

        /*
         * 4. Client chỉ được gửi ID.
         *
         * Không nhận:
         *
         * suggested text
         * sourceId
         * evidenceIds
         * modified CandidateProfile
         */
        List<SuggestionItem> selected =
                selectAcceptedSuggestions(
                        context.suggestions(),
                        acceptedSuggestionIds
                );

        /*
         * 5. Build evidence LẠI từ CandidateProfile thật.
         *
         * Không blindly trust EvidenceMap trong session.
         */
        CvEvidenceService.EvidenceMap freshEvidence =
                cvEvidenceService.build(
                        profile
                );

        /*
         * 6. Validate LẠI ngay trước apply.
         */
        List<SuggestionItem> revalidated =
                suggestionValidator
                        .validateAll(
                                profile,
                                freshEvidence,
                                selected
                        );

        /*
         * 7. Apply lên COPY.
         *
         * CandidateProfile Mongo gốc không bị mutate.
         */
        CandidateProfile temporaryProfile =
                suggestionApplier.apply(
                        profile,
                        revalidated,
                        freshEvidence
                );

        return new TemporaryDraft(
                context,
                profile,
                temporaryProfile,
                revalidated
                        .stream()
                        .map(
                                SuggestionItem::id
                        )
                        .toList()
        );
    }

    private List<SuggestionItem>
    selectAcceptedSuggestions(
            List<SuggestionItem> availableSuggestions,
            List<String> acceptedSuggestionIds
    ) {
        if (acceptedSuggestionIds == null
                || acceptedSuggestionIds.isEmpty()) {

            return List.of();
        }

        Map<String, SuggestionItem> byId =
                availableSuggestions
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        SuggestionItem::id,
                                        Function.identity()
                                )
                        );

        Set<String> seen =
                new LinkedHashSet<>();

        List<SuggestionItem> selected =
                new ArrayList<>();

        for (
                String suggestionId
                : acceptedSuggestionIds
        ) {

            if (suggestionId == null
                    || suggestionId.isBlank()) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "acceptedSuggestionIds must not "
                                + "contain blank values"
                );
            }

            if (!seen.add(
                    suggestionId
            )) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "acceptedSuggestionIds must not "
                                + "contain duplicates"
                );
            }

            SuggestionItem suggestion =
                    byId.get(
                            suggestionId
                    );

            if (suggestion == null) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Unknown or unvalidated suggestion id: "
                                + suggestionId
                );
            }

            selected.add(
                    suggestion
            );
        }

        return List.copyOf(
                selected
        );
    }

    /*
     * Không expose record này ra REST.
     *
     * Phase 3 sẽ consume internal service này
     * rồi generate temporary embedding.
     */
    public record TemporaryDraft(
            CvTailoringAnalysisStore.AnalysisContext analysis,
            CandidateProfile originalProfile,
            CandidateProfile temporaryProfile,
            List<String> appliedSuggestionIds
    ) {

        public TemporaryDraft {
            appliedSuggestionIds =
                    appliedSuggestionIds == null
                            ? List.of()
                            : List.copyOf(
                            appliedSuggestionIds
                    );
        }
    }
}