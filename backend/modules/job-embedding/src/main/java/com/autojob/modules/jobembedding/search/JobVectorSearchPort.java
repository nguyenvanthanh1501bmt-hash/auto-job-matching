package com.autojob.modules.jobembedding.search;

import java.util.List;

public interface JobVectorSearchPort {

    List<JobVectorHit> search(
            List<Double> queryVector,
            JobVectorSearchCriteria criteria
    );
}