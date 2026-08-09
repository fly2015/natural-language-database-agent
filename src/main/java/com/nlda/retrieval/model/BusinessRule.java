package com.nlda.retrieval.model;

import java.util.Set;

public record BusinessRule(
        String id,
        String text,
        Set<String> schemaRefs,
        Set<String> aliases
) {
}


