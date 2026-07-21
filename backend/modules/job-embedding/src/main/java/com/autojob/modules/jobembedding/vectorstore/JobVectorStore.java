package com.autojob.modules.jobembedding.vectorstore;

public interface JobVectorStore {

    void ensureCollection();

    void upsert(JobVectorPoint point);

    boolean pointExists(String pointId);
}