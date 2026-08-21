package com.nlda.retrieval.governance;

import com.nlda.retrieval.contract.EmbeddingClient;
import com.nlda.retrieval.contract.SchemaMetadataProvider;
import com.nlda.retrieval.index.SchemaIndexService;
import com.nlda.retrieval.model.IndexedSchemaChunks;
import com.nlda.retrieval.model.schema.SchemaMetadataSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RetrievalIndexRebuildService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalIndexRebuildService.class);

    private final SchemaIndexService schemaIndexService;
    private final SchemaMetadataProvider metadataProvider;
    private final GovernedBusinessRuleRepository ruleRepository;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingClient embeddingClient;

    public RetrievalIndexRebuildService(
            SchemaIndexService schemaIndexService,
            SchemaMetadataProvider metadataProvider,
            GovernedBusinessRuleRepository ruleRepository,
            @Qualifier("governanceJdbcTemplate") JdbcTemplate jdbcTemplate,
            ObjectProvider<EmbeddingClient> embeddingClient
    ) {
        this.schemaIndexService = schemaIndexService;
        this.metadataProvider = metadataProvider;
        this.ruleRepository = ruleRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingClient = embeddingClient.getIfAvailable();
    }

    public RebuildResult rebuildAll(String reason, String triggerSource) {
        try {
            SchemaMetadataSnapshot snapshot = metadataProvider.extract();
            revalidateSchemaRefs(snapshot);
            IndexedSchemaChunks indexed = schemaIndexService.refresh();
            List<String> ruleIds = indexed.chunks().stream()
                    .filter(chunk -> chunk.kind() == com.nlda.retrieval.model.ChunkKind.BUSINESS_RULE)
                    .map(com.nlda.retrieval.model.RetrievedChunk::id)
                    .toList();
            RebuildResult result = new RebuildResult("OK", reason, triggerSource, indexed.fingerprint(),
                    indexed.chunks().size(), ruleIds, "retrieval indexes rebuilt");
            logResult(result, "");
            return result;
        } catch (RuntimeException ex) {
            RebuildResult result = new RebuildResult("FAILED", reason, triggerSource, "", 0, List.of(),
                    ex.getMessage());
            logResult(result, ex.getMessage());
            throw ex;
        }
    }

    public RebuildResult refreshSchemaAndRebuildAffected() {
        return rebuildAll("schema refresh", "admin.schema");
    }

    public RebuildResult rebuildBusinessRules() {
        return rebuildAll("business rule rebuild", "admin.business-rules");
    }

    public RebuildResult rebuildSelectedRule(String ruleId) {
        RebuildResult result = rebuildAll("selected business rule rebuild", "admin.business-rules");
        return new RebuildResult(result.outcome(), result.reason(), result.triggerSource(), result.schemaFingerprint(),
                result.chunkCount(), List.of(ruleId), result.message());
    }

    public SchemaMetadataSnapshot currentSchemaSnapshot() {
        return metadataProvider.extract();
    }

    private void revalidateSchemaRefs(SchemaMetadataSnapshot snapshot) {
        for (GovernedBusinessRule rule : ruleRepository.findAll()) {
            List<ValidatedSchemaRef> refs = rule.schemaRefs().stream()
                    .map(ref -> new ValidatedSchemaRef(ref, snapshot.containsSchemaRef(ref), snapshot.fingerprint()))
                    .toList();
            ruleRepository.replaceSchemaRefs(rule.id(), refs);
        }
    }

    private void logResult(RebuildResult result, String errorMessage) {
        log.info("retrievalIndexRebuild reason={} triggerSource={} outcome={} fingerprint={} chunkCount={} affectedRules={}",
                result.reason(), result.triggerSource(), result.outcome(), result.schemaFingerprint(),
                result.chunkCount(), result.affectedRuleIds());
        try {
            jdbcTemplate.update("""
                    INSERT INTO retrieval_index_rebuild_log (
                        reason, trigger_source, affected_rule_ids, affected_chunk_ids, schema_fingerprint,
                        embedding_model, outcome, message
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    result.reason(),
                    result.triggerSource(),
                    String.join(",", result.affectedRuleIds()),
                    "",
                    result.schemaFingerprint(),
                    embeddingClient == null ? "" : embeddingClient.model(),
                    result.outcome(),
                    errorMessage == null || errorMessage.isBlank() ? result.message() : errorMessage);
        } catch (RuntimeException ex) {
            log.warn("retrievalIndexRebuildLogFailed message={}", ex.getMessage());
        }
    }
}
