package com.nlda.retrieval.impl.rules;

import com.nlda.retrieval.config.BusinessRuleProperties;
import com.nlda.retrieval.contract.BusinessRuleSource;
import com.nlda.retrieval.model.BusinessRule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "agent.retrieval", name = "business-rule-source", havingValue = "yaml", matchIfMissing = true)
public class ConfigBusinessRuleSource implements BusinessRuleSource {

    private final BusinessRuleProperties properties;

    public ConfigBusinessRuleSource(BusinessRuleProperties properties) {
        this.properties = properties;
    }

    @Override
    public List<BusinessRule> rules() {
        return properties.getBusinessRules().stream()
                .filter(rule -> !blank(rule.getId()) && !blank(rule.getText()))
                .map(rule -> new BusinessRule(
                        normalizeId(rule.getId()),
                        rule.getText().strip(),
                        normalizeSet(rule.getSchemaRefs()),
                        normalizeSet(rule.getAliases())
                ))
                .toList();
    }

    private Set<String> normalizeSet(List<String> values) {
        return values.stream()
                .filter(value -> !blank(value))
                .map(value -> value.toLowerCase(Locale.ROOT).strip())
                .collect(Collectors.toUnmodifiableSet());
    }

    private String normalizeId(String value) {
        return value.toLowerCase(Locale.ROOT).strip();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
