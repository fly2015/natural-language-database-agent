package com.nlda.generation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
@EnableConfigurationProperties(SqlGenerationProperties.class)
public class SqlLlmClientConfig {

    @Bean
    public SqlLlmClient sqlLlmClient(SqlGenerationProperties properties) {
        String provider = properties.getProvider().toLowerCase(Locale.ROOT).strip();
        if ("openai".equals(provider)) {
            if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
                throw new IllegalStateException("OPENAI_API_KEY is required when AGENT_LLM_PROVIDER=openai.");
            }
            return new LangChain4jOpenAiSqlLlmClient(properties);
        }
        return new DeterministicSqlLlmClient();
    }
}
