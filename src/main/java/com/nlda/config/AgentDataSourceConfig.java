package com.nlda.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class AgentDataSourceConfig {

    @Bean(name = "appJdbcTemplate")
    @Primary
    public JdbcTemplate appJdbcTemplate(@Qualifier("appDataSource") DataSource appDataSource) {
        return new JdbcTemplate(appDataSource);
    }

    @Bean(name = "appDataSourceProperties")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties appDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "appDataSource")
    @Primary
    public DataSource appDataSource(@Qualifier("appDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "governanceDataSourceProperties")
    @ConditionalOnProperty(prefix = "agent.datasource.governance", name = "url")
    @ConfigurationProperties(prefix = "agent.datasource.governance")
    public DataSourceProperties governanceDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "governanceDataSource")
    @ConditionalOnBean(name = "governanceDataSourceProperties")
    public DataSource governanceDataSource(
            @Qualifier("governanceDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "governanceJdbcTemplate")
    @ConditionalOnBean(name = "governanceDataSource")
    public JdbcTemplate governanceJdbcTemplate(
            @Qualifier("governanceDataSource") DataSource governanceDataSource
    ) {
        return new JdbcTemplate(governanceDataSource);
    }

    @Bean(name = "governanceJdbcTemplate")
    @ConditionalOnMissingBean(name = "governanceJdbcTemplate")
    public JdbcTemplate localGovernanceJdbcTemplate(@Qualifier("appJdbcTemplate") JdbcTemplate appJdbcTemplate) {
        return appJdbcTemplate;
    }

    @Bean(name = "retrievalDataSourceProperties")
    @ConditionalOnProperty(prefix = "agent.datasource.retrieval", name = "url")
    @ConfigurationProperties(prefix = "agent.datasource.retrieval")
    public DataSourceProperties retrievalDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "retrievalDataSource")
    @ConditionalOnBean(name = "retrievalDataSourceProperties")
    public DataSource retrievalDataSource(
            @Qualifier("retrievalDataSourceProperties") DataSourceProperties properties
    ) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "retrievalJdbcTemplate")
    @ConditionalOnBean(name = "retrievalDataSource")
    public JdbcTemplate retrievalJdbcTemplate(
            @Qualifier("retrievalDataSource") DataSource retrievalDataSource
    ) {
        return new JdbcTemplate(retrievalDataSource);
    }
}
