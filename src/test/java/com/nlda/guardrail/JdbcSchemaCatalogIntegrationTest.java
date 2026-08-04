package com.nlda.guardrail;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JdbcSchemaCatalogIntegrationTest {

    @Autowired
    private JdbcSchemaCatalog schemaCatalog;

    @Test
    void readsDemoSchemaFromDatabaseMetadata() {
        assertThat(schemaCatalog.tableExists("orders")).isTrue();
        assertThat(schemaCatalog.columnExists("orders", "total_amount")).isTrue();
        assertThat(schemaCatalog.tableExists("invoices")).isFalse();
        assertThat(schemaCatalog.columnExists("orders", "secret_note")).isFalse();
    }
}
