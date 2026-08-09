package com.nlda.retrieval.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "agent.retrieval")
public class BusinessRuleProperties {

    private List<Rule> businessRules = new ArrayList<>();

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
}
