package com.nlda.retrieval;

import com.nlda.retrieval.contract.SchemaRetriever;
import com.nlda.retrieval.contract.SchemaMetadataProvider;
import com.nlda.retrieval.index.SchemaIndexService;
import com.nlda.retrieval.metadata.JdbcSchemaMetadataExtractor;
import com.nlda.retrieval.model.ChunkKind;
import com.nlda.retrieval.model.IndexedSchemaChunks;
import com.nlda.retrieval.model.RetrievalMode;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.query.ProcessedQuery;
import com.nlda.retrieval.model.schema.SchemaColumnMetadata;
import com.nlda.retrieval.model.schema.SchemaForeignKeyMetadata;
import com.nlda.retrieval.model.schema.SchemaMetadataSnapshot;
import com.nlda.retrieval.model.schema.SchemaTableMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DynamicSchemaRetrievalIntegrationTest {

    @Autowired
    private JdbcSchemaMetadataExtractor metadataExtractor;

    @Autowired
    private SchemaMetadataProvider metadataProvider;

    @Autowired
    private SchemaIndexService schemaIndexService;

    @Autowired
    private SchemaRetriever schemaRetriever;

    @Test
    void extractsDemoH2SchemaFromJdbcMetadata() {
        SchemaMetadataSnapshot snapshot = metadataExtractor.extract();

        assertThat(snapshot.tables()).extracting(SchemaTableMetadata::name)
                .contains("customers", "orders", "order_items", "products");
        SchemaTableMetadata orders = table(snapshot, "orders");
        assertThat(orders.columns()).extracting(SchemaColumnMetadata::name)
                .contains("id", "customer_id", "order_date", "status", "total_amount");
        assertThat(orders.primaryKeys()).contains("id");
        assertThat(orders.foreignKeys()).anySatisfy(foreignKey -> {
            assertThat(foreignKey.columnName()).isEqualTo("customer_id");
            assertThat(foreignKey.referencedTable()).isEqualTo("customers");
            assertThat(foreignKey.referencedColumn()).isEqualTo("id");
        });
    }

    @Test
    void exposesSchemaExtractionBehindMetadataProviderBoundary() {
        SchemaMetadataSnapshot snapshot = metadataProvider.extract();

        assertThat(metadataProvider.dialect()).isEqualTo("generic-jdbc");
        assertThat(snapshot.tables()).extracting(SchemaTableMetadata::name).contains("customers");
    }

    @Test
    void refreshBuildsDynamicChunksAndRetrievalUsesThem() {
        IndexedSchemaChunks indexed = schemaIndexService.refresh();

        assertThat(indexed.fingerprint()).isNotBlank();
        assertThat(indexed.chunks()).anySatisfy(chunk -> assertThat(chunk.text())
                .contains("table: orders", "foreign keys:", "customer_id -> customers.id"));
        assertThat(indexed.chunks()).anySatisfy(chunk -> {
            assertThat(chunk.kind()).isEqualTo(ChunkKind.BUSINESS_RULE);
            assertThat(chunk.id()).startsWith("rule.");
        });
        assertThat(indexed.chunks()).anySatisfy(chunk -> {
            assertThat(chunk.kind()).isEqualTo(ChunkKind.JOIN_PATH);
            assertThat(chunk.text()).contains("join path:");
        });

        List<RetrievedChunk> chunks = schemaRetriever.retrieve(
                processedQuery("Show top customers by revenue"),
                RetrievalMode.NORMALIZED
        );

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).anySatisfy(chunk -> assertThat(chunk.text()).contains("orders"));
        assertThat(chunks).anySatisfy(chunk -> assertThat(chunk.text()).contains("customers"));
    }

    private SchemaTableMetadata table(SchemaMetadataSnapshot snapshot, String name) {
        return snapshot.tables().stream()
                .filter(table -> table.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private ProcessedQuery processedQuery(String value) {
        return new ProcessedQuery(value, value.toLowerCase(), List.of(), List.of(), java.util.Set.of(),
                value.toLowerCase(), false, 1.0);
    }
}
