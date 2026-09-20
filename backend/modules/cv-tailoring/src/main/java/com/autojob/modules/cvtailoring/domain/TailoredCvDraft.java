package com.autojob.modules.cvtailoring.domain;

import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CoachingItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.CurrentMatch;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.GapItem;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.JobSnapshot;
import com.autojob.modules.cvtailoring.contract.CvTailoringAnalyzeResponse.SuggestionItem;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "tailored_cv_drafts")
@CompoundIndex(
        name = "tailored_cv_draft_owner_candidate_job_updated_idx",
        def = "{'ownerUserId': 1, 'candidateProfileId': 1, 'normalizedJobId': 1, 'updatedAt': -1}"
)
public class TailoredCvDraft {

    @Id
    private String id;

    private String ownerUserId;
    private String candidateProfileId;
    private String normalizedJobId;

    private String analysisId;
    private Instant analysisExpiresAt;

    private JobSnapshot job;
    private CurrentMatch baselineMatch;

    private List<SuggestionItem> suggestions = List.of();
    private List<CoachingItem> coaching = List.of();
    private List<GapItem> gaps = List.of();

    private List<GeneratedCoachingSuggestion> generatedCoachingSuggestions =
            List.of();

    private List<String> acceptedSuggestionIds = List.of();
    private List<String> rejectedSuggestionIds = List.of();
    private Map<String, String> coachingAnswers = Map.of();

    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastAnalyzedAt;

    @Version
    private Long version;

    public TailoredCvDraft() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getCandidateProfileId() {
        return candidateProfileId;
    }

    public void setCandidateProfileId(String candidateProfileId) {
        this.candidateProfileId = candidateProfileId;
    }

    public String getNormalizedJobId() {
        return normalizedJobId;
    }

    public void setNormalizedJobId(String normalizedJobId) {
        this.normalizedJobId = normalizedJobId;
    }

    public String getAnalysisId() {
        return analysisId;
    }

    public void setAnalysisId(String analysisId) {
        this.analysisId = analysisId;
    }

    public Instant getAnalysisExpiresAt() {
        return analysisExpiresAt;
    }

    public void setAnalysisExpiresAt(Instant analysisExpiresAt) {
        this.analysisExpiresAt = analysisExpiresAt;
    }

    public JobSnapshot getJob() {
        return job;
    }

    public void setJob(JobSnapshot job) {
        this.job = job;
    }

    public CurrentMatch getBaselineMatch() {
        return baselineMatch;
    }

    public void setBaselineMatch(CurrentMatch baselineMatch) {
        this.baselineMatch = baselineMatch;
    }

    public List<SuggestionItem> getSuggestions() {
        return suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public void setSuggestions(List<SuggestionItem> suggestions) {
        this.suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public List<CoachingItem> getCoaching() {
        return coaching == null ? List.of() : List.copyOf(coaching);
    }

    public void setCoaching(List<CoachingItem> coaching) {
        this.coaching = coaching == null ? List.of() : List.copyOf(coaching);
    }

    public List<GapItem> getGaps() {
        return gaps == null ? List.of() : List.copyOf(gaps);
    }

    public void setGaps(List<GapItem> gaps) {
        this.gaps = gaps == null ? List.of() : List.copyOf(gaps);
    }

    public List<GeneratedCoachingSuggestion> getGeneratedCoachingSuggestions() {
        return generatedCoachingSuggestions == null
                ? List.of()
                : List.copyOf(generatedCoachingSuggestions);
    }

    public void setGeneratedCoachingSuggestions(
            List<GeneratedCoachingSuggestion> generatedCoachingSuggestions
    ) {
        this.generatedCoachingSuggestions = generatedCoachingSuggestions == null
                ? List.of()
                : List.copyOf(generatedCoachingSuggestions);
    }

    public List<String> getAcceptedSuggestionIds() {
        return acceptedSuggestionIds == null
                ? List.of()
                : List.copyOf(acceptedSuggestionIds);
    }

    public void setAcceptedSuggestionIds(List<String> acceptedSuggestionIds) {
        this.acceptedSuggestionIds = acceptedSuggestionIds == null
                ? List.of()
                : List.copyOf(acceptedSuggestionIds);
    }

    public List<String> getRejectedSuggestionIds() {
        return rejectedSuggestionIds == null
                ? List.of()
                : List.copyOf(rejectedSuggestionIds);
    }

    public void setRejectedSuggestionIds(List<String> rejectedSuggestionIds) {
        this.rejectedSuggestionIds = rejectedSuggestionIds == null
                ? List.of()
                : List.copyOf(rejectedSuggestionIds);
    }

    public Map<String, String> getCoachingAnswers() {
        return coachingAnswers == null
                ? Map.of()
                : Map.copyOf(coachingAnswers);
    }

    public void setCoachingAnswers(Map<String, String> coachingAnswers) {
        if (coachingAnswers == null || coachingAnswers.isEmpty()) {
            this.coachingAnswers = Map.of();
            return;
        }

        this.coachingAnswers = Map.copyOf(new LinkedHashMap<>(coachingAnswers));
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getLastAnalyzedAt() {
        return lastAnalyzedAt;
    }

    public void setLastAnalyzedAt(Instant lastAnalyzedAt) {
        this.lastAnalyzedAt = lastAnalyzedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public record GeneratedCoachingSuggestion(
            String coachingId,
            String answerHash,
            String userEvidenceId,
            SuggestionItem suggestion,
            Instant generatedAt
    ) {
    }
}