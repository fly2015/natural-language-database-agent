package com.nlda.retrieval.governance;

import com.nlda.audit.AuditContext;
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

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
        long started = System.nanoTime();
        log.info("operationEvent=retrieval.index_rebuild.started traceId={} status=STARTED reason=\"{}\" triggerSource={} embeddingModel={}",
                AuditContext.traceId(), safe(reason), triggerSource, embeddingClient == null ? "" : embeddingClient.model());
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
            log.info("operationEvent=retrieval.index_rebuild.completed traceId={} status=OK latencyMs={} reason=\"{}\" triggerSource={} fingerprint={} chunkCount={} affectedRuleCount={} embeddingModel={}",
                    AuditContext.traceId(), elapsedMs(started), safe(reason), triggerSource, indexed.fingerprint(),
                    indexed.chunks().size(), ruleIds.size(), embeddingClient == null ? "" : embeddingClient.model());
            return result;
        } catch (RuntimeException ex) {
            RebuildResult result = new RebuildResult("FAILED", reason, triggerSource, "", 0, List.of(),
                    ex.getMessage());
            logResult(result, ex.getMessage());
            log.warn("operationEvent=retrieval.index_rebuild.completed traceId={} status=FAILED latencyMs={} reason=\"{}\" triggerSource={} embeddingModel={} error=\"{}\"",
                    AuditContext.traceId(), elapsedMs(started), safe(reason), triggerSource,
                    embeddingClient == null ? "" : embeddingClient.model(), safe(ex.getMessage()));
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

    public RebuildStatus lastRebuildStatus() {
        try {
            return jdbcTemplate.query("""
                    SELECT outcome,
                           reason,
                           trigger_source,
                           schema_fingerprint,
                           embedding_model,
                           message,
                           created_at
                      FROM retrieval_index_rebuild_log
                     ORDER BY created_at DESC, id DESC
                     LIMIT 1
                    """, rs -> {
                if (!rs.next()) {
                    return RebuildStatus.empty();
                }
                return new RebuildStatus(
                        rs.getString("outcome"),
                        rs.getString("reason"),
                        rs.getString("trigger_source"),
                        rs.getString("schema_fingerprint"),
                        rs.getString("embedding_model"),
                        rs.getString("message"),
                        localDateTime(rs.getTimestamp("created_at"))
                );
            });
        } catch (RuntimeException ex) {
            log.warn("retrievalIndexLastRebuildStatusFailed message={}", ex.getMessage());
            return RebuildStatus.empty();
        }
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
        log.info("operationEvent=retrieval.index_rebuild.result traceId={} status={} reason=\"{}\" triggerSource={} fingerprint={} chunkCount={} affectedRuleCount={}",
                AuditContext.traceId(), result.outcome(), safe(result.reason()), result.triggerSource(),
                result.schemaFingerprint(), result.chunkCount(), result.affectedRuleIds().size());
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

    private LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    private long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private String safe(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() <= 240 ? sanitized : sanitized.substring(0, 240);
    }
}
