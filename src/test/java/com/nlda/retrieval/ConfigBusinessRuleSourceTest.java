package com.nlda.retrieval;

import com.nlda.retrieval.config.BusinessRuleProperties;
import com.nlda.retrieval.impl.rules.ConfigBusinessRuleSource;
import com.nlda.retrieval.model.BusinessRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigBusinessRuleSourceTest {

    @Test
    void loadsBusinessRulesFromPropertiesInsteadOfJavaLiterals() {
        BusinessRuleProperties properties = new BusinessRuleProperties();
        BusinessRuleProperties.Rule rule = new BusinessRuleProperties.Rule();
        rule.setId("Rule.Net_Revenue");
        rule.setText("Business rule: net revenue uses invoice amount minus discount.");
        rule.setSchemaRefs(List.of("Invoices", "Invoice_Discounts"));
        rule.setAliases(List.of("Net Revenue", "NRR"));
        properties.setBusinessRules(List.of(rule));

        ConfigBusinessRuleSource source = new ConfigBusinessRuleSource(properties);

        List<BusinessRule> rules = source.rules();

        assertThat(rules).hasSize(1);
        assertThat(rules.getFirst().id()).isEqualTo("rule.net_revenue");
        assertThat(rules.getFirst().schemaRefs()).containsExactlyInAnyOrder("invoices", "invoice_discounts");
        assertThat(rules.getFirst().aliases()).containsExactlyInAnyOrder("net revenue", "nrr");
    }
}
