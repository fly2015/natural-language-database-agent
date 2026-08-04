package com.nlda.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaIndexServiceTest {

    @Test
    void schemaFingerprintChangesWhenMetadataChanges() {
        SchemaMetadataSnapshot first = new SchemaMetadataSnapshot(List.of(
                new SchemaTableMetadata("orders",
                        List.of(new SchemaColumnMetadata("id", "BIGINT", false, 1)),
                        Set.of("id"),
                        List.of())
        ));
        SchemaMetadataSnapshot second = new SchemaMetadataSnapshot(List.of(
                new SchemaTableMetadata("orders",
                        List.of(
                                new SchemaColumnMetadata("id", "BIGINT", false, 1),
                                new SchemaColumnMetadata("total_amount", "DECIMAL", false, 2)
                        ),
                        Set.of("id"),
                        List.of())
        ));

        assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
    }

    @Test
    void fallbackCanUsePreviousIndexWhenDynamicExtractionFails() {
        PgVectorReadySchemaChunkRepository repository = new PgVectorReadySchemaChunkRepository();
        repository.replace(new IndexedSchemaChunks("known", List.of(
                new RetrievedChunk("schema.customers", "table: customers; columns: id BIGINT, name VARCHAR",
                        0.75, Set.of("customers")),
                new RetrievedChunk("schema.orders", "table: orders; columns: id BIGINT, customer_id BIGINT, total_amount DECIMAL",
                        0.75, Set.of("orders", "customers")),
                new RetrievedChunk("rule.revenue", "Business rule: revenue and spending use orders.total_amount.",
                        0.75, Set.of("orders"))
        )));
        SchemaIndexService indexService = new SchemaIndexService(
                new FailingMetadataExtractor(),
                new SchemaChunkBuilder(() -> List.of()),
                repository
        );
        RetrievalService service = new RetrievalService(new DynamicSchemaRetriever(indexService));

        RetrievalContext context = service.retrieve("Show top customers by spending");

        assertThat(context.proceed()).isTrue();
        assertThat(context.finalMode()).isEqualTo(RetrievalMode.FALLBACK_CACHE);
        assertThat(context.attempts()).extracting(RetrievalAttempt::failureCode).contains("RF-03");
    }

    private static class FailingMetadataExtractor extends JdbcSchemaMetadataExtractor {

        private FailingMetadataExtractor() {
            super(null);
        }

        @Override
        public SchemaMetadataSnapshot extract() {
            throw new SchemaMetadataException("metadata unavailable", new IllegalStateException("offline"));
        }
    }
}
