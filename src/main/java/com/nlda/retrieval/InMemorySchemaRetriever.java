package com.nlda.retrieval;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class InMemorySchemaRetriever implements SchemaRetriever {

    private static final List<RetrievedChunk> CHUNKS = List.of(
            new RetrievedChunk("schema.customers", "customers(id, name, region, vip)", 0.0, Set.of("customers")),
            new RetrievedChunk("schema.orders", "orders(id, customer_id, order_date, status, total_amount)", 0.0,
                    Set.of("orders", "customers")),
            new RetrievedChunk("schema.order_items", "order_items(id, order_id, product_id, quantity, unit_price)",
                    0.0, Set.of("order_items", "orders", "products")),
            new RetrievedChunk("schema.products", "products(id, name, category)", 0.0, Set.of("products")),
            new RetrievedChunk("rule.revenue",
                    "Business rule: revenue and spending use monetary order total_amount unless product-level revenue is needed.",
                    0.0, Set.of("orders", "order_items", "products")),
            new RetrievedChunk("rule.undelivered",
                    "Business rule: undelivered orders are any orders where status is not DELIVERED.",
                    0.0, Set.of("orders"))
    );

    @Override
    public List<RetrievedChunk> retrieve(String query, RetrievalMode mode) {
        String normalized = normalize(query);
        List<RetrievedChunk> scored = new ArrayList<>();
        for (RetrievedChunk chunk : CHUNKS) {
            double score = score(normalized, chunk, mode);
            if (score > 0.0) {
                scored.add(chunk.withScore(score));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(5)
                .toList();
    }

    @Override
    public List<RetrievedChunk> fallback(String query) {
        String normalized = normalize(query);
        if (containsAny(normalized, "customer", "customers", "client", "clients", "order", "orders",
                "sale", "sales", "revenue", "spending", "spend", "product", "products", "region")) {
            return CHUNKS.stream()
                    .map(chunk -> chunk.withScore(0.72))
                    .toList();
        }
        return List.of();
    }

    private double score(String query, RetrievedChunk chunk, RetrievalMode mode) {
        String text = normalize(chunk.text());
        double score = 0.0;
        for (String token : query.split(" ")) {
            if (token.length() < 3) {
                continue;
            }
            if (text.contains(token)) {
                score += 0.16;
            }
        }
        if (containsAny(query, "customer", "customers", "client", "clients", "region")
                && chunk.schemaRefs().contains("customers")) {
            score += 0.35;
        }
        if (containsAny(query, "order", "orders", "revenue", "spending", "spend", "sale", "sales", "undelivered")
                && chunk.schemaRefs().contains("orders")) {
            score += 0.35;
        }
        if (containsAny(query, "product", "products") && chunk.schemaRefs().contains("products")) {
            score += 0.35;
        }
        if (mode == RetrievalMode.EXPANDED && containsAny(query, "client", "clients", "sale", "sales", "spend")) {
            score += aliasScore(query, text);
        }
        if (mode == RetrievalMode.HYBRID && containsAny(query, "top", "monthly", "undelivered", "highest")) {
            score += 0.12;
        }
        return Math.min(score, 0.95);
    }

    private double aliasScore(String query, String text) {
        double score = 0.0;
        if (containsAny(query, "client", "clients") && text.contains("customers")) {
            score += 0.24;
        }
        if (containsAny(query, "sale", "sales", "spend") && (text.contains("orders") || text.contains("revenue"))) {
            score += 0.24;
        }
        return score;
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }
}
