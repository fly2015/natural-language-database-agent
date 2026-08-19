package com.nlda.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "agent.retrieval")
public class BusinessRuleProperties {

    private Source businessRuleSource = Source.YAML;
    private String datasourceId = "default";
    private String tenantId = "";
    private boolean consumeOnly = true;
    private List<Rule> businessRules = new ArrayList<>();

    public Source getBusinessRuleSource() {
        return businessRuleSource;
    }

    public void setBusinessRuleSource(Source businessRuleSource) {
        this.businessRuleSource = businessRuleSource == null ? Source.YAML : businessRuleSource;
    }

    public String getDatasourceId() {
        return datasourceId;
    }

    public void setDatasourceId(String datasourceId) {
        this.datasourceId = datasourceId == null || datasourceId.isBlank() ? "default" : datasourceId.strip();
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId == null ? "" : tenantId.strip();
    }

    public boolean isConsumeOnly() {
        return consumeOnly;
    }

    public void setConsumeOnly(boolean consumeOnly) {
        this.consumeOnly = consumeOnly;
    }

    public List<Rule> getBusinessRules() {
        return businessRules;
    }

    public void setBusinessRules(List<Rule> businessRules) {
        this.businessRules = businessRules == null ? new ArrayList<>() : businessRules;
    }

    public static class Rule {

        private String id = "";
        private String text = "";
        private List<String> schemaRefs = new ArrayList<>();
        private List<String> aliases = new ArrayList<>();

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public List<String> getSchemaRefs() {
            return schemaRefs;
        }

        public void setSchemaRefs(List<String> schemaRefs) {
            this.schemaRefs = schemaRefs == null ? new ArrayList<>() : schemaRefs;
        }

        public List<String> getAliases() {
            return aliases;
        }

        public void setAliases(List<String> aliases) {
            this.aliases = aliases == null ? new ArrayList<>() : aliases;
        }
    }

    public enum Source {
        YAML,
        DATABASE
    }
}
