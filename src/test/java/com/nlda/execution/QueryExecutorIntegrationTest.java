package com.nlda.execution;

import com.nlda.format.TableResult;
import com.nlda.guardrail.SqlExecutionRejectedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class QueryExecutorIntegrationTest {

    @Autowired
    private QueryExecutor queryExecutor;

    @Test
    void executesApprovedSelectStatement() {
        TableResult result = queryExecutor.execute("SELECT id AS order_id, status FROM orders LIMIT 1");

        assertThat(result.rows()).hasSize(1);
        assertThat(result.columns()).contains("order_id", "status");
    }

    @Test
    void wrapsInvalidSchemaExecutionWithSafeException() {
        assertThatThrownBy(() -> queryExecutor.execute("SELECT missing_column FROM orders LIMIT 1"))
                .isInstanceOf(SqlExecutionRejectedException.class)
                .hasMessage("The approved SQL could not be executed safely.");
    }
}
