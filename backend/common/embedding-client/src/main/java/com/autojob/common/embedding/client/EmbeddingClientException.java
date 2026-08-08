package com.autojob.common.embedding.client;

public class EmbeddingClientException extends RuntimeException {

    public EmbeddingClientException(String message) {
        super(message);
    }

    public EmbeddingClientException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}