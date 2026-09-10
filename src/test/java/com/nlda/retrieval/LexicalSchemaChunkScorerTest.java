package com.nlda.retrieval;

import com.nlda.retrieval.impl.retriever.LexicalSchemaChunkScorer;
import com.nlda.retrieval.model.RetrievalMode;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.query.ProcessedQuery;
import com.nlda.retrieval.text.TextNormalizer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LexicalSchemaChunkScorerTest {

    private final LexicalSchemaChunkScorer scorer = new LexicalSchemaChunkScorer(new TextNormalizer());

    @Test
    void scoresBusinessRuleAliasesAndRelatedSchemaRefs() {
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.businessRule("rule.revenue", "Business rule: revenue uses orders.total_amount.",
                        0.0, Set.of("orders"), Set.of("sales")),
                RetrievedChunk.schema("schema.orders", "table: orders; columns: id, total_amount", 0.0,
                        Set.of("orders")),
                RetrievedChunk.schema("schema.products", "table: products; columns: id, name", 0.0,
                        Set.of("products"))
        );

        List<RetrievedChunk> scored = scorer.score(query("show sales"), RetrievalMode.NORMALIZED, chunks);

        RetrievedChunk rule = chunk(scored, "rule.revenue");
        RetrievedChunk orders = chunk(scored, "schema.orders");
        RetrievedChunk products = chunk(scored, "schema.products");
        assertThat(rule.score()).isGreaterThan(0.0);
        assertThat(orders.score()).isGreaterThan(0.0);
        assertThat(products.score()).isEqualTo(0.0);
    }

    @Test
    void boostsJoinPathWhenBusinessRuleInfersBothSchemaRefs() {
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.businessRule("rule.revenue", "Business rule: revenue uses orders and customers.",
                        0.0, Set.of("orders", "customers"), Set.of("revenue")),
                RetrievedChunk.joinPath("join.orders.customers.customer_id",
                        "join path: orders -> customers; key: orders.customer_id = customers.id", 0.0,
                        Set.of("orders", "customers"))
        );

        List<RetrievedChunk> scored = scorer.score(query("revenue"), RetrievalMode.HYBRID, chunks);

        assertThat(chunk(scored, "join.orders.customers.customer_id").score()).isGreaterThan(0.0);
    }

    @Test
    void ambiguousCorrectionReducesScores() {
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.schema("schema.orders", "table: orders; columns: order_date", 0.0,
                        Set.of("orders"))
        );

        double normalScore = chunk(scorer.score(query("orders"), RetrievalMode.NORMALIZED, chunks),
                "schema.orders").score();
        double ambiguousScore = chunk(scorer.score(ambiguousQuery("orders"), RetrievalMode.NORMALIZED, chunks),
                "schema.orders").score();

        assertThat(ambiguousScore).isLessThan(normalScore);
    }

    private ProcessedQuery query(String value) {
        return new ProcessedQuery(value, value, List.of(value.split(" ")), List.of(), Set.of(), value, false, 1.0);
    }

    private ProcessedQuery ambiguousQuery(String value) {
        return new ProcessedQuery(value, value, List.of(value.split(" ")), List.of(), Set.of(), value, true, 1.0);
    }

    private RetrievedChunk chunk(List<RetrievedChunk> chunks, String id) {
        return chunks.stream()
                .filter(chunk -> chunk.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
