package com.nlda.retrieval.governance;

import java.time.LocalDateTime;

public record RebuildStatus(
        String outcome,
        String reason,
        String triggerSource,
        String schemaFingerprint,
        String embeddingModel,
        String message,
        LocalDateTime createdAt
) {

    public static RebuildStatus empty() {
        return new RebuildStatus("", "", "", "", "", "", null);
    }
}
