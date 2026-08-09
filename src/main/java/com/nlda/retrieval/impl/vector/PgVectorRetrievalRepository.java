package com.nlda.retrieval.impl.vector;

import com.nlda.retrieval.config.VocabularyProperties;
import com.nlda.retrieval.contract.VectorRetrievalRepository;
import com.nlda.retrieval.model.ChunkKind;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.VectorIndexedChunk;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Repository
@ConditionalOnProperty(prefix = "agent.retrieval.vector", name = "provider", havingValue = "pgvector")
public class PgVectorRetrievalRepository implements VectorRetrievalRepository {

    private final JdbcTemplate jdbcTemplate;
    private final VocabularyProperties vocabularyProperties;

    public PgVectorRetrievalRepository(JdbcTemplate jdbcTemplate, VocabularyProperties vocabularyProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.vocabularyProperties = vocabularyProperties;
    }

    @Override
    @Transactional
    public void replace(String fingerprint, String embeddingModel, List<VectorIndexedChunk> chunks) {
        jdbcTemplate.update("""
                UPDATE retrieval_chunk_embedding
                   SET active = false,
                       updated_at = now()
                 WHERE datasource_id = ?
                   AND (schema_fingerprint <> ? OR embedding_model <> ?)
                """, vocabularyProperties.datasourceId(), fingerprint, embeddingModel);

        for (VectorIndexedChunk indexed : chunks) {
            RetrievedChunk chunk = indexed.chunk();
            jdbcTemplate.update("""
                    INSERT INTO retrieval_chunk_embedding (
                        chunk_id,
                        datasource_id,
                        schema_fingerprint,
                        kind,
                        text,
                        schema_refs,
                        aliases,
                        content_hash,
                        embedding_model,
                        embedding_dimensions,
                        embedding,
                        active,
                        updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, true, now())
                    ON CONFLICT (datasource_id, schema_fingerprint, chunk_id, embedding_model)
                    DO UPDATE SET
                        kind = EXCLUDED.kind,
                        text = EXCLUDED.text,
                        schema_refs = EXCLUDED.schema_refs,
                        aliases = EXCLUDED.aliases,
                        content_hash = EXCLUDED.content_hash,
                        embedding_dimensions = EXCLUDED.embedding_dimensions,
                        embedding = EXCLUDED.embedding,
                        active = true,
                        updated_at = now()
                    """,
                    chunk.id(),
                    vocabularyProperties.datasourceId(),
                    fingerprint,
                    chunk.kind().name(),
                    chunk.text(),
                    chunk.schemaRefs().toArray(String[]::new),
                    chunk.aliases().toArray(String[]::new),
                    indexed.contentHash(),
                    embeddingModel,
                    indexed.embedding().length,
                    vectorLiteral(indexed.embedding())
            );
        }
    }

    @Override
    public List<RetrievedChunk> search(float[] queryEmbedding, String fingerprint, String embeddingModel, int limit) {
        return jdbcTemplate.query("""
                SELECT chunk_id,
                       kind,
                       text,
                       schema_refs,
                       aliases,
                       1.0 - (embedding <=> ?::vector) AS score
                  FROM retrieval_chunk_embedding
                 WHERE datasource_id = ?
                   AND schema_fingerprint = ?
                   AND embedding_model = ?
                   AND active = true
                 ORDER BY embedding <=> ?::vector
                 LIMIT ?
                """,
                (rs, rowNum) -> toChunk(rs),
                vectorLiteral(queryEmbedding),
                vocabularyProperties.datasourceId(),
                fingerprint,
                embeddingModel,
                vectorLiteral(queryEmbedding),
                limit
        );
    }

    private RetrievedChunk toChunk(ResultSet rs) throws SQLException {
        return new RetrievedChunk(
                rs.getString("chunk_id"),
                rs.getString("text"),
                Math.max(0.0, rs.getDouble("score")),
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

    private String vectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }
}
