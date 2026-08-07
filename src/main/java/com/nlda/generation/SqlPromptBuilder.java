package com.nlda.generation;

import com.nlda.retrieval.model.RetrievalContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SqlPromptBuilder {

    public String buildGenerationPrompt(String question, RetrievalContext context) {
        return """
                You are a SQL generator for a natural-language database agent.

                Rules:
                - Return only one JSON object. Do not include markdown.
                - JSON fields: status, sql, assumptions, reason.
                - status must be "OK" or "REJECTED".
                - If status is "OK", sql must contain exactly one read-only SELECT statement.
                - Never return INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, CREATE, MERGE, CALL, EXEC, GRANT, or REVOKE.
                - Do not return multiple statements.
                - Do not use SELECT *.
                - Include LIMIT 100 unless the user asks for fewer rows.
                - Use only the retrieved schema and business-rule context below.
                - Put only real table names after FROM or JOIN.
                - Never use a column name, date field, metric field, or alias as a table name.
                - For year filters on date/timestamp columns, prefer a half-open date range such as column >= DATE '2026-01-01' AND column < DATE '2027-01-01'. Do not wrap indexed date columns in EXTRACT, YEAR, FORMAT, or TO_CHAR for filtering.
                - Reject ambiguous or unsupported questions instead of guessing.

                User question:
                %s

                Retrieval confidence: %.2f
                Retrieval mode: %s

                Retrieved context:
                %s

                Response example:
                {"status":"OK","sql":"SELECT id FROM orders LIMIT 100","assumptions":[],"reason":""}
                """.formatted(question, context.confidence(), context.finalMode(), snippets(context.snippets()));
    }

    public String buildRepairPrompt(
            String question,
            RetrievalContext context,
            String rejectedSql,
            List<String> validationErrors
    ) {
        return """
                You are repairing SQL for a natural-language database agent.

                Rules:
                - Return only one JSON object. Do not include markdown.
                - JSON fields: status, sql, assumptions, reason.
                - status must be "OK" or "REJECTED".
                - Repair only if the retrieved context supports a safe read-only SELECT.
                - Never return write/destructive SQL or multiple statements.
                - Do not use SELECT *.
                - Include LIMIT 100 unless the user asks for fewer rows.
                - Use only the retrieved schema and business-rule context below.
                - Put only real table names after FROM or JOIN.
                - If an unknown table name is actually a column in the retrieved context, move it into SELECT, WHERE, GROUP BY, or ORDER BY and use the owning table in FROM.
                - For year filters on date/timestamp columns, repair EXTRACT/YEAR/FORMAT/TO_CHAR filters into half-open date ranges.
                - If the validation errors cannot be fixed from the context, return REJECTED.

                User question:
                %s

                Rejected SQL:
                %s

                Validation errors:
                %s

                Retrieved context:
                %s
                """.formatted(question, nullToEmpty(rejectedSql), validationErrors, snippets(context.snippets()));
    }

    private String snippets(List<String> snippets) {
        if (snippets == null || snippets.isEmpty()) {
            return "- none";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < snippets.size(); i++) {
            builder.append("- [").append(i + 1).append("] ").append(snippets.get(i)).append('\n');
        }
        return builder.toString().stripTrailing();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
