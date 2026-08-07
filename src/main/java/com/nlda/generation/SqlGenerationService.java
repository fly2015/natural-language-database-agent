package com.nlda.generation;

import com.nlda.retrieval.model.RetrievalContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class SqlGenerationService {

    private static final Logger log = LoggerFactory.getLogger(SqlGenerationService.class);

    private final SqlPromptBuilder promptBuilder;
    private final SqlLlmClient llmClient;
    private final LlmSqlResponseParser responseParser;

    public SqlGenerationService(
            SqlPromptBuilder promptBuilder,
            SqlLlmClient llmClient,
            LlmSqlResponseParser responseParser
    ) {
        this.promptBuilder = promptBuilder;
        this.llmClient = llmClient;
        this.responseParser = responseParser;
    }

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
        return callModel(promptBuilder.buildGenerationPrompt(question, context), "generation");
    }

    public GeneratedSql repair(
            String question,
            RetrievalContext context,
            String rejectedSql,
            List<String> validationErrors
    ) {
        if (!context.proceed()) {
            return GeneratedSql.rejected(context.reason());
        }
        return callModel(promptBuilder.buildRepairPrompt(question, context, rejectedSql, validationErrors), "repair");
    }

    private GeneratedSql callModel(String prompt, String phase) {
        try {
            return responseParser.parse(llmClient.complete(prompt));
        } catch (RuntimeException ex) {
            log.warn("sqlGeneration phase={} failed message={}", phase, ex.getMessage());
            return GeneratedSql.rejected("The request could not be completed safely.");
        }
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
