package com.autojob.modules.cv.repository;

import com.autojob.modules.cv.domain.CvProcessingStatus;
import com.autojob.modules.cv.domain.RawCv;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
@RequiredArgsConstructor
public class RawCvStatusRepository {

    private final MongoTemplate mongoTemplate;

    /**
     * Atomic compare-and-set acquisition.
     *
     * Standard transitions:
     * UPLOADED -> PARSING
     * FAILED   -> PARSING
     *
     * PARSED -> PARSING is permitted only when the service has already
     * established that the stored CandidateProfile parserVersion is older
     * than the configured expected parser version.
     */
    public boolean acquireForParsing(
            String rawCvId,
            String ownerUserId,
            boolean allowParsedVersionUpgrade
    ) {
        Objects.requireNonNull(rawCvId, "rawCvId");
        Objects.requireNonNull(ownerUserId, "ownerUserId");

        List<CvProcessingStatus> allowedStatuses =
                new ArrayList<>(List.of(
                        CvProcessingStatus.UPLOADED,
                        CvProcessingStatus.FAILED
                ));

        if (allowParsedVersionUpgrade) {
            allowedStatuses.add(
                    CvProcessingStatus.PARSED
            );
        }

        Query query = Query.query(
                Criteria.where("_id")
                        .is(rawCvId)
                        .and("ownerUserId")
                        .is(ownerUserId)
                        .and("status")
                        .in(allowedStatuses)
        );

        Update update = new Update()
                .set(
                        "status",
                        CvProcessingStatus.PARSING
                )
                .unset("lastError");

        return mongoTemplate.updateFirst(
                        query,
                        update,
                        RawCv.class
                )
                .getModifiedCount() == 1;
    }

    /**
     * Atomic PARSING -> PARSED transition.
     */
    public boolean markParsed(
            String rawCvId,
            String ownerUserId
    ) {
        Objects.requireNonNull(rawCvId, "rawCvId");
        Objects.requireNonNull(ownerUserId, "ownerUserId");

        Query query = Query.query(
                Criteria.where("_id")
                        .is(rawCvId)
                        .and("ownerUserId")
                        .is(ownerUserId)
                        .and("status")
                        .is(CvProcessingStatus.PARSING)
        );

        Update update = new Update()
                .set(
                        "status",
                        CvProcessingStatus.PARSED
                )
                .unset("lastError");

        return mongoTemplate.updateFirst(
                        query,
                        update,
                        RawCv.class
                )
                .getModifiedCount() == 1;
    }

    /**
     * Atomic PARSING -> FAILED transition.
     */
    public boolean markFailed(
            String rawCvId,
            String ownerUserId,
            String sanitizedLastError
    ) {
        Objects.requireNonNull(rawCvId, "rawCvId");
        Objects.requireNonNull(ownerUserId, "ownerUserId");
        Objects.requireNonNull(
                sanitizedLastError,
                "sanitizedLastError"
        );

        Query query = Query.query(
                Criteria.where("_id")
                        .is(rawCvId)
                        .and("ownerUserId")
                        .is(ownerUserId)
                        .and("status")
                        .is(CvProcessingStatus.PARSING)
        );

        Update update = new Update()
                .set(
                        "status",
                        CvProcessingStatus.FAILED
                )
                .set(
                        "lastError",
                        sanitizedLastError
                );

        return mongoTemplate.updateFirst(
                        query,
                        update,
                        RawCv.class
                )
                .getModifiedCount() == 1;
    }
}