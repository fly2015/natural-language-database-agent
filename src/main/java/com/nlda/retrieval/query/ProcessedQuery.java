package com.nlda.retrieval.query;

import java.util.List;
import java.util.Set;

public record ProcessedQuery(
        String original,
        String normalized,
        List<String> tokens,
        List<TermCorrection> correctedTerms,
        Set<String> aliases,
        String retrievalQuery,
        boolean ambiguous,
        double correctionConfidence
) {
}
