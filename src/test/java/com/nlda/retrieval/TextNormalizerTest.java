package com.nlda.retrieval;

import com.nlda.retrieval.text.TextNormalizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    private final TextNormalizer normalizer = new TextNormalizer();

    @Test
    void normalizesUnicodePunctuationCasingAndWhitespace() {
        assertThat(normalizer.normalize("  Café--CUSTOMERS!!  revenue\t2026 "))
                .isEqualTo("cafe customers revenue 2026");
    }

    @Test
    void emitsLuceneAnalyzedTermsForStemming() {
        assertThat(normalizer.analyzedTerms("customers spending orders"))
                .contains("custom", "spend", "order");
    }
}
