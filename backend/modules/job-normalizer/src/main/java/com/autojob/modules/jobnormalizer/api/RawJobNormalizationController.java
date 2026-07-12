package com.autojob.modules.jobnormalizer.api;

import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.service.JobNormalizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/raw-jobs")
@RequiredArgsConstructor
public class RawJobNormalizationController {

    private final JobNormalizationService jobNormalizationService;

    @PostMapping("/{rawJobId}/normalize")
    public NormalizedJobController.NormalizedJobDetailResponse normalize(
            @PathVariable("rawJobId") String rawJobId
    ) {
        NormalizedJob normalizedJob =
                jobNormalizationService.normalizeByRawJobId(rawJobId);

        return NormalizedJobController
                .NormalizedJobDetailResponse
                .from(normalizedJob);
    }
}