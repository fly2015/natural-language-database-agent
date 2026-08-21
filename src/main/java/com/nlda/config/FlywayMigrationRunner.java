package com.nlda.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FlywayMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationRunner.class);

    private final DataSource appDataSource;
    private final ObjectProvider<DataSource> governanceDataSource;
    private final ObjectProvider<DataSource> retrievalDataSource;
    private final AgentMigrationProperties migrationProperties;

    public FlywayMigrationRunner(
            @Qualifier("appDataSource") DataSource appDataSource,
            @Qualifier("governanceDataSource") ObjectProvider<DataSource> governanceDataSource,
            @Qualifier("retrievalDataSource") ObjectProvider<DataSource> retrievalDataSource,
            AgentMigrationProperties migrationProperties
    ) {
        this.appDataSource = appDataSource;
        this.governanceDataSource = governanceDataSource;
        this.retrievalDataSource = retrievalDataSource;
        this.migrationProperties = migrationProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (migrationProperties.isAppEnabled()) {
            migrate(appDataSource, "classpath:db/datasource/migration", "flyway_datasource_schema_history");
        } else {
            log.info("migrationSkipped datasource=app");
        }
        DataSource configuredGovernanceDataSource = governanceDataSource.getIfAvailable();
        if (!migrationProperties.isGovernanceEnabled()) {
            log.info("migrationSkipped datasource=governance");
        } else if (configuredGovernanceDataSource != null) {
            migrate(configuredGovernanceDataSource, "classpath:db/governance/migration",
                    "flyway_governance_schema_history");
        } else {
            migrate(appDataSource, "classpath:db/governance/migration", "flyway_governance_schema_history");
        }
        DataSource configuredRetrievalDataSource = retrievalDataSource.getIfAvailable();
        if (!migrationProperties.isRetrievalEnabled()) {
            log.info("migrationSkipped datasource=retrieval");
        } else if (configuredRetrievalDataSource != null) {
            migrate(configuredRetrievalDataSource, "classpath:db/retrieval/migration",
                    "flyway_retrieval_schema_history");
        }
    }

    private void migrate(DataSource dataSource, String location, String table) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations(location)
                .table(table)
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load()
                .migrate();
    }
}
