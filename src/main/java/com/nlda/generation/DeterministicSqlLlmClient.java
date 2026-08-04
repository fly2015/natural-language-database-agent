package com.nlda.generation;

import java.util.Locale;

public class DeterministicSqlLlmClient implements SqlLlmClient {

    @Override
    public String complete(String prompt) {
        String normalizedPrompt = prompt.toLowerCase(Locale.ROOT);
        if (normalizedPrompt.contains("you are repairing sql")) {
            return repair(normalizedPrompt);
        }
        String normalized = userQuestion(prompt).toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "drop", "delete", "update", "insert", "alter", "truncate", "create", "merge")) {
            return rejected("The request asks for a write or destructive database operation.");
        }
        if (containsAny(normalized, "two statements", "multiple statements")) {
            return rejected("Only one read-only SQL statement is allowed.");
        }
        if (containsAny(normalized, "customer", "customers") && normalized.contains("top")
                && !containsAny(normalized, "spending", "spend", "revenue")) {
            return rejected("Please clarify the customer ranking metric, for example total spending or order count.");
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
            return ok(("""
                    SELECT o.id AS order_id, c.name AS customer_name, c.region, o.order_date, o.status, o.total_amount
                    FROM orders o
                    JOIN customers c ON c.id = o.customer_id
                    WHERE o.status <> 'DELIVERED'
                    %s
                    ORDER BY o.order_date DESC
                    LIMIT 100
                    """).formatted(regionFilter(normalized)), "Undelivered means any order status other than DELIVERED.");
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
        return rejected("I could not map the question to trusted schema context yet.");
    }

    private String repair(String normalized) {
        if (normalized.contains("unknown table") && normalized.contains("top")
                && containsAny(normalized, "customer", "customers")) {
            return ok("""
                    SELECT c.id AS customer_id, c.name AS customer_name, SUM(o.total_amount) AS total_spending
                    FROM customers c
                    JOIN orders o ON o.customer_id = c.id
                    GROUP BY c.id, c.name
                    ORDER BY total_spending DESC
                    LIMIT 10
                    """, "Repaired invalid table reference using retrieved customers and orders context.");
        }
        return rejected("The SQL could not be repaired safely from the retrieved context.");
    }

    private String userQuestion(String prompt) {
        String marker = "User question:";
        int start = prompt.indexOf(marker);
        if (start < 0) {
            return prompt;
        }
        start += marker.length();
        int end = prompt.indexOf("Retrieval confidence:", start);
        if (end < 0) {
            end = prompt.indexOf("Retrieved context:", start);
        }
        if (end < 0) {
            end = prompt.length();
        }
        return prompt.substring(start, end).strip();
    }

    private String ok(String sql, String assumption) {
        return """
                {"status":"OK","sql":%s,"assumptions":[%s],"reason":""}
                """.formatted(jsonString(sql.strip()), jsonString(assumption)).strip();
    }

    private String rejected(String reason) {
        return """
                {"status":"REJECTED","sql":null,"assumptions":[],"reason":%s}
                """.formatted(jsonString(reason)).strip();
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

    private String regionFilter(String normalizedPrompt) {
        if (normalizedPrompt.contains("hanoi")) {
            return "AND c.region = 'Hanoi'";
        }
        if (normalizedPrompt.contains("da nang")) {
            return "AND c.region = 'Da Nang'";
        }
        if (normalizedPrompt.contains("ho chi minh")) {
            return "AND c.region = 'Ho Chi Minh City'";
        }
        return "";
    }

    private String jsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"";
    }
}
