package com.metajpa.nlda.guardrail;

import java.util.List;

public record GuardrailResult(
        boolean allowed,
        String sql,
        List<String> violations
) {
    public static GuardrailResult deny(List<String> violations) {
        return new GuardrailResult(false, null, violations);
    }
}
