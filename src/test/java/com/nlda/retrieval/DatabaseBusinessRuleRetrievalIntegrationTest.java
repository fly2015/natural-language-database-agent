package com.nlda.retrieval;

import com.nlda.retrieval.index.SchemaIndexService;
import com.nlda.retrieval.model.ChunkKind;
import com.nlda.retrieval.model.IndexedSchemaChunks;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "agent.retrieval.business-rule-source=database")
class DatabaseBusinessRuleRetrievalIntegrationTest {

    @Autowired
    private SchemaIndexService schemaIndexService;

    @Test
    void databaseBackedRulesAreBuiltIntoRetrievalChunks() {
        IndexedSchemaChunks indexed = schemaIndexService.refresh();

        assertThat(indexed.chunks())
                .anySatisfy(chunk -> {
                    assertThat(chunk.kind()).isEqualTo(ChunkKind.BUSINESS_RULE);
                    assertThat(chunk.id()).isEqualTo("rule.revenue");
                    assertThat(chunk.text()).contains("orders.total_amount");
                    assertThat(chunk.aliases()).contains("revenue", "spending");
                });
    }
}
