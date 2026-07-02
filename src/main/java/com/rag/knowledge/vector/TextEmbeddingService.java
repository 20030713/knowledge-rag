package com.rag.knowledge.vector;

public interface TextEmbeddingService {

    double[] embed(String text);

    int dimensions();

    default boolean modelAvailable() {
        return false;
    }

    default String modelName() {
        return "local-hash";
    }

    default String modelNamespace() {
        return modelName() + ":" + dimensions();
    }
}
