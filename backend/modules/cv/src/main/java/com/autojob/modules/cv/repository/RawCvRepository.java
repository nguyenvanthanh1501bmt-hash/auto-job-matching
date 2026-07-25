package com.autojob.modules.cv.repository;

import com.autojob.modules.cv.domain.RawCv;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RawCvRepository
        extends MongoRepository<RawCv, String> {
}