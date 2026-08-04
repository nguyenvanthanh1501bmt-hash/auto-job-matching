package com.autojob.modules.cv.domain;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateProfileIndexTest {

    @Test
    void shouldDeclareCandidateProfilesCollectionAndIndexes() {
        Document document = CandidateProfile.class
                .getAnnotation(Document.class);

        assertThat(document).isNotNull();
        assertThat(document.collection())
                .isEqualTo("candidate_profiles");

        CompoundIndexes compoundIndexes =
                CandidateProfile.class.getAnnotation(
                        CompoundIndexes.class
                );

        assertThat(compoundIndexes).isNotNull();
        assertThat(compoundIndexes.value()).hasSize(3);

        Map<String, CompoundIndex> indexesByName =
                Arrays.stream(compoundIndexes.value())
                        .collect(
                                Collectors.toMap(
                                        CompoundIndex::name,
                                        Function.identity()
                                )
                        );

        CompoundIndex rawCvIdIndex = indexesByName.get(
                "uk_candidate_profiles_raw_cv_id"
        );

        assertThat(rawCvIdIndex).isNotNull();
        assertThat(rawCvIdIndex.def())
                .isEqualTo("{'rawCvId': 1}");
        assertThat(rawCvIdIndex.unique()).isTrue();

        CompoundIndex ownerCreatedAtIndex =
                indexesByName.get(
                        "idx_candidate_profiles_owner_created_at"
                );

        assertThat(ownerCreatedAtIndex).isNotNull();
        assertThat(ownerCreatedAtIndex.def())
                .isEqualTo(
                        "{'ownerUserId': 1, 'createdAt': -1}"
                );
        assertThat(ownerCreatedAtIndex.unique())
                .isFalse();

        CompoundIndex parserVersionIndex =
                indexesByName.get(
                        "idx_candidate_profiles_parser_version"
                );

        assertThat(parserVersionIndex).isNotNull();
        assertThat(parserVersionIndex.def())
                .isEqualTo("{'parserVersion': 1}");
        assertThat(parserVersionIndex.unique())
                .isFalse();
    }
}