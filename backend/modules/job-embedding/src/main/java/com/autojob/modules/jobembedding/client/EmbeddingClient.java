package com.autojob.modules.jobembedding.client;

import com.autojob.modules.jobembedding.client.dto.EmbeddingResponse;

public interface EmbeddingClient {

    EmbeddingResponse embed(String text);
}