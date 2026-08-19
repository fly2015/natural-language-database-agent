package com.nlda.retrieval.governance;

import java.time.LocalDateTime;
import java.util.Set;

public record GovernedBusinessRule(
        String id,
        String name,
        String text,
        String owner,
        int version,
        ApprovalStatus approvalStatus,
        LocalDateTime effectiveStart,
        LocalDateTime effectiveEnd,
        String datasourceId,
        String tenantId,
        boolean active,
        Set<String> schemaRefs,
        Set<String> aliases,
        String contentHash,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean approvedActiveAndEffective(LocalDateTime now) {
        return active
                && approvalStatus == ApprovalStatus.APPROVED
                && (effectiveStart == null || !effectiveStart.isAfter(now))
                && (effectiveEnd == null || effectiveEnd.isAfter(now));
    }
}
