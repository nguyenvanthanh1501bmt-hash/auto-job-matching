package com.autojob.modules.jobcrawler.service;

import com.autojob.modules.jobcrawler.domain.RawJob;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class RawPayloadPurgeService {

    private final MongoTemplate mongoTemplate;

    public RawPayloadPurgeResult purgeRawPayload(String rawJobId) {
        if (rawJobId == null || rawJobId.isBlank()) {
            throw new IllegalArgumentException(
                    "rawJobId must not be blank"
            );
        }

        Instant purgedAt = Instant.now();

        Query query = Query.query(
                Criteria.where("_id").is(rawJobId)
        );

        Update update = new Update()
                .unset("rawHtml")
                .unset("rawText")
                .set("rawPayloadPurgedAt", purgedAt);

        UpdateResult updateResult = mongoTemplate.updateFirst(
                query,
                update,
                RawJob.class
        );

        RawPayloadPurgeResult result = new RawPayloadPurgeResult(
                rawJobId,
                updateResult.getMatchedCount(),
                updateResult.getModifiedCount(),
                purgedAt
        );

        log.info(
                "Purged raw payload rawJobId={}, matchedCount={}, modifiedCount={}",
                rawJobId,
                result.matchedCount(),
                result.modifiedCount()
        );

        return result;
    }
}