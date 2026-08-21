package com.nlda.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "agent.migration")
public class AgentMigrationProperties {

    private boolean appEnabled = true;
    private boolean governanceEnabled = true;
    private boolean retrievalEnabled = true;

    public boolean isAppEnabled() {
        return appEnabled;
    }

    public void setAppEnabled(boolean appEnabled) {
        this.appEnabled = appEnabled;
    }

    public boolean isGovernanceEnabled() {
        return governanceEnabled;
    }

    public void setGovernanceEnabled(boolean governanceEnabled) {
        this.governanceEnabled = governanceEnabled;
    }

    public boolean isRetrievalEnabled() {
        return retrievalEnabled;
    }

    public void setRetrievalEnabled(boolean retrievalEnabled) {
        this.retrievalEnabled = retrievalEnabled;
    }
}
