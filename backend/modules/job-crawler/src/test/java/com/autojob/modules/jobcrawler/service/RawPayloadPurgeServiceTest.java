package com.autojob.modules.jobcrawler.service;

import com.autojob.modules.jobcrawler.domain.RawJob;
import com.autojob.modules.jobcrawler.domain.RawPayloadPurgeResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RawPayloadPurgeServiceTest {

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void shouldUnsetOnlyPayloadAndKeepDocumentRetentionFields() {
        RawPayloadPurgeService service = new RawPayloadPurgeService(
                mongoTemplate
        );

        when(mongoTemplate.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(RawJob.class)
        )).thenReturn(
                UpdateResult.acknowledged(
                        1,
                        1L,
                        null
                )
        );

        RawPayloadPurgeResult result = service.purgeRawPayload(
                "raw-1"
        );

        assertThat(result.rawJobId()).isEqualTo("raw-1");
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.modifiedCount()).isEqualTo(1);
        assertThat(result.purgedAt()).isNotNull();

        ArgumentCaptor<Query> queryCaptor =
                ArgumentCaptor.forClass(Query.class);

        ArgumentCaptor<Update> updateCaptor =
                ArgumentCaptor.forClass(Update.class);

        verify(mongoTemplate).updateFirst(
                queryCaptor.capture(),
                updateCaptor.capture(),
                eq(RawJob.class)
        );

        assertThat(queryCaptor.getValue().getQueryObject())
                .containsEntry("_id", "raw-1");

        Document updateDocument = updateCaptor
                .getValue()
                .getUpdateObject();

        Document unsetDocument = updateDocument.get(
                "$unset",
                Document.class
        );

        Document setDocument = updateDocument.get(
                "$set",
                Document.class
        );

        assertThat(unsetDocument).isNotNull();
        assertThat(setDocument).isNotNull();

        assertThat(updateDocument.keySet())
                .containsExactlyInAnyOrder(
                        "$unset",
                        "$set"
                );

        assertThat(unsetDocument.keySet())
                .containsExactlyInAnyOrder(
                        "rawHtml",
                        "rawText"
                );

        assertThat(setDocument.keySet())
                .containsExactly(
                        "rawPayloadPurgedAt"
                );

        assertThat(unsetDocument.keySet())
                .doesNotContain(
                        "expiresAt",
                        "firstSeenAt",
                        "lastSeenAt",
                        "collectedAt",
                        "descriptionText",
                        "requirementsText",
                        "benefitsText"
                );

        assertThat(setDocument.keySet())
                .doesNotContain(
                        "expiresAt",
                        "firstSeenAt",
                        "lastSeenAt",
                        "collectedAt",
                        "descriptionText",
                        "requirementsText",
                        "benefitsText"
                );
    }

    @Test
    void shouldBeIdempotentWhenPayloadWasAlreadyUnset() {
        RawPayloadPurgeService service = new RawPayloadPurgeService(
                mongoTemplate
        );

        when(mongoTemplate.updateFirst(
                any(Query.class),
                any(Update.class),
                eq(RawJob.class)
        )).thenReturn(
                UpdateResult.acknowledged(
                        1,
                        0L,
                        null
                )
        );

        RawPayloadPurgeResult result = service.purgeRawPayload(
                "raw-1"
        );

        assertThat(result.rawJobId()).isEqualTo("raw-1");
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.modifiedCount()).isZero();
        assertThat(result.purgedAt()).isNotNull();
    }
}