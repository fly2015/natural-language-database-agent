package com.nlda.retrieval.governance;

import java.util.List;

public record RebuildResult(
        String outcome,
        String reason,
        String triggerSource,
        String schemaFingerprint,
        int chunkCount,
        List<String> affectedRuleIds,
        String message
) {
}
