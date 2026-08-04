package com.nlda.generation;

import dev.langchain4j.model.openai.OpenAiChatModel;

public class LangChain4jOpenAiSqlLlmClient implements SqlLlmClient {

    private final OpenAiChatModel chatModel;

    public LangChain4jOpenAiSqlLlmClient(SqlGenerationProperties properties) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getModel())
                .temperature(0.0)
                .responseFormat("json_object")
                .timeout(properties.getTimeout())
                .maxRetries(properties.getMaxRetries())
                .logRequests(false)
                .logResponses(false);
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank()) {
            builder.baseUrl(properties.getBaseUrl());
        }
        this.chatModel = builder.build();
    }

    @Override
    public String complete(String prompt) {
        return chatModel.chat(prompt);
    }
}
