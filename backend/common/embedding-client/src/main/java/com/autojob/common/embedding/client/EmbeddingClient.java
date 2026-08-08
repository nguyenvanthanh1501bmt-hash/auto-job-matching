package com.autojob.common.embedding.client;

import com.autojob.common.embedding.client.dto.EmbeddingResponse;

public interface EmbeddingClient {

    EmbeddingResponse embed(String text);
}