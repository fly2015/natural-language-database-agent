package com.nlda.guardrail;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SqlGuardrailServiceTest {

    private final SqlGuardrailService guardrailService = new SqlGuardrailService(new StaticSchemaCatalog());

    @Test
    void rejectsMutationQuery() {
        GuardrailResult result = guardrailService.validateAndSanitize("DELETE FROM orders");

        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).contains("SQL contains a mutation or destructive keyword.");
    }

    @Test
    void rejectsMultiStatementSql() {
        GuardrailResult result = guardrailService.validateAndSanitize("SELECT id FROM orders; UPDATE orders SET status = 'X'");

        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).contains("Only one SQL statement is allowed.");
    }

    @Test
    void injectsLimitWhenMissing() {
        GuardrailResult result = guardrailService.validateAndSanitize("SELECT id, status FROM orders");

        assertThat(result.allowed()).isTrue();
        assertThat(result.sql()).isEqualTo("SELECT id, status FROM orders LIMIT 100");
    }

    @Test
    void preservesExistingLimit() {
        GuardrailResult result = guardrailService.validateAndSanitize("SELECT id, status FROM orders LIMIT 5");

        assertThat(result.allowed()).isTrue();
        assertThat(result.sql()).isEqualTo("SELECT id, status FROM orders LIMIT 5");
    }

    @Test
    void rejectsSelectStar() {
        GuardrailResult result = guardrailService.validateAndSanitize("SELECT * FROM orders");

        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).contains("SELECT star is not allowed in this phase.");
    }

    @Test
    void rejectsHiddenMutationKeywordSplitByComment() {
        GuardrailResult result = guardrailService.validateAndSanitize("UP/**/DATE orders SET status = 'X'");

        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).contains("SQL contains a mutation or destructive keyword.");
    }

    @Test
    void rejectsMutationKeywordHiddenAfterLineCommentRemoval() {
        GuardrailResult result = guardrailService.validateAndSanitize("SELECT id FROM orders; -- harmless\nDROP TABLE orders");

        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).contains("SQL contains a mutation or destructive keyword.");
        assertThat(result.violations()).contains("Only one SQL statement is allowed.");
    }

    @Test
    void rejectsNonSelectReadLikeStatement() {
        GuardrailResult result = guardrailService.validateAndSanitize("WITH recent_orders AS (SELECT id FROM orders) SELECT id FROM recent_orders");

        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).contains("Only SELECT statements are allowed.");
    }

    @Test
    void capsExcessiveLimit() {
        GuardrailResult result = guardrailService.validateAndSanitize("SELECT id, status FROM orders LIMIT 5000");

        assertThat(result.allowed()).isTrue();
        assertThat(result.sql()).isEqualTo("SELECT id, status FROM orders LIMIT 1000");
    }

    @Test
    void rejectsInvalidTableReference() {
        GuardrailResult result = guardrailService.validateAndSanitize("SELECT id FROM invoices");

        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).contains("SQL references an unknown table: invoices.");
    }

    @Test
    void rejectsInvalidQualifiedColumnReference() {
        GuardrailResult result = guardrailService.validateAndSanitize("SELECT o.secret_note FROM orders o");

        assertThat(result.allowed()).isFalse();
        assertThat(result.violations()).contains("SQL references an unknown column: o.secret_note.");
    }

    private static class StaticSchemaCatalog implements SchemaCatalog {

        private static final Map<String, Set<String>> TABLES = Map.of(
                "customers", Set.of("id", "name", "region", "vip"),
                "orders", Set.of("id", "customer_id", "order_date", "status", "total_amount"),
                "order_items", Set.of("id", "order_id", "product_id", "quantity", "unit_price"),
                "products", Set.of("id", "name", "category")
        );

        @Override
        public boolean tableExists(String tableName) {
            return TABLES.containsKey(tableName);
        }

        @Override
        public boolean columnExists(String tableName, String columnName) {
            return TABLES.getOrDefault(tableName, Set.of()).contains(columnName);
        }
    }
}
