package com.nlda.retrieval.impl.rules;

import com.nlda.retrieval.config.BusinessRuleProperties;
import com.nlda.retrieval.contract.BusinessRuleSource;
import com.nlda.retrieval.governance.GovernedBusinessRule;
import com.nlda.retrieval.governance.GovernedBusinessRuleRepository;
import com.nlda.retrieval.model.BusinessRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "agent.retrieval", name = "business-rule-source", havingValue = "database")
public class DatabaseBusinessRuleSource implements BusinessRuleSource {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBusinessRuleSource.class);

    private final GovernedBusinessRuleRepository repository;
    private final BusinessRuleProperties properties;

    public DatabaseBusinessRuleSource(GovernedBusinessRuleRepository repository, BusinessRuleProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public List<BusinessRule> rules() {
        List<GovernedBusinessRule> governedRules = repository.findApprovedForRetrieval(
                properties.getDatasourceId(),
                properties.getTenantId()
        );
        log.debug("businessRuleSource source=database datasourceId={} tenantId={} count={}",
                properties.getDatasourceId(), properties.getTenantId(), governedRules.size());
        return governedRules.stream()
                .map(rule -> new BusinessRule(
                        rule.id(),
                        rule.text(),
                        rule.schemaRefs(),
                        rule.aliases()
                ))
                .toList();
    }
}
