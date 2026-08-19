package com.nlda.api;

import java.time.LocalDateTime;
import java.util.Set;

public record AdminBusinessRuleRequest(
        String id,
        String name,
        String text,
        String owner,
        Integer version,
        LocalDateTime effectiveStart,
        LocalDateTime effectiveEnd,
        String datasourceId,
        String tenantId,
        Set<String> schemaRefs,
        Set<String> aliases
) {
}
