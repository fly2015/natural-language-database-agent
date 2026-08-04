package com.nlda.generation;

import com.nlda.retrieval.RetrievalContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class SqlGenerationService {

    public GeneratedSql generate(String question, RetrievalContext context) {
        String normalized = question.toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "drop", "delete", "update", "insert", "alter", "truncate", "create", "merge")) {
            return GeneratedSql.rejected("The request asks for a write or destructive database operation.");
        }
        if (containsAny(normalized, "two statements", "multiple statements")) {
            return GeneratedSql.rejected("Only one read-only SQL statement is allowed.");
        }

        if (!context.proceed()) {
            return GeneratedSql.rejected(context.reason());
        }

        if (containsAny(normalized, "customer", "customers") && normalized.contains("top")
                && !containsAny(normalized, "spending", "spend", "revenue")) {
            return GeneratedSql.rejected("Please clarify the customer ranking metric, for example total spending or order count.");
        }
        if (normalized.contains("top") && containsAny(normalized, "customer", "customers")
                && containsAny(normalized, "spending", "spend", "revenue")) {
            return ok("""
                    SELECT c.id AS customer_id, c.name AS customer_name, SUM(o.total_amount) AS total_spending
                    FROM customers c
                    JOIN orders o ON o.customer_id = c.id
                    GROUP BY c.id, c.name
                    ORDER BY total_spending DESC
                    LIMIT 10
                    """, "Interpreted spending as sum of order total_amount.");
        }
        if (containsAll(normalized, "monthly", "revenue")) {
            return ok("""
                    SELECT FORMATDATETIME(o.order_date, 'yyyy-MM') AS revenue_month, SUM(o.total_amount) AS revenue
                    FROM orders o
                    GROUP BY FORMATDATETIME(o.order_date, 'yyyy-MM')
                    ORDER BY revenue_month
                    LIMIT 100
                    """, "Monthly revenue is grouped by order_date month.");
        }
        if (containsAll(normalized, "undelivered", "orders")) {
            String regionFilter = regionFilter(normalized);
            return ok(("""
                    SELECT o.id AS order_id, c.name AS customer_name, c.region, o.order_date, o.status, o.total_amount
                    FROM orders o
                    JOIN customers c ON c.id = o.customer_id
                    WHERE o.status <> 'DELIVERED'
                    %s
                    ORDER BY o.order_date DESC
                    LIMIT 100
                    """).formatted(regionFilter), "Undelivered means any order status other than DELIVERED.");
        }
        if (containsAll(normalized, "products") && containsAny(normalized, "highest revenue", "generated the highest", "top")) {
            return ok("""
                    SELECT p.id AS product_id, p.name AS product_name, SUM(oi.quantity * oi.unit_price) AS revenue
                    FROM order_items oi
                    JOIN products p ON p.id = oi.product_id
                    GROUP BY p.id, p.name
                    ORDER BY revenue DESC
                    LIMIT 100
                    """, "Product revenue is calculated from order_items quantity times unit_price.");
        }
        if (containsAll(normalized, "order", "counts", "region") || containsAll(normalized, "orders", "by", "region")) {
            return ok("""
                    SELECT c.region, COUNT(o.id) AS order_count
                    FROM orders o
                    JOIN customers c ON c.id = o.customer_id
                    GROUP BY c.region
                    ORDER BY order_count DESC
                    LIMIT 100
                    """, "Region is read from customers.region.");
        }
        return GeneratedSql.rejected("I could not map the question to trusted demo schema context yet.");
    }

    private GeneratedSql ok(String sql, String assumption) {
        return new GeneratedSql("OK", sql.strip(), List.of(assumption), "");
    }

    private boolean containsAll(String value, String... terms) {
        for (String term : terms) {
            if (!value.contains(term)) {
                return false;
            }
        }
        return true;
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String regionFilter(String normalizedQuestion) {
        if (normalizedQuestion.contains("hanoi")) {
            return "AND c.region = 'Hanoi'";
        }
        if (normalizedQuestion.contains("da nang")) {
            return "AND c.region = 'Da Nang'";
        }
        if (normalizedQuestion.contains("ho chi minh")) {
            return "AND c.region = 'Ho Chi Minh City'";
        }
        return "";
    }
}
