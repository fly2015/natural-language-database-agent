package com.nlda.retrieval;

import com.nlda.retrieval.config.BusinessRuleProperties;
import com.nlda.retrieval.config.EmbeddingProperties;
import com.nlda.retrieval.config.RetrievalConfig;
import com.nlda.retrieval.config.VocabularyProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileConfigurationTest {

    @Test
    void defaultProfileUsesLocalYamlAndInMemoryProviders() {
        contextRunner()
                .run(context -> {
                    Environment environment = context.getEnvironment();
                    BusinessRuleProperties rules = context.getBean(BusinessRuleProperties.class);
                    VocabularyProperties vocabulary = context.getBean(VocabularyProperties.class);
                    EmbeddingProperties embedding = context.getBean(EmbeddingProperties.class);

                    assertThat(environment.getActiveProfiles()).isEmpty();
                    assertThat(environment.getDefaultProfiles()).contains("full-local");
                    assertThat(rules.getBusinessRuleSource()).isEqualTo(BusinessRuleProperties.Source.YAML);
                    assertThat(rules.getBusinessRules()).isNotEmpty();
                    assertThat(vocabulary.provider()).isEqualTo("in-memory");
                    assertThat(environment.getProperty("agent.retrieval.vector.provider")).isEqualTo("in-memory");
                    assertThat(embedding.provider()).isEqualTo("fake");
                    assertThat(embedding.model()).isEqualTo("fake-hash-embedding");
                    assertThat(environment.getProperty("agent.llm.provider")).isEqualTo("deterministic");
                });
    }

    @Test
    void fullPostgresProfileUsesPostgresForAllDatabasePurposes() {
        contextRunner()
                .withPropertyValues("spring.profiles.active=full-postgres")
                .run(context -> {
                    Environment environment = context.getEnvironment();
                    BusinessRuleProperties rules = context.getBean(BusinessRuleProperties.class);
                    VocabularyProperties vocabulary = context.getBean(VocabularyProperties.class);
                    EmbeddingProperties embedding = context.getBean(EmbeddingProperties.class);

                    assertThat(environment.getActiveProfiles()).contains("full-postgres");
                    assertThat(rules.getBusinessRuleSource()).isEqualTo(BusinessRuleProperties.Source.DATABASE);
                    assertThat(vocabulary.provider()).isEqualTo("postgres-trgm");
                    assertThat(environment.getProperty("agent.retrieval.vector.provider")).isEqualTo("pgvector");
                    assertThat(embedding.provider()).isEqualTo("openai");
                    assertThat(embedding.model()).isEqualTo("text-embedding-3-small");
                    assertThat(embedding.dimensions()).isEqualTo(64);
                    assertThat(environment.getProperty("spring.datasource.driver-class-name"))
                            .isEqualTo("org.postgresql.Driver");
                    assertThat(environment.getProperty("spring.datasource.url"))
                            .contains("nlda_app");
                    assertThat(environment.getProperty("agent.datasource.governance.url"))
                            .contains("nlda_governance");
                    assertThat(environment.getProperty("agent.datasource.retrieval.url"))
                            .contains("nlda_retrieval");
                    assertThat(environment.getProperty("agent.migration.app-enabled", Boolean.class))
                            .isTrue();
                    assertThat(environment.getProperty("agent.migration.governance-enabled", Boolean.class))
                            .isFalse();
                    assertThat(environment.getProperty("agent.migration.retrieval-enabled", Boolean.class))
                            .isFalse();
                });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(RetrievalConfig.class);
    }
}
