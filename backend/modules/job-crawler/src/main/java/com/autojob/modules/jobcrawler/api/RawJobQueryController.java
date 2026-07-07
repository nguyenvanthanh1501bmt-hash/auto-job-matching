package com.autojob.modules.jobcrawler.api;

import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.domain.RawJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/raw-jobs")
@RequiredArgsConstructor
public class RawJobQueryController {

    private final RawJobRepository rawJobRepository;

    @GetMapping
    public List<RawJob> list(@RequestParam(name = "limit", defaultValue = "20") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 100);

        return rawJobRepository
                .findAll(PageRequest.of(
                        0,
                        safeLimit,
                        Sort.by(Sort.Direction.DESC, "collectedAt")
                ))
                .getContent();
    }
}