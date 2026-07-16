package com.autojob.modules.jobnormalizer.api;

import com.autojob.modules.jobnormalizer.service.JobRenormalizationBatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/job-normalization")
@RequiredArgsConstructor
public class JobNormalizationAdminController {

    private final JobRenormalizationBatchService batchService;

    @PostMapping("/renormalize")
    public JobRenormalizationBatchService.RenormalizationBatchResponse
    renormalize(
            @RequestBody(required = false)
            JobRenormalizationBatchService
                    .RenormalizationBatchRequest request
    ) {
        return batchService.renormalize(request);
    }
}