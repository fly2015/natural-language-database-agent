package com.nlda.retrieval;

import com.nlda.retrieval.index.SchemaChunkBuilder;
import com.nlda.retrieval.model.BusinessRule;
import com.nlda.retrieval.model.ChunkKind;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.schema.SchemaColumnMetadata;
import com.nlda.retrieval.model.schema.SchemaForeignKeyMetadata;
import com.nlda.retrieval.model.schema.SchemaMetadataSnapshot;
import com.nlda.retrieval.model.schema.SchemaTableMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaChunkBuilderTest {

    @Test
    void buildsTableChunksWithColumnsKeysAndJoins() {
        SchemaChunkBuilder builder = new SchemaChunkBuilder(() -> List.of());
        SchemaMetadataSnapshot snapshot = new SchemaMetadataSnapshot(List.of(
                new SchemaTableMetadata(
                        "orders",
                        List.of(
                                new SchemaColumnMetadata("id", "BIGINT", false, 1),
                                new SchemaColumnMetadata("customer_id", "BIGINT", false, 2)
                        ),
                        Set.of("id"),
                        List.of(new SchemaForeignKeyMetadata("customer_id", "customers", "id"))
                )
        ));

        List<RetrievedChunk> chunks = builder.build(snapshot);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.getFirst().id()).isEqualTo("schema.orders");
        assertThat(chunks.getFirst().kind()).isEqualTo(ChunkKind.SCHEMA);
        assertThat(chunks.getFirst().text()).contains(
                "table: orders",
                "id BIGINT not null",
                "primary key: id",
                "customer_id -> customers.id"
        );
        assertThat(chunks.getFirst().schemaRefs()).contains("orders", "customers");
        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk.kind()).isEqualTo(ChunkKind.JOIN_PATH);
            assertThat(chunk.text()).contains("join path: orders -> customers");
            assertThat(chunk.schemaRefs()).contains("orders", "customers");
        });
    }

    @Test
    void addsBusinessRuleChunksSeparatelyFromSchemaChunks() {
        SchemaChunkBuilder builder = new SchemaChunkBuilder(() -> List.of(
                new BusinessRule("rule.vip", "Business rule: VIP customers spend more than 2000.",
                        Set.of("customers", "orders"), Set.of("vip", "important"))
        ));
        SchemaMetadataSnapshot snapshot = new SchemaMetadataSnapshot(List.of(
                new SchemaTableMetadata("customers",
                        List.of(new SchemaColumnMetadata("id", "BIGINT", false, 1)),
                        Set.of("id"),
                        List.of()),
                new SchemaTableMetadata("orders",
                        List.of(new SchemaColumnMetadata("id", "BIGINT", false, 1)),
                        Set.of("id"),
                        List.of())
        ));

        List<RetrievedChunk> chunks = builder.build(snapshot);

        assertThat(chunks).hasSize(3);
        assertThat(chunks).anySatisfy(chunk -> {
            assertThat(chunk.id()).isEqualTo("rule.vip");
            assertThat(chunk.kind()).isEqualTo(ChunkKind.BUSINESS_RULE);
            assertThat(chunk.text()).contains("VIP customers", "Aliases: important, vip");
            assertThat(chunk.schemaRefs()).contains("customers", "orders");
            assertThat(chunk.aliases()).contains("vip", "important");
        });
    }

    @Test
    void excludesBusinessRuleWhenSchemaRefsAreStale() {
        SchemaChunkBuilder builder = new SchemaChunkBuilder(() -> List.of(
                new BusinessRule("rule.stale", "Business rule: stale metric uses missing_table.amount.",
                        Set.of("missing_table.amount"), Set.of("stale"))
        ));
        SchemaMetadataSnapshot snapshot = new SchemaMetadataSnapshot(List.of(
                new SchemaTableMetadata("customers",
                        List.of(new SchemaColumnMetadata("id", "BIGINT", false, 1)),
                        Set.of("id"),
                        List.of())
        ));

        List<RetrievedChunk> chunks = builder.build(snapshot);

        assertThat(chunks).noneSatisfy(chunk -> assertThat(chunk.id()).isEqualTo("rule.stale"));
    }
}
