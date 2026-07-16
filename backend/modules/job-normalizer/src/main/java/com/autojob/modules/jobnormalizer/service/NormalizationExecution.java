package com.autojob.modules.jobnormalizer.service;

import com.autojob.modules.jobnormalizer.domain.NormalizationAction;
import com.autojob.modules.jobnormalizer.domain.NormalizedJob;

public record NormalizationExecution(
        NormalizedJob normalizedJob,
        NormalizationAction action
) {
}