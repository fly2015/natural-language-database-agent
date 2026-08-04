package com.metajpa.nlda.retrieval;

public record RetrievalAttempt(
        int attemptNumber,
        RetrievalMode mode,
        double confidence,
        String failureCode,
        int resultCount
) {
}
