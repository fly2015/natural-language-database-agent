package com.nlda.retrieval.model;

public record RetrievalAttempt(
        int attemptNumber,
        RetrievalMode mode,
        double confidence,
        String failureCode,
        int resultCount
) {
}


