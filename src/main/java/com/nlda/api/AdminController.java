package com.nlda.api;

import com.nlda.retrieval.contract.VectorRetrievalRepository;
import com.nlda.retrieval.governance.BusinessRuleGovernanceService;
import com.nlda.retrieval.governance.GovernedBusinessRule;
import com.nlda.retrieval.governance.RebuildResult;
import com.nlda.retrieval.governance.RetrievalIndexRebuildService;
import com.nlda.retrieval.model.RetrievalIndexRecord;
import com.nlda.retrieval.model.schema.SchemaMetadataSnapshot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final BusinessRuleGovernanceService governanceService;
    private final RetrievalIndexRebuildService rebuildService;
    private final VectorRetrievalRepository vectorRepository;

    public AdminController(
            BusinessRuleGovernanceService governanceService,
            RetrievalIndexRebuildService rebuildService,
            VectorRetrievalRepository vectorRepository
    ) {
        this.governanceService = governanceService;
        this.rebuildService = rebuildService;
        this.vectorRepository = vectorRepository;
    }

    @GetMapping("/business-rules")
    public List<GovernedBusinessRule> businessRules() {
        return governanceService.listRules();
    }

    @PostMapping("/business-rules")
    public ResponseEntity<GovernedBusinessRule> createDraft(@RequestBody AdminBusinessRuleRequest request) {
        return ResponseEntity.ok(saveDraft(request));
    }

    @PutMapping("/business-rules/{id}")
    public ResponseEntity<GovernedBusinessRule> updateDraft(
            @PathVariable String id,
            @RequestBody AdminBusinessRuleRequest request
    ) {
        AdminBusinessRuleRequest merged = new AdminBusinessRuleRequest(id, request.name(), request.text(),
                request.owner(), request.version(), request.effectiveStart(), request.effectiveEnd(),
                request.datasourceId(), request.tenantId(), request.schemaRefs(), request.aliases());
        return ResponseEntity.ok(saveDraft(merged));
    }

    @PostMapping("/business-rules/{id}/approve")
    public GovernedBusinessRule approve(@PathVariable String id) {
        return governanceService.approve(id);
    }

    @PostMapping("/business-rules/{id}/deactivate")
    public GovernedBusinessRule deactivate(@PathVariable String id) {
        return governanceService.deactivate(id);
    }

    @PostMapping("/business-rules/{id}/reindex")
    public RebuildResult reindexRule(@PathVariable String id) {
        return governanceService.reindex(id);
    }

    @GetMapping("/schema")
    public SchemaSummary schema() {
        SchemaMetadataSnapshot snapshot = rebuildService.currentSchemaSnapshot();
        return new SchemaSummary(snapshot.fingerprint(), snapshot.tables().size(), snapshot.tables());
    }

    @PostMapping("/schema/refresh")
    public RebuildResult refreshSchema() {
        return rebuildService.refreshSchemaAndRebuildAffected();
    }

    @PostMapping("/retrieval-index/rebuild")
    public RebuildResult rebuildAll() {
        return rebuildService.rebuildAll("manual full rebuild", "admin.retrieval-index");
    }

    @PostMapping("/retrieval-index/business-rules/rebuild")
    public RebuildResult rebuildBusinessRules() {
        return rebuildService.rebuildBusinessRules();
    }

    @GetMapping("/retrieval-index")
    public List<RetrievalIndexRecord> retrievalIndex() {
        return vectorRepository.records();
    }

    @GetMapping("/retrieval-diagnostics")
    public DiagnosticsSummary retrievalDiagnostics() {
        return new DiagnosticsSummary(vectorRepository.records().size(), "Use query trace logs for selected chunks and scores.");
    }

    private GovernedBusinessRule saveDraft(AdminBusinessRuleRequest request) {
        return governanceService.saveDraft(request.id(), request.name(), request.text(), request.owner(),
                request.version(), request.effectiveStart(), request.effectiveEnd(), request.datasourceId(),
                request.tenantId(), request.schemaRefs(), request.aliases());
    }

    public record SchemaSummary(String fingerprint, int tableCount, Object tables) {
    }

    public record DiagnosticsSummary(int indexedRecordCount, String message) {
    }
}
