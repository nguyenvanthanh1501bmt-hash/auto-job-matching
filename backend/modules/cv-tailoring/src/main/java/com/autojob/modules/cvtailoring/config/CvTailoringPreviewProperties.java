package com.autojob.modules.cvtailoring.config;

import jakarta.validation.constraints.DecimalMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(
        prefix = "autojob.cv-tailoring.preview"
)
public class CvTailoringPreviewProperties {

    /**
     * Production safety gate.
     *
     * Khi bật, preview thử từng suggestion và chỉ giữ
     * suggestion không làm giảm final score của job
     * đang được tailor.
     */
    private boolean preventScoreRegression = true;

    /**
     * Floating-point tolerance.
     *
     * Score nằm trong khoảng 0..1.
     *
     * 0.000001 tương đương 0.0001 percentage point.
     */
    @DecimalMin("0.0")
    private double scoreEpsilon = 0.000001d;

    public boolean isPreventScoreRegression() {
        return preventScoreRegression;
    }

    public void setPreventScoreRegression(
            boolean preventScoreRegression
    ) {
        this.preventScoreRegression =
                preventScoreRegression;
    }

    public double getScoreEpsilon() {
        return scoreEpsilon;
    }

    public void setScoreEpsilon(
            double scoreEpsilon
    ) {
        this.scoreEpsilon =
                scoreEpsilon;
    }
}