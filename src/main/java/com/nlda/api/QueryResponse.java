package com.nlda.api;

import com.nlda.format.TableResult;

import java.util.List;

public record QueryResponse(
        String status,
        String answer,
        TableResult table,
        String sql,
        String traceId,
        long latencyMs,
        List<String> assumptions,
        String reason
) {
}
