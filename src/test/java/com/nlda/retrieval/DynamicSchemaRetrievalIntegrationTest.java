package com.nlda.retrieval;

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
    void refreshBuildsDynamicChunksAndRetrievalUsesThem() {
        IndexedSchemaChunks indexed = schemaIndexService.refresh();

        assertThat(indexed.fingerprint()).isNotBlank();
        assertThat(indexed.chunks()).anySatisfy(chunk -> assertThat(chunk.text())
                .contains("table: orders", "foreign keys:", "customer_id -> customers.id"));
        assertThat(indexed.chunks()).anySatisfy(chunk -> assertThat(chunk.id()).startsWith("rule."));

        List<RetrievedChunk> chunks = schemaRetriever.retrieve("Show top customers by revenue", RetrievalMode.NORMALIZED);

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
}
