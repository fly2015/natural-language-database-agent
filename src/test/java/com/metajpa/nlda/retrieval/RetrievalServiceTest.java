package com.metajpa.nlda.retrieval;

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
                new RetrievedChunk("schema.customers", "customers(id, name, region, vip)", 0.72, Set.of("customers")),
                new RetrievedChunk("schema.orders", "orders(id, customer_id, total_amount)", 0.72,
                        Set.of("orders", "customers"))
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
                new RetrievedChunk("schema.orders", "orders(id, customer_id, total_amount)", 0.75, Set.of("orders")),
                new RetrievedChunk("schema.customers", "customers(id, name)", 0.75, Set.of("customers"))
        );
        RetrievalService service = new RetrievalService(new StaticSchemaRetriever(List.of(), fallback));
        RetrievalContext previous = new RetrievalContext(true, 0.70, List.of("stale schema"), "", "NONE",
                RetrievalMode.NORMALIZED, List.of());

        RetrievalContext context = service.recoverFromValidationFailure("Show top customers by spending", previous);

        assertThat(context.proceed()).isTrue();
        assertThat(context.attempts()).extracting(RetrievalAttempt::failureCode).contains("RF-04");
        assertThat(context.finalMode()).isEqualTo(RetrievalMode.FALLBACK_CACHE);
    }

    private record StaticSchemaRetriever(
            List<RetrievedChunk> chunks,
            List<RetrievedChunk> fallbackChunks
    ) implements SchemaRetriever {

        @Override
        public List<RetrievedChunk> retrieve(String query, RetrievalMode mode) {
            return chunks;
        }

        @Override
        public List<RetrievedChunk> fallback(String query) {
            return fallbackChunks;
        }
    }

    private static class TimeoutSchemaRetriever implements SchemaRetriever {

        @Override
        public List<RetrievedChunk> retrieve(String query, RetrievalMode mode) {
            throw new IllegalStateException("vector service timeout");
        }

        @Override
        public List<RetrievedChunk> fallback(String query) {
            return List.of();
        }
    }
}
