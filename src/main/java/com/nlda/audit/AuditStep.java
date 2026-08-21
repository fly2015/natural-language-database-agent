package com.nlda.audit;

import java.util.Map;

public record AuditStep(
        int sequence,
        String name,
        String implementationClass,
        String status,
        long durationMs,
        Map<String, Object> input,
        Map<String, Object> output
) {
}
