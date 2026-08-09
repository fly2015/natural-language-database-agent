package com.nlda.retrieval.query;

import com.nlda.retrieval.model.VocabularySourceType;

public record CorrectionCandidate(
        String original,
        String corrected,
        VocabularySourceType sourceType,
        String sourceId,
        double score,
        boolean exact,
        boolean ambiguous
) {
}
