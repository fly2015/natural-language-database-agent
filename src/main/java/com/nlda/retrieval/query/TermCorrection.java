package com.nlda.retrieval.query;

public record TermCorrection(
        String original,
        String corrected,
        double confidence,
        boolean ambiguous
) {
}
