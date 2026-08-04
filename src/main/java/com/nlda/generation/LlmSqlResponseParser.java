package com.nlda.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class LlmSqlResponseParser {

    private final ObjectMapper objectMapper;

    public LlmSqlResponseParser() {
        this(new ObjectMapper());
    }

    LlmSqlResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GeneratedSql parse(String response) {
        if (response == null || response.isBlank()) {
            return GeneratedSql.rejected("The model returned an empty response.");
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            String status = text(root, "status").toUpperCase(Locale.ROOT);
            if ("OK".equals(status)) {
                String sql = text(root, "sql").strip();
                if (sql.isBlank()) {
                    return GeneratedSql.rejected("The model did not return SQL.");
                }
                return GeneratedSql.ok(sql, assumptions(root));
            }
            if ("REJECTED".equals(status)) {
                String reason = text(root, "reason");
                return GeneratedSql.rejected(reason.isBlank() ? "The model rejected the request." : reason);
            }
            return GeneratedSql.rejected("The model returned an unsupported status.");
        } catch (Exception ex) {
            return GeneratedSql.rejected("The model returned malformed JSON.");
        }
    }

    private List<String> assumptions(JsonNode root) {
        JsonNode node = root.get("assumptions");
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return List.copyOf(values);
    }

    private String text(JsonNode root, String fieldName) {
        JsonNode node = root.get(fieldName);
        if (node == null || node.isNull()) {
            return "";
        }
        return node.asText("");
    }

    private String extractJson(String value) {
        String stripped = value.strip();
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            int lastFence = stripped.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                stripped = stripped.substring(firstNewline + 1, lastFence).strip();
            }
        }
        int firstBrace = stripped.indexOf('{');
        int lastBrace = stripped.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace >= firstBrace) {
            return stripped.substring(firstBrace, lastBrace + 1);
        }
        return stripped;
    }
}
