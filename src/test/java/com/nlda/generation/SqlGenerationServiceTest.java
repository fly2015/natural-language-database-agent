package com.nlda.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nlda.retrieval.RetrievalContext;
import com.nlda.retrieval.RetrievalMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlGenerationServiceTest {

    @Test
    void fakeLlmHappyPathReturnsParsedSql() {
        CapturingClient client = new CapturingClient("""
                {"status":"OK","sql":"SELECT c.id, c.name FROM customers c LIMIT 100","assumptions":["customer list"],"reason":""}
                """);
        SqlGenerationService service = service(client);

        GeneratedSql sql = service.generate("List customers", context());

        assertThat(sql.status()).isEqualTo("OK");
        assertThat(sql.sql()).isEqualTo("SELECT c.id, c.name FROM customers c LIMIT 100");
        assertThat(sql.assumptions()).containsExactly("customer list");
    }

    @Test
    void promptIncludesOnlyRetrievedContextSnippets() {
        CapturingClient client = new CapturingClient("""
                {"status":"REJECTED","sql":null,"assumptions":[],"reason":"unsupported"}
                """);
        SqlGenerationService service = service(client);

        service.generate("List customers", context());

        assertThat(client.prompt).contains("table: customers");
        assertThat(client.prompt).contains("table: orders");
        assertThat(client.prompt).contains("Put only real table names after FROM or JOIN.");
        assertThat(client.prompt).contains("column >= DATE '2026-01-01'");
        assertThat(client.prompt).doesNotContain("products(id, name, category)");
    }

    @Test
    void malformedLlmJsonReturnsSafeRejection() {
        SqlGenerationService service = service(new CapturingClient("not-json"));

        GeneratedSql sql = service.generate("List customers", context());

        assertThat(sql.status()).isEqualTo("REJECTED");
        assertThat(sql.reason()).contains("malformed JSON");
    }

    @Test
    void repairPromptIncludesValidationErrors() {
        CapturingClient client = new CapturingClient("""
                {"status":"OK","sql":"SELECT c.id FROM customers c LIMIT 100","assumptions":[],"reason":""}
                """);
        SqlGenerationService service = service(client);

        GeneratedSql repaired = service.repair("List customers", context(), "SELECT x.id FROM invoices x",
                List.of("SQL references an unknown table: invoices."));

        assertThat(repaired.status()).isEqualTo("OK");
        assertThat(client.prompt).contains("You are repairing SQL");
        assertThat(client.prompt).contains("unknown table: invoices");
    }

    private SqlGenerationService service(SqlLlmClient client) {
        return new SqlGenerationService(new SqlPromptBuilder(), client, new LlmSqlResponseParser(new ObjectMapper()));
    }

    private RetrievalContext context() {
        return new RetrievalContext(true, 0.87, List.of(
                "table: customers; columns: id BIGINT not null, name VARCHAR, region VARCHAR; primary key: id",
                "table: orders; columns: id BIGINT not null, customer_id BIGINT, total_amount NUMERIC; foreign keys: customer_id -> customers.id"
        ), "", "NONE", RetrievalMode.NORMALIZED, List.of());
    }

    private static class CapturingClient implements SqlLlmClient {

        private final String response;
        private String prompt;

        private CapturingClient(String response) {
            this.response = response;
        }

        @Override
        public String complete(String prompt) {
            this.prompt = prompt;
            return response;
        }
    }
}
