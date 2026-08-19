package com.nlda.retrieval.governance;

import java.util.List;
import java.util.Optional;

public interface GovernedBusinessRuleRepository {

    List<GovernedBusinessRule> findAll();

    List<GovernedBusinessRule> findApprovedForRetrieval(String datasourceId, String tenantId);

    Optional<GovernedBusinessRule> findById(String id);

    GovernedBusinessRule save(GovernedBusinessRule rule);

    void updateStatus(String id, ApprovalStatus status, boolean active);

    void replaceSchemaRefs(String id, List<ValidatedSchemaRef> schemaRefs);
}
