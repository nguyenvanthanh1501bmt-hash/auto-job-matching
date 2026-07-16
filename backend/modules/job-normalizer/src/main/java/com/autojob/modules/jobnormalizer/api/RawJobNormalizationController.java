package com.autojob.modules.jobnormalizer.api;

import com.autojob.modules.jobnormalizer.domain.NormalizedJob;
import com.autojob.modules.jobnormalizer.service.JobNormalizationService;
import com.autojob.modules.jobnormalizer.service.NormalizationRunResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/raw-jobs")
@RequiredArgsConstructor
public class RawJobNormalizationController {

    private final JobNormalizationService jobNormalizationService;

    @PostMapping("/{rawJobId}/normalize")
    public NormalizedJobController.NormalizedJobDetailResponse normalize(
            @PathVariable("rawJobId") String rawJobId,
            @RequestParam(name = "force", defaultValue = "false")
            boolean force
    ) {
        NormalizationRunResult result =
                jobNormalizationService.normalizeByRawJobId(
                        rawJobId,
                        force
                );

        NormalizedJob normalizedJob =
                result.execution().normalizedJob();

        return NormalizedJobController
                .NormalizedJobDetailResponse
                .from(normalizedJob);
    }
}