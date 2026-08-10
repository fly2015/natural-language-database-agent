package com.nlda.retrieval;

import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.impl.vocabulary.InMemoryVocabularyCorrectionService;
import com.nlda.retrieval.query.RetrievalQueryProcessor;
import com.nlda.retrieval.query.SchemaVocabularyMatcher;
import com.nlda.retrieval.model.schema.SchemaMetadataSnapshot;
import com.nlda.retrieval.text.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalQueryProcessorTest {

    private final TextNormalizer textNormalizer = new TextNormalizer();

    @Test
    void emitsOriginalNormalizedCorrectedAndRetrievalQuery() {
        InMemoryVocabularyCorrectionService correctionService = correctionService();
        RetrievalQueryProcessor processor = new RetrievalQueryProcessor(textNormalizer, correctionService);
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.schema("schema.customers", "table: customers; columns: id, region",
                        0.0, Set.of("customers", "customers.region")),
                RetrievedChunk.businessRule("rule.revenue", "spending means orders.total_amount",
                        0.0, Set.of("orders.total_amount"), Set.of("spending", "revenue"))
        );
        correctionService.rebuild(new SchemaMetadataSnapshot(List.of()), chunks);

        var query = processor.process("Top custmer spend by regoin");

        assertThat(query.original()).isEqualTo("Top custmer spend by regoin");
        assertThat(query.normalized()).isEqualTo("top custmer spend by regoin");
        assertThat(query.correctedTerms()).extracting("corrected")
                .contains("customer", "region");
        assertThat(query.retrievalQuery()).contains("custmer", "customer", "regoin", "region", "spend");
        assertThat(query.ambiguous()).isFalse();
    }

    @Test
    void correctsAdjacentTranspositionTypoFromMaintainedVocabulary() {
        InMemoryVocabularyCorrectionService correctionService = correctionService();
        RetrievalQueryProcessor processor = new RetrievalQueryProcessor(textNormalizer, correctionService);
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.schema("schema.customers", "table: customers; columns: id, name",
                        0.0, Set.of("customers"))
        );
        correctionService.rebuild(new SchemaMetadataSnapshot(List.of()), chunks);

        var query = processor.process("show cutsomer list");

        assertThat(query.correctedTerms()).extracting("corrected").contains("customer");
        assertThat(query.retrievalQuery()).contains("cutsomer", "customer");
    }

    @Test
    void marksAmbiguousCorrectionWhenMultipleSchemaTermsAreEquallyLikely() {
        InMemoryVocabularyCorrectionService correctionService = correctionService();
        RetrievalQueryProcessor processor = new RetrievalQueryProcessor(textNormalizer, correctionService);
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.schema("schema.abcd", "table: abcd", 0.0, Set.of("abcd")),
                RetrievedChunk.schema("schema.abcf", "table: abcf", 0.0, Set.of("abcf"))
        );
        correctionService.rebuild(new SchemaMetadataSnapshot(List.of()), chunks);

        var query = processor.process("show abce");

        assertThat(query.ambiguous()).isTrue();
        assertThat(query.correctionConfidence()).isLessThan(1.0);
    }

    private InMemoryVocabularyCorrectionService correctionService() {
        return new InMemoryVocabularyCorrectionService(new SchemaVocabularyMatcher(textNormalizer));
    }
}
