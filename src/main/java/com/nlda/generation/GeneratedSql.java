package com.nlda.generation;

import java.util.List;

public record GeneratedSql(
        String status,
        String sql,
        List<String> assumptions,
        String reason
) {
    public static GeneratedSql ok(String sql, List<String> assumptions) {
        return new GeneratedSql("OK", sql, List.copyOf(assumptions), "");
    }

    public static GeneratedSql rejected(String reason) {
        return new GeneratedSql("REJECTED", null, List.of(), reason);
    }
}
