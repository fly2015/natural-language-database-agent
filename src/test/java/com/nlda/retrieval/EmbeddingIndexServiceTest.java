package com.nlda.retrieval;

import com.nlda.retrieval.contract.EmbeddingClient;
import com.nlda.retrieval.contract.VectorRetrievalRepository;
import com.nlda.retrieval.impl.vector.InMemoryVectorRetrievalRepository;
import com.nlda.retrieval.index.ChunkCanonicalizer;
import com.nlda.retrieval.index.EmbeddingIndexService;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.VectorIndexedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmbeddingIndexServiceTest {

    @Test
    void canonicalChunkHashIsStableAndChangesWithContent() {
        ChunkCanonicalizer canonicalizer = new ChunkCanonicalizer();
        RetrievedChunk first = RetrievedChunk.schema("schema.orders", "table: orders", 0.0, Set.of("orders"));
        RetrievedChunk same = RetrievedChunk.schema("schema.orders", "table: orders", 0.5, Set.of("orders"));
        RetrievedChunk changed = RetrievedChunk.schema("schema.orders", "table: orders; columns: id", 0.0,
                Set.of("orders"));

        assertThat(canonicalizer.contentHash(first)).isEqualTo(canonicalizer.contentHash(same));
        assertThat(canonicalizer.contentHash(first)).isNotEqualTo(canonicalizer.contentHash(changed));
    }

    @Test
    void indexesAndSearchesRealVectorsInMemory() {
        InMemoryVectorRetrievalRepository repository = new InMemoryVectorRetrievalRepository();
        EmbeddingClient embeddingClient = new StaticEmbeddingClient();
        EmbeddingIndexService indexService = new EmbeddingIndexService(
                embeddingClient,
                repository,
                new ChunkCanonicalizer()
        );
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.businessRule("rule.revenue", "revenue spending sales", 0.0,
                        Set.of("orders.total_amount"), Set.of("revenue")),
                RetrievedChunk.schema("schema.products", "products category inventory", 0.0, Set.of("products"))
        );

        indexService.rebuild("fingerprint-1", chunks);

        List<RetrievedChunk> results = repository.search(embeddingClient.embed("spending"), "fingerprint-1",
                embeddingClient.model(), 2);

        assertThat(results).isNotEmpty();
        assertThat(results.getFirst().id()).isEqualTo("rule.revenue");
    }

    @Test
    void doesNotWriteVectorsWhenAnyEmbeddingFails() {
        RecordingVectorRepository repository = new RecordingVectorRepository();
        EmbeddingIndexService indexService = new EmbeddingIndexService(
                new FailingEmbeddingClient(),
                repository,
                new ChunkCanonicalizer()
        );
        List<RetrievedChunk> chunks = List.of(
                RetrievedChunk.schema("schema.orders", "table: orders", 0.0, Set.of("orders")),
                RetrievedChunk.schema("schema.customers", "table: customers", 0.0, Set.of("customers"))
        );

        assertThatThrownBy(() -> indexService.rebuild("fingerprint-1", chunks))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("embedding index rebuild failed for 1 of 2 chunks")
                .hasMessageContaining("schema.customers")
                .hasMessageContaining("provider unavailable");

        assertThat(repository.replaceCalls).isZero();
        assertThat(repository.lastChunks).isEmpty();
    }

    @Test
    void replacingInMemoryIndexWithNewModelKeepsCurrentModelOnly() {
        InMemoryVectorRetrievalRepository repository = new InMemoryVectorRetrievalRepository();
        RetrievedChunk chunk = RetrievedChunk.schema("schema.orders", "table: orders", 0.0, Set.of("orders"));

        new EmbeddingIndexService(new NamedEmbeddingClient("model-a"), repository, new ChunkCanonicalizer())
                .rebuild("fingerprint-1", List.of(chunk));
        new EmbeddingIndexService(new NamedEmbeddingClient("model-b"), repository, new ChunkCanonicalizer())
                .rebuild("fingerprint-1", List.of(chunk));

        assertThat(repository.records()).hasSize(1);
        assertThat(repository.records().getFirst().embeddingModel()).isEqualTo("model-b");
        assertThat(repository.activeCount("fingerprint-1", "model-a")).isZero();
        assertThat(repository.activeCount("fingerprint-1", "model-b")).isEqualTo(1);
    }

    private static class StaticEmbeddingClient implements EmbeddingClient {

        @Override
        public String model() {
            return "static-test";
        }

        @Override
        public float[] embed(String text) {
            if (text.contains("revenue") || text.contains("spending") || text.contains("sales")) {
                return new float[]{1.0f, 0.0f, 0.0f};
            }
            return new float[]{0.0f, 1.0f, 0.0f};
        }
    }

    private static class NamedEmbeddingClient extends StaticEmbeddingClient {

        private final String model;

        private NamedEmbeddingClient(String model) {
            this.model = model;
        }

        @Override
        public String model() {
            return model;
        }
    }

    private static class FailingEmbeddingClient implements EmbeddingClient {

        @Override
        public String model() {
            return "failing-test";
        }

        @Override
        public float[] embed(String text) {
            if (text.contains("customers")) {
                throw new IllegalStateException("schema.customers provider unavailable");
            }
            return new float[]{1.0f, 0.0f, 0.0f};
        }
    }

    private static class RecordingVectorRepository implements VectorRetrievalRepository {

        private int replaceCalls;
        private List<VectorIndexedChunk> lastChunks = List.of();

        @Override
        public void replace(String fingerprint, String embeddingModel, List<VectorIndexedChunk> chunks) {
            replaceCalls++;
            lastChunks = List.copyOf(chunks);
        }

        @Override
        public List<RetrievedChunk> search(float[] queryEmbedding, String fingerprint, String embeddingModel, int limit) {
            return List.of();
        }
    }
}
