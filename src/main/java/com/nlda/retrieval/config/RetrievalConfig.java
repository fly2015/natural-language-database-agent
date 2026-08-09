package com.nlda.retrieval.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({BusinessRuleProperties.class, VocabularyProperties.class, EmbeddingProperties.class})
public class RetrievalConfig {
}
