package com.nlda.retrieval.model;

import java.util.List;

public record RetrievalContext(
        boolean proceed,
        double confidence,
        List<String> snippets,
        String reason,
        String failureCode,
        RetrievalMode finalMode,
        List<RetrievalAttempt> attempts
) {
}


