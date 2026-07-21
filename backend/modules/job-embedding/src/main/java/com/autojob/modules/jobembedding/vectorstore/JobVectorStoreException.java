package com.autojob.modules.jobembedding.vectorstore;

public class JobVectorStoreException extends RuntimeException {

    public JobVectorStoreException(String message) {
        super(message);
    }

    public JobVectorStoreException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}