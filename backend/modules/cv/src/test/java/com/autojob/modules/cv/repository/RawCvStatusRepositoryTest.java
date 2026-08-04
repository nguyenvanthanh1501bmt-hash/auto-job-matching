package com.autojob.modules.cv.repository;

import com.autojob.modules.cv.domain.CvProcessingStatus;
import com.autojob.modules.cv.domain.RawCv;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RawCvStatusRepositoryTest {

    private MongoTemplate mongoTemplate;
    private UpdateResult updateResult;
    private RawCvStatusRepository repository;

    @BeforeEach
    void setUp() {
        mongoTemplate = mock(MongoTemplate.class);
        updateResult = mock(UpdateResult.class);

        when(updateResult.getModifiedCount())
                .thenReturn(1L);

        when(
                mongoTemplate.updateFirst(
                        any(Query.class),
                        any(Update.class),
                        eq(RawCv.class)
                )
        ).thenReturn(updateResult);

        repository = new RawCvStatusRepository(
                mongoTemplate
        );
    }

    @Test
    void shouldAtomicallyAcquireUploadedOrFailedCv() {
        boolean acquired =
                repository.acquireForParsing(
                        "raw-cv-001",
                        "user-001",
                        false
                );

        assertThat(acquired).isTrue();

        CapturedUpdate captured = captureUpdate();

        assertThat(
                captured.query().get("_id")
        ).isEqualTo("raw-cv-001");

        assertThat(
                captured.query().get("ownerUserId")
        ).isEqualTo("user-001");

        Document statusCriteria =
                captured.query().get(
                        "status",
                        Document.class
                );

        assertThat(statusCriteria).isNotNull();

        assertThat(
                statusCriteria.getList(
                        "$in",
                        Object.class
                )
        ).containsExactly(
                CvProcessingStatus.UPLOADED,
                CvProcessingStatus.FAILED
        );

        Document set = captured.update().get(
                "$set",
                Document.class
        );

        assertThat(set).isNotNull();
        assertThat(set.get("status"))
                .isEqualTo(
                        CvProcessingStatus.PARSING
                );

        Document unset = captured.update().get(
                "$unset",
                Document.class
        );

        assertThat(unset).isNotNull();
        assertThat(unset)
                .containsKey("lastError");
    }

    @Test
    void shouldAllowParsedOnlyForVersionUpgrade() {
        boolean acquired =
                repository.acquireForParsing(
                        "raw-cv-001",
                        "user-001",
                        true
                );

        assertThat(acquired).isTrue();

        CapturedUpdate captured = captureUpdate();

        Document statusCriteria =
                captured.query().get(
                        "status",
                        Document.class
                );

        List<Object> statuses =
                statusCriteria.getList(
                        "$in",
                        Object.class
                );

        assertThat(statuses).containsExactly(
                CvProcessingStatus.UPLOADED,
                CvProcessingStatus.FAILED,
                CvProcessingStatus.PARSED
        );
    }

    @Test
    void shouldAtomicallyMarkParsingCvAsParsed() {
        boolean updated = repository.markParsed(
                "raw-cv-001",
                "user-001"
        );

        assertThat(updated).isTrue();

        CapturedUpdate captured = captureUpdate();

        assertThat(
                captured.query().get("status")
        ).isEqualTo(
                CvProcessingStatus.PARSING
        );

        Document set = captured.update().get(
                "$set",
                Document.class
        );

        assertThat(set.get("status"))
                .isEqualTo(
                        CvProcessingStatus.PARSED
                );

        Document unset = captured.update().get(
                "$unset",
                Document.class
        );

        assertThat(unset)
                .containsKey("lastError");
    }

    @Test
    void shouldAtomicallyMarkParsingCvAsFailed() {
        boolean updated = repository.markFailed(
                "raw-cv-001",
                "user-001",
                "PARSER_RESPONSE_TIMEOUT"
        );

        assertThat(updated).isTrue();

        CapturedUpdate captured = captureUpdate();

        assertThat(
                captured.query().get("status")
        ).isEqualTo(
                CvProcessingStatus.PARSING
        );

        Document set = captured.update().get(
                "$set",
                Document.class
        );

        assertThat(set.get("status"))
                .isEqualTo(
                        CvProcessingStatus.FAILED
                );

        assertThat(set.get("lastError"))
                .isEqualTo(
                        "PARSER_RESPONSE_TIMEOUT"
                );
    }

    @Test
    void shouldReturnFalseWhenCompareAndSetDoesNotModifyDocument() {
        when(updateResult.getModifiedCount())
                .thenReturn(0L);

        boolean acquired =
                repository.acquireForParsing(
                        "raw-cv-001",
                        "user-001",
                        false
                );

        assertThat(acquired).isFalse();
    }

    private CapturedUpdate captureUpdate() {
        ArgumentCaptor<Query> queryCaptor =
                ArgumentCaptor.forClass(
                        Query.class
                );

        ArgumentCaptor<Update> updateCaptor =
                ArgumentCaptor.forClass(
                        Update.class
                );

        verify(mongoTemplate).updateFirst(
                queryCaptor.capture(),
                updateCaptor.capture(),
                eq(RawCv.class)
        );

        return new CapturedUpdate(
                queryCaptor
                        .getValue()
                        .getQueryObject(),
                updateCaptor
                        .getValue()
                        .getUpdateObject()
        );
    }

    private record CapturedUpdate(
            Document query,
            Document update
    ) {
    }
}