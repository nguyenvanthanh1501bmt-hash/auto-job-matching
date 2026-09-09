package com.autojob.modules.cvtailoring.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties(
        prefix = "autojob.cv-tailoring.ai"
)
public class CvTailoringAiProperties {

    private boolean enabled = true;

    @NotBlank
    private String promptVersion =
            "cv-rewrite-v1";

    @NotNull
    private Duration connectTimeout =
            Duration.ofSeconds(3);

    @NotNull
    private Duration requestTimeout =
            Duration.ofSeconds(18);

    @NotNull
    private Duration providerCooldown =
            Duration.ofMinutes(2);

    @NotNull
    private Duration cacheTtl =
            Duration.ofMinutes(15);

    @Min(1)
    private int maxRewriteCandidates = 6;

    @Min(1)
    private int maxEvidencePerCandidate = 10;

    @Min(80)
    private int maxEvidenceTextChars = 240;

    @Min(200)
    private int maxJobDescriptionChars = 1_200;

    @Min(200)
    private int maxJobRequirementsChars = 2_400;

    @Min(200)
    private int maxOutputTokens = 1_400;

    @Valid
    @NotNull
    private Provider gemini =
            Provider.geminiDefaults();

    @Valid
    @NotNull
    private Provider groq =
            Provider.groqDefaults();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(
            boolean enabled
    ) {
        this.enabled = enabled;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(
            String promptVersion
    ) {
        this.promptVersion = promptVersion;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(
            Duration connectTimeout
    ) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(
            Duration requestTimeout
    ) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getProviderCooldown() {
        return providerCooldown;
    }

    public void setProviderCooldown(
            Duration providerCooldown
    ) {
        this.providerCooldown = providerCooldown;
    }

    public Duration getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(
            Duration cacheTtl
    ) {
        this.cacheTtl = cacheTtl;
    }

    public int getMaxRewriteCandidates() {
        return maxRewriteCandidates;
    }

    public void setMaxRewriteCandidates(
            int maxRewriteCandidates
    ) {
        this.maxRewriteCandidates =
                maxRewriteCandidates;
    }

    public int getMaxEvidencePerCandidate() {
        return maxEvidencePerCandidate;
    }

    public void setMaxEvidencePerCandidate(
            int maxEvidencePerCandidate
    ) {
        this.maxEvidencePerCandidate =
                maxEvidencePerCandidate;
    }

    public int getMaxEvidenceTextChars() {
        return maxEvidenceTextChars;
    }

    public void setMaxEvidenceTextChars(
            int maxEvidenceTextChars
    ) {
        this.maxEvidenceTextChars =
                maxEvidenceTextChars;
    }

    public int getMaxJobDescriptionChars() {
        return maxJobDescriptionChars;
    }

    public void setMaxJobDescriptionChars(
            int maxJobDescriptionChars
    ) {
        this.maxJobDescriptionChars =
                maxJobDescriptionChars;
    }

    public int getMaxJobRequirementsChars() {
        return maxJobRequirementsChars;
    }

    public void setMaxJobRequirementsChars(
            int maxJobRequirementsChars
    ) {
        this.maxJobRequirementsChars =
                maxJobRequirementsChars;
    }

    public int getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public void setMaxOutputTokens(
            int maxOutputTokens
    ) {
        this.maxOutputTokens = maxOutputTokens;
    }

    public Provider getGemini() {
        return gemini;
    }

    public void setGemini(
            Provider gemini
    ) {
        this.gemini = gemini;
    }

    public Provider getGroq() {
        return groq;
    }

    public void setGroq(
            Provider groq
    ) {
        this.groq = groq;
    }

    public static class Provider {

        private boolean enabled = true;

        @NotBlank
        private String baseUrl;

        @NotBlank
        private String model;

        private String apiKey;

        private static Provider geminiDefaults() {
            Provider provider =
                    new Provider();

            provider.baseUrl =
                    "https://generativelanguage.googleapis.com";

            provider.model =
                    "gemini-3.8-flash";

            return provider;
        }

        private static Provider groqDefaults() {
            Provider provider =
                    new Provider();

            provider.baseUrl =
                    "https://api.groq.com/openai/v1";

            provider.model =
                    "openai/gpt-oss-120b";

            return provider;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(
                boolean enabled
        ) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(
                String baseUrl
        ) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(
                String model
        ) {
            this.model = model;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(
                String apiKey
        ) {
            this.apiKey = apiKey;
        }

        public boolean hasApiKey() {
            return apiKey != null
                    && !apiKey.isBlank();
        }

        public String normalizedBaseUrl() {
            if (baseUrl == null
                    || baseUrl.isBlank()) {
                return "";
            }

            if (baseUrl.endsWith("/")) {
                return baseUrl.substring(
                        0,
                        baseUrl.length() - 1
                );
            }

            return baseUrl;
        }
    }
}