package com.nlda.retrieval;

import com.nlda.retrieval.config.BusinessRuleProperties;
import com.nlda.retrieval.governance.ApprovalStatus;
import com.nlda.retrieval.governance.GovernedBusinessRule;
import com.nlda.retrieval.governance.GovernedBusinessRuleRepository;
import com.nlda.retrieval.impl.rules.DatabaseBusinessRuleSource;
import com.nlda.retrieval.model.BusinessRule;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DatabaseBusinessRuleSourceTest {

    @Autowired
    private GovernedBusinessRuleRepository repository;

    @Test
    void loadsOnlyApprovedActiveEffectiveRulesForDatasourceAndTenant() {
        repository.save(rule("rule.approved", ApprovalStatus.APPROVED, true, "test", "", null, null));
        repository.save(rule("rule.draft", ApprovalStatus.DRAFT, true, "test", "", null, null));
        repository.save(rule("rule.inactive", ApprovalStatus.APPROVED, false, "test", "", null, null));
        repository.save(rule("rule.future", ApprovalStatus.APPROVED, true, "test", "",
                LocalDateTime.now().plusDays(1), null));
        repository.save(rule("rule.other_datasource", ApprovalStatus.APPROVED, true, "warehouse", "", null, null));

        BusinessRuleProperties properties = new BusinessRuleProperties();
        properties.setDatasourceId("test");
        DatabaseBusinessRuleSource source = new DatabaseBusinessRuleSource(repository, properties);

        List<BusinessRule> rules = source.rules();

        assertThat(rules).extracting(BusinessRule::id).containsExactly("rule.approved");
        assertThat(rules.getFirst().schemaRefs()).containsExactly("orders");
        assertThat(rules.getFirst().aliases()).containsExactly("revenue");
    }

    private GovernedBusinessRule rule(
            String id,
            ApprovalStatus status,
            boolean active,
            String datasourceId,
            String tenantId,
            LocalDateTime effectiveStart,
            LocalDateTime effectiveEnd
    ) {
        return new GovernedBusinessRule(id, id, "Business rule: revenue uses orders.total_amount.", "owner", 1,
                status, effectiveStart, effectiveEnd, datasourceId, tenantId, active, Set.of("orders"),
                Set.of("revenue"), "", null, null);
    }
}
