package com.nlda.retrieval;

import com.nlda.retrieval.impl.repository.PgVectorReadySchemaChunkRepository;
import com.nlda.retrieval.impl.retriever.DynamicSchemaRetriever;
import com.nlda.retrieval.index.SchemaChunkBuilder;
import com.nlda.retrieval.index.SchemaIndexService;
import com.nlda.retrieval.metadata.JdbcSchemaMetadataExtractor;
import com.nlda.retrieval.metadata.SchemaMetadataException;
import com.nlda.retrieval.model.IndexedSchemaChunks;
import com.nlda.retrieval.model.RetrievalAttempt;
import com.nlda.retrieval.model.RetrievalContext;
import com.nlda.retrieval.model.RetrievalMode;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.schema.SchemaColumnMetadata;
import com.nlda.retrieval.model.schema.SchemaMetadataSnapshot;
import com.nlda.retrieval.model.schema.SchemaTableMetadata;
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
    void consumeOnlyRetrievalUsesReadyIndexWithoutRefreshingMetadata() {
        PgVectorReadySchemaChunkRepository repository = new PgVectorReadySchemaChunkRepository();
        repository.replace(new IndexedSchemaChunks("known", List.of(
                RetrievedChunk.schema("schema.customers", "table: customers; columns: id BIGINT, name VARCHAR",
                        0.75, Set.of("customers")),
                RetrievedChunk.schema("schema.orders", "table: orders; columns: id BIGINT, customer_id BIGINT, total_amount DECIMAL",
                        0.75, Set.of("orders", "customers")),
                RetrievedChunk.businessRule("rule.revenue", "Business rule: revenue and spending use orders.total_amount.",
                        0.75, Set.of("orders"), Set.of("revenue", "spending"))
        )));
        SchemaIndexService indexService = new SchemaIndexService(
                new FailingMetadataExtractor(),
                new SchemaChunkBuilder(() -> List.of()),
                repository
        );
        RetrievalService service = new RetrievalService(new DynamicSchemaRetriever(indexService));

        RetrievalContext context = service.retrieve("Show top customers by spending");

        assertThat(context.proceed()).isTrue();
        assertThat(context.finalMode()).isEqualTo(RetrievalMode.NORMALIZED);
        assertThat(context.attempts()).extracting(RetrievalAttempt::failureCode).containsExactly("NONE");
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
