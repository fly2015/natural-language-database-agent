package com.nlda.retrieval;

import com.nlda.retrieval.contract.SchemaRetriever;
import com.nlda.retrieval.impl.retriever.InMemorySchemaRetriever;
import com.nlda.retrieval.model.ChunkKind;
import com.nlda.retrieval.model.RetrievalAttempt;
import com.nlda.retrieval.model.RetrievalContext;
import com.nlda.retrieval.model.RetrievalMode;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.query.ProcessedQuery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalServiceTest {

    @Test
    void classifiesEmptyRetrievalAsRf01AndStopsSafely() {
        RetrievalService service = new RetrievalService(new StaticSchemaRetriever(List.of(), List.of()));

        RetrievalContext context = service.retrieve("What is employee churn by department?");

        assertThat(context.proceed()).isFalse();
        assertThat(context.failureCode()).isEqualTo("RF-01");
        assertThat(context.attempts()).hasSize(4);
        assertThat(context.attempts()).extracting(RetrievalAttempt::mode)
                .containsExactly(RetrievalMode.NORMALIZED, RetrievalMode.EXPANDED, RetrievalMode.HYBRID,
                        RetrievalMode.FALLBACK_CACHE);
        assertThat(context.reason()).contains("trusted schema context");
    }

    @Test
    void retriesLowConfidenceAndUsesFallbackCache() {
        RetrievedChunk weakChunk = new RetrievedChunk("weak.orders", "orders(id)", 0.20, Set.of("orders"));
        List<RetrievedChunk> fallback = List.of(
                RetrievedChunk.schema("schema.customers", "customers(id, name, region, vip)", 0.72, Set.of("customers")),
                RetrievedChunk.schema("schema.orders", "orders(id, customer_id, total_amount)", 0.72,
                        Set.of("orders", "customers")),
                RetrievedChunk.businessRule("rule.spending", "Business rule: spending uses orders.total_amount.", 0.72,
                        Set.of("orders"), Set.of("spending"))
        );
        RetrievalService service = new RetrievalService(new StaticSchemaRetriever(List.of(weakChunk), fallback));

        RetrievalContext context = service.retrieve("Show top customers by spending");

        assertThat(context.proceed()).isTrue();
        assertThat(context.finalMode()).isEqualTo(RetrievalMode.FALLBACK_CACHE);
        assertThat(context.attempts()).hasSize(4);
        assertThat(context.attempts().getFirst().failureCode()).isEqualTo("RF-02");
    }

    @Test
    void classifiesTimeoutAsRf03ThenStopsSafelyWhenFallbackIsEmpty() {
        RetrievalService service = new RetrievalService(new TimeoutSchemaRetriever());

        RetrievalContext context = service.retrieve("Show monthly revenue");

        assertThat(context.proceed()).isFalse();
        assertThat(context.attempts()).extracting(RetrievalAttempt::failureCode).contains("RF-03");
        assertThat(context.failureCode()).isEqualTo("RF-01");
        assertThat(context.snippets()).isEmpty();
    }

    @Test
    void expandedAttemptCanRecoverAliasQuery() {
        RetrievalService service = new RetrievalService(new InMemorySchemaRetriever());

        RetrievalContext context = service.retrieve("Show top clients by sales");

        assertThat(context.proceed()).isTrue();
        assertThat(context.confidence()).isGreaterThanOrEqualTo(0.65);
        assertThat(context.snippets()).anySatisfy(snippet -> assertThat(snippet).contains("customers"));
    }

    @Test
    void productRevenueRetrievalIncludesJoinPathContext() {
        RetrievalService service = new RetrievalService(new InMemorySchemaRetriever());

        RetrievalContext context = service.retrieve("Which products generated the highest revenue?");

        assertThat(context.proceed()).isTrue();
        assertThat(context.snippets()).anySatisfy(snippet -> assertThat(snippet).contains("products"));
        assertThat(context.snippets()).anySatisfy(snippet -> assertThat(snippet).contains("order_items"));
        assertThat(context.snippets()).anySatisfy(snippet -> assertThat(snippet).contains("revenue"));
    }

    @Test
    void validationFailureIsTrackedAsRf04BeforeFallback() {
        List<RetrievedChunk> fallback = List.of(
                RetrievedChunk.schema("schema.orders", "orders(id, customer_id, total_amount)", 0.75, Set.of("orders")),
                RetrievedChunk.schema("schema.customers", "customers(id, name)", 0.75, Set.of("customers")),
                RetrievedChunk.businessRule("rule.spending", "Business rule: spending uses orders.total_amount.", 0.75,
                        Set.of("orders"), Set.of("spending"))
        );
        RetrievalService service = new RetrievalService(new StaticSchemaRetriever(List.of(), fallback));
        RetrievalContext previous = new RetrievalContext(true, 0.70, List.of("stale schema"), "", "NONE",
                RetrievalMode.NORMALIZED, List.of());

        RetrievalContext context = service.recoverFromValidationFailure("Show top customers by spending", previous);

        assertThat(context.proceed()).isTrue();
        assertThat(context.attempts()).extracting(RetrievalAttempt::failureCode).contains("RF-04");
        assertThat(context.finalMode()).isEqualTo(RetrievalMode.FALLBACK_CACHE);
    }

    @Test
    void confidenceUsesTypedContextWithoutDomainSpecificCoverage() {
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.schema("schema.invoices", "table: invoices; columns: id, amount, account_id",
                        0.70, Set.of("invoices")),
                RetrievedChunk.schema("schema.accounts", "table: accounts; columns: id, name",
                        0.70, Set.of("accounts")),
                RetrievedChunk.businessRule("rule.arr", "Business rule: ARR means invoice amount.", 0.70,
                        Set.of("invoices"), Set.of("arr"))
        );
        RetrievalService service = new RetrievalService(new StaticSchemaRetriever(chunks, List.of()));

        RetrievalContext context = service.retrieve("Show ARR by account");

        assertThat(context.proceed()).isTrue();
        assertThat(context.snippets()).anySatisfy(snippet -> assertThat(snippet).contains("ARR"));
    }

    private record StaticSchemaRetriever(
            List<RetrievedChunk> chunks,
            List<RetrievedChunk> fallbackChunks
    ) implements SchemaRetriever {

        @Override
        public List<RetrievedChunk> retrieve(ProcessedQuery query, RetrievalMode mode) {
            return chunks;
        }

        @Override
        public List<RetrievedChunk> fallback(ProcessedQuery query) {
            return fallbackChunks;
        }
    }

    private static class TimeoutSchemaRetriever implements SchemaRetriever {

        @Override
        public List<RetrievedChunk> retrieve(ProcessedQuery query, RetrievalMode mode) {
            throw new IllegalStateException("vector service timeout");
        }

        @Override
        public List<RetrievedChunk> fallback(ProcessedQuery query) {
            return List.of();
        }
    }
}
