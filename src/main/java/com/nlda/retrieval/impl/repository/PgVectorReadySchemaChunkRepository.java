package com.nlda.retrieval.impl.repository;

import com.nlda.retrieval.contract.SchemaChunkRepository;
import com.nlda.retrieval.config.VocabularyProperties;
import com.nlda.retrieval.model.ChunkKind;
import com.nlda.retrieval.model.IndexedSchemaChunks;
import com.nlda.retrieval.model.RetrievedChunk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Repository
public class PgVectorReadySchemaChunkRepository implements SchemaChunkRepository {

    private static final Logger log = LoggerFactory.getLogger(PgVectorReadySchemaChunkRepository.class);

    private final AtomicReference<IndexedSchemaChunks> current = new AtomicReference<>();
    private final JdbcTemplate jdbcTemplate;
    private final VocabularyProperties properties;

    public PgVectorReadySchemaChunkRepository() {
        this.jdbcTemplate = null;
        this.properties = null;
    }

    public PgVectorReadySchemaChunkRepository(
            @Qualifier("retrievalJdbcTemplate") ObjectProvider<JdbcTemplate> jdbcTemplate,
            VocabularyProperties properties
    ) {
        this.jdbcTemplate = jdbcTemplate.getIfAvailable();
        this.properties = properties;
    }

    @Override
    public Optional<IndexedSchemaChunks> current() {
        IndexedSchemaChunks cached = current.get();
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<IndexedSchemaChunks> loaded = loadCurrentFromPostgres();
        loaded.ifPresent(current::set);
        return loaded;
    }

    @Override
    public void replace(IndexedSchemaChunks chunks) {
        current.set(chunks);
    }

    @Override
    public List<RetrievedChunk> fallbackChunks() {
        return current()
                .map(IndexedSchemaChunks::chunks)
                .orElse(List.of());
    }

    private Optional<IndexedSchemaChunks> loadCurrentFromPostgres() {
        if (jdbcTemplate == null || properties == null) {
            return Optional.empty();
        }
        try {
            List<String> fingerprints = jdbcTemplate.query("""
                    SELECT schema_fingerprint
                      FROM retrieval_chunk_embedding
                     WHERE datasource_id = ?
                       AND active = true
                     GROUP BY schema_fingerprint
                     ORDER BY max(updated_at) DESC
                     LIMIT 1
                    """, (rs, rowNum) -> rs.getString("schema_fingerprint"), properties.datasourceId());
            if (fingerprints.isEmpty()) {
                return Optional.empty();
            }
            String fingerprint = fingerprints.getFirst();
            List<RetrievedChunk> chunks = jdbcTemplate.query("""
                    SELECT DISTINCT ON (chunk_id)
                           chunk_id,
                           kind,
                           text,
                           schema_refs,
                           aliases
                      FROM retrieval_chunk_embedding
                     WHERE datasource_id = ?
                       AND schema_fingerprint = ?
                       AND active = true
                     ORDER BY chunk_id, updated_at DESC
                    """, (rs, rowNum) -> toChunk(rs), properties.datasourceId(), fingerprint);
            log.info("retrievalIndexLoadedFromPostgres datasourceId={} fingerprint={} chunkCount={}",
                    properties.datasourceId(), fingerprint, chunks.size());
            return Optional.of(new IndexedSchemaChunks(fingerprint, chunks));
        } catch (RuntimeException ex) {
            log.warn("retrievalIndexLoadFromPostgresFailed datasourceId={} message={}", properties.datasourceId(),
                    ex.getMessage());
            return Optional.empty();
        }
    }

    private RetrievedChunk toChunk(ResultSet rs) throws SQLException {
        return new RetrievedChunk(
                rs.getString("chunk_id"),
                rs.getString("text"),
                0.0,
                stringSet(rs.getArray("schema_refs")),
                ChunkKind.valueOf(rs.getString("kind")),
                stringSet(rs.getArray("aliases"))
        );
    }

    private Set<String> stringSet(Array array) throws SQLException {
        if (array == null) {
            return Set.of();
        }
        Object values = array.getArray();
        if (values instanceof String[] strings) {
            return new LinkedHashSet<>(Arrays.asList(strings));
        }
        return Set.of();
    }
}

