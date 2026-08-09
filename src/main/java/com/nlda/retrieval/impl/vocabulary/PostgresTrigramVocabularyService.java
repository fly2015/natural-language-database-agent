package com.nlda.retrieval.impl.vocabulary;

import com.nlda.retrieval.config.VocabularyProperties;
import com.nlda.retrieval.contract.RetrievalVocabularyIndexService;
import com.nlda.retrieval.contract.VocabularyCorrectionService;
import com.nlda.retrieval.model.ChunkKind;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.VocabularySourceType;
import com.nlda.retrieval.model.schema.SchemaMetadataSnapshot;
import com.nlda.retrieval.model.schema.SchemaTableMetadata;
import com.nlda.retrieval.query.CorrectionCandidate;
import com.nlda.retrieval.text.TextNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "agent.retrieval.vocabulary", name = "provider", havingValue = "postgres-trgm")
public class PostgresTrigramVocabularyService implements VocabularyCorrectionService, RetrievalVocabularyIndexService {

    private static final Logger log = LoggerFactory.getLogger(PostgresTrigramVocabularyService.class);

    private final JdbcTemplate jdbcTemplate;
    private final VocabularyProperties properties;
    private final TextNormalizer textNormalizer;

    public PostgresTrigramVocabularyService(
            JdbcTemplate jdbcTemplate,
            VocabularyProperties properties,
            TextNormalizer textNormalizer
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.textNormalizer = textNormalizer;
    }

    @Override
    public List<CorrectionCandidate> correct(List<String> tokens) {
        List<CorrectionCandidate> corrections = new ArrayList<>();
        for (String token : tokens) {
            if (token.length() < 3) {
                continue;
            }
            List<CorrectionCandidate> candidates = findCandidates(token);
            if (candidates.isEmpty()) {
                continue;
            }
            CorrectionCandidate best = candidates.getFirst();
            corrections.add(best);
        }
        return List.copyOf(corrections);
    }

    @Override
    @Transactional
    public void rebuild(SchemaMetadataSnapshot snapshot, List<RetrievedChunk> chunks) {
        String fingerprint = snapshot.fingerprint();
        Instant now = Instant.now();
        List<VocabularyEntry> entries = entries(snapshot, chunks, fingerprint, now);

        jdbcTemplate.update("""
                UPDATE retrieval_vocabulary
                   SET active = false,
                       updated_at = ?
                 WHERE datasource_id = ?
                   AND schema_fingerprint <> ?
                """, Timestamp.from(now), properties.datasourceId(), fingerprint);

        for (VocabularyEntry entry : entries) {
            jdbcTemplate.update("""
                    INSERT INTO retrieval_vocabulary (
                        datasource_id,
                        schema_fingerprint,
                        source_type,
                        source_id,
                        term,
                        normalized_term,
                        active,
                        updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, true, ?)
                    ON CONFLICT (datasource_id, schema_fingerprint, source_type, source_id, normalized_term)
                    DO UPDATE SET
                        term = EXCLUDED.term,
                        active = true,
                        updated_at = EXCLUDED.updated_at
                    """,
                    properties.datasourceId(),
                    fingerprint,
                    entry.sourceType().name(),
                    entry.sourceId(),
                    entry.term(),
                    entry.normalizedTerm(),
                    Timestamp.from(now)
            );
        }
        log.info("retrievalVocabularyRebuild provider=postgres-trgm datasourceId={} fingerprint={} termCount={}",
                properties.datasourceId(), fingerprint, entries.size());
    }

    private List<CorrectionCandidate> findCandidates(String token) {
        String normalized = textNormalizer.normalize(token);
        try {
            List<CorrectionCandidate> candidates = jdbcTemplate.query("""
                    SELECT source_type,
                           source_id,
                           term,
                           normalized_term,
                           CASE
                               WHEN normalized_term = ? THEN 1.0
                               ELSE similarity(normalized_term, ?)
                           END AS score
                      FROM retrieval_vocabulary
                     WHERE datasource_id = ?
                       AND active = true
                       AND (
                           normalized_term = ?
                           OR similarity(normalized_term, ?) >= ?
                       )
                     ORDER BY score DESC, length(normalized_term), normalized_term
                     LIMIT ?
                    """,
                    (rs, rowNum) -> toCandidate(token, normalized, rs),
                    normalized,
                    normalized,
                    properties.datasourceId(),
                    normalized,
                    normalized,
                    properties.similarityThreshold(),
                    properties.maxCandidates()
            );
            if (candidates.isEmpty()) {
                return List.of();
            }
            CorrectionCandidate best = candidates.getFirst();
            boolean ambiguous = candidates.stream()
                    .skip(1)
                    .anyMatch(candidate -> Math.abs(candidate.score() - best.score()) <= properties.ambiguityDelta());
            return List.of(new CorrectionCandidate(
                    best.original(),
                    best.corrected(),
                    best.sourceType(),
                    best.sourceId(),
                    best.score(),
                    best.exact(),
                    ambiguous
            ));
        } catch (DataAccessException ex) {
            log.warn("retrievalVocabularyCorrectionFailed token={} message={}", token, ex.getMessage());
            return List.of();
        }
    }

    private CorrectionCandidate toCandidate(String original, String normalized, ResultSet rs) throws SQLException {
        String corrected = rs.getString("normalized_term");
        return new CorrectionCandidate(
                original,
                corrected,
                VocabularySourceType.valueOf(rs.getString("source_type")),
                rs.getString("source_id"),
                rs.getDouble("score"),
                corrected.equals(normalized),
                false
        );
    }

    private List<VocabularyEntry> entries(
            SchemaMetadataSnapshot snapshot,
            List<RetrievedChunk> chunks,
            String fingerprint,
            Instant now
    ) {
        Map<String, VocabularyEntry> entries = new LinkedHashMap<>();
        for (SchemaTableMetadata table : snapshot.tables()) {
            add(entries, new VocabularyEntry(VocabularySourceType.SCHEMA, "schema." + table.name(),
                    table.name(), textNormalizer.normalize(table.name()), fingerprint, now));
            for (var column : table.columns()) {
                add(entries, new VocabularyEntry(VocabularySourceType.COLUMN,
                        "column." + table.name() + "." + column.name(),
                        column.name(), textNormalizer.normalize(column.name()), fingerprint, now));
                add(entries, new VocabularyEntry(VocabularySourceType.COLUMN,
                        "column." + table.name() + "." + column.name(),
                        table.name() + "." + column.name(),
                        textNormalizer.normalize(table.name() + " " + column.name()), fingerprint, now));
            }
        }
        for (RetrievedChunk chunk : chunks) {
            for (String alias : chunk.aliases()) {
                add(entries, new VocabularyEntry(VocabularySourceType.ALIAS, chunk.id(),
                        alias, textNormalizer.normalize(alias), fingerprint, now));
            }
            if (chunk.kind() == ChunkKind.BUSINESS_RULE) {
                for (String term : textNormalizer.tokens(chunk.text())) {
                    if (term.length() >= 4) {
                        add(entries, new VocabularyEntry(VocabularySourceType.BUSINESS_RULE, chunk.id(),
                                term, term, fingerprint, now));
                    }
                }
            }
            if (chunk.kind() == ChunkKind.JOIN_PATH) {
                for (String schemaRef : chunk.schemaRefs()) {
                    for (String term : splitSchemaRef(schemaRef)) {
                        add(entries, new VocabularyEntry(VocabularySourceType.JOIN_PATH, chunk.id(),
                                term, textNormalizer.normalize(term), fingerprint, now));
                    }
                }
            }
        }
        return List.copyOf(entries.values());
    }

    private void add(Map<String, VocabularyEntry> entries, VocabularyEntry entry) {
        if (entry.normalizedTerm().isBlank() || entry.normalizedTerm().length() < 3) {
            return;
        }
        entries.put(entry.sourceType() + "|" + entry.sourceId() + "|" + entry.normalizedTerm(), entry);
    }

    private Set<String> splitSchemaRef(String schemaRef) {
        Set<String> values = new LinkedHashSet<>();
        values.add(schemaRef);
        values.add(schemaRef.replace('.', ' '));
        values.add(schemaRef.replace('_', ' '));
        for (String part : schemaRef.split("[._]")) {
            values.add(part);
        }
        return values;
    }

    private record VocabularyEntry(
            VocabularySourceType sourceType,
            String sourceId,
            String term,
            String normalizedTerm,
            String schemaFingerprint,
            Instant updatedAt
    ) {
    }
}
