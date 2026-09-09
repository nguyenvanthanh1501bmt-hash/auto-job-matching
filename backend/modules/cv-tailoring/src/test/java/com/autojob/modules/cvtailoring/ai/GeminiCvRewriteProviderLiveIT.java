package com.autojob.modules.cvtailoring.ai;

import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EditableNode;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.EvidenceValue;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.JobContext;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteRequest;
import com.autojob.modules.cvtailoring.ai.CvRewriteProvider.RewriteResponse;
import com.autojob.modules.cvtailoring.config.CvTailoringAiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GeminiCvRewriteProviderLiveIT {

    @Test
    @EnabledIfEnvironmentVariable(
            named = "GEMINI_API_KEY",
            matches = ".+"
    )
    void callsRealGeminiAndParsesStructuredRewriteResponse() {

        String apiKey =
                System.getenv(
                        "GEMINI_API_KEY"
                );

        String model =
                environmentOrDefault(
                        "GEMINI_MODEL",
                        "gemini-3.7-flash"
                );

        String baseUrl =
                environmentOrDefault(
                        "GEMINI_BASE_URL",
                        "https://generativelanguage.googleapis.com"
                );

        CvTailoringAiProperties properties =
                new CvTailoringAiProperties();

        properties.setEnabled(
                true
        );

        properties.setConnectTimeout(
                Duration.ofSeconds(5)
        );

        properties.setRequestTimeout(
                Duration.ofSeconds(30)
        );

        properties.setMaxOutputTokens(
                600
        );

        properties
                .getGemini()
                .setEnabled(
                        true
                );

        properties
                .getGemini()
                .setBaseUrl(
                        baseUrl
                );

        properties
                .getGemini()
                .setModel(
                        model
                );

        properties
                .getGemini()
                .setApiKey(
                        apiKey
                );

        GeminiCvRewriteProvider provider =
                new GeminiCvRewriteProvider(
                        properties,
                        new ObjectMapper()
                                .findAndRegisterModules()
                );

        RewriteRequest request =
                new RewriteRequest(
                        new JobContext(
                                "Backend Developer",

                                List.of(
                                        "Java",
                                        "Spring Boot"
                                ),

                                """
                                Strong Java and Spring Boot
                                backend development skills.
                                """.trim(),

                                """
                                Build and maintain backend
                                services and REST APIs.
                                """.trim()
                        ),

                        List.of(
                                new EditableNode(
                                        "work:0:responsibility:0",

                                        "WORK_EXPERIENCE",

                                        "Backend Developer work experience",

                                        "Built backend services.",

                                        List.of(
                                                "work:0:responsibility:0",
                                                "work:0:skill:0",
                                                "work:0:skill:1"
                                        )
                                )
                        ),

                        List.of(
                                new EvidenceValue(
                                        "work:0:responsibility:0",
                                        "TEXT",
                                        "Built backend services.",
                                        ""
                                ),

                                new EvidenceValue(
                                        "work:0:skill:0",
                                        "SKILL",
                                        "Java",
                                        "java"
                                ),

                                new EvidenceValue(
                                        "work:0:skill:1",
                                        "SKILL",
                                        "Spring Boot",
                                        "spring boot"
                                )
                        )
                );

        RewriteResponse response =
                provider.generate(
                        request
                );

        assertThat(
                response
        ).isNotNull();

        assertThat(
                response.suggestions()
        ).isNotNull();

        /*
         * Empty suggestions are valid according to the
         * provider system instruction.
         *
         * If Gemini does return suggestions, however,
         * they must stay inside the identifiers supplied
         * by this smoke test.
         */
        Set<String> allowedSourceIds =
                Set.of(
                        "work:0:responsibility:0"
                );

        Set<String> allowedEvidenceIds =
                Set.of(
                        "work:0:responsibility:0",
                        "work:0:skill:0",
                        "work:0:skill:1"
                );

        response
                .suggestions()
                .forEach(
                        suggestion -> {

                            assertThat(
                                    suggestion.sourceId()
                            ).isIn(
                                    allowedSourceIds
                                            .toArray(
                                                    String[]::new
                                            )
                            );

                            assertThat(
                                    suggestion.suggested()
                            ).isNotBlank();

                            assertThat(
                                    suggestion.evidenceIds()
                            ).allMatch(
                                    allowedEvidenceIds::contains
                            );
                        }
                );

        System.out.println(
                "Gemini live smoke PASS"
                        + " | model="
                        + model
                        + " | suggestions="
                        + response
                        .suggestions()
                        .size()
        );
    }

    private String environmentOrDefault(
            String name,
            String fallback
    ) {
        String value =
                System.getenv(
                        name
                );

        if (value == null
                || value.isBlank()) {
            return fallback;
        }

        return value.trim();
    }
}