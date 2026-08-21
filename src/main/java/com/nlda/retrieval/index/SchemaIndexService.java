package com.nlda.retrieval.index;

import com.nlda.retrieval.contract.SchemaChunkRepository;
import com.nlda.retrieval.contract.SchemaMetadataProvider;
import com.nlda.retrieval.contract.RetrievalVocabularyIndexService;
import com.nlda.retrieval.model.IndexedSchemaChunks;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.schema.SchemaMetadataSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SchemaIndexService {

    private static final Logger log = LoggerFactory.getLogger(SchemaIndexService.class);

    private final SchemaMetadataProvider metadataProvider;
    private final SchemaChunkBuilder chunkBuilder;
    private final SchemaChunkRepository chunkRepository;
    private final List<RetrievalVocabularyIndexService> vocabularyIndexServices;
    private final EmbeddingIndexService embeddingIndexService;
    private volatile String vocabularyFingerprint = "";
    private volatile String embeddingIndexKey = "";

    public SchemaIndexService(
            SchemaMetadataProvider metadataProvider,
            SchemaChunkBuilder chunkBuilder,
            SchemaChunkRepository chunkRepository
    ) {
        this.metadataProvider = metadataProvider;
        this.chunkBuilder = chunkBuilder;
        this.chunkRepository = chunkRepository;
        this.vocabularyIndexServices = List.of();
        this.embeddingIndexService = null;
    }

    @Autowired
    public SchemaIndexService(
            SchemaMetadataProvider metadataProvider,
            SchemaChunkBuilder chunkBuilder,
            SchemaChunkRepository chunkRepository,
            ObjectProvider<RetrievalVocabularyIndexService> vocabularyIndexServices,
            ObjectProvider<EmbeddingIndexService> embeddingIndexService
    ) {
        this.metadataProvider = metadataProvider;
        this.chunkBuilder = chunkBuilder;
        this.chunkRepository = chunkRepository;
        this.vocabularyIndexServices = vocabularyIndexServices.stream().toList();
        this.embeddingIndexService = embeddingIndexService.getIfAvailable();
    }

    public List<RetrievedChunk> currentChunks() {
        return ensureIndexed().chunks();
    }

    public IndexedSchemaChunks currentIndex() {
        return ensureIndexed();
    }

    public IndexedSchemaChunks readyIndex() {
        return chunkRepository.current()
                .orElseThrow(() -> new IllegalStateException("retrieval index is not ready"));
    }

    public List<RetrievedChunk> fallbackChunks() {
        return chunkRepository.fallbackChunks();
    }

    public IndexedSchemaChunks refresh() {
        SchemaMetadataSnapshot snapshot = metadataProvider.extract();
        List<RetrievedChunk> chunks = chunkBuilder.build(snapshot);
        IndexedSchemaChunks indexed = new IndexedSchemaChunks(snapshot.fingerprint(), chunks);
        chunkRepository.replace(indexed);
        rebuildSupportIndexes(snapshot, indexed);
        log.info("schemaIndexRefresh dialect={} fingerprint={} chunkCount={}", metadataProvider.dialect(),
                indexed.fingerprint(), indexed.chunks().size());
        return indexed;
    }

    private IndexedSchemaChunks ensureIndexed() {
        SchemaMetadataSnapshot snapshot = metadataProvider.extract();
        return chunkRepository.current()
                .filter(existing -> existing.fingerprint().equals(snapshot.fingerprint()))
                .map(existing -> {
                    rebuildSupportIndexes(snapshot, existing);
                    return existing;
                })
                .orElseGet(() -> {
                    List<RetrievedChunk> chunks = chunkBuilder.build(snapshot);
                    IndexedSchemaChunks indexed = new IndexedSchemaChunks(snapshot.fingerprint(), chunks);
                    chunkRepository.replace(indexed);
                    rebuildSupportIndexes(snapshot, indexed);
                    log.info("schemaIndexBuild dialect={} fingerprint={} chunkCount={}", metadataProvider.dialect(),
                            indexed.fingerprint(), indexed.chunks().size());
                    return indexed;
                });
    }

    private void rebuildVocabulary(SchemaMetadataSnapshot snapshot, List<RetrievedChunk> chunks) {
        for (RetrievalVocabularyIndexService vocabularyIndexService : vocabularyIndexServices) {
            vocabularyIndexService.rebuild(snapshot, chunks);
        }
        vocabularyFingerprint = snapshot.fingerprint();
    }

    private void rebuildEmbeddings(IndexedSchemaChunks indexed) {
        if (embeddingIndexService != null) {
            embeddingIndexService.rebuild(indexed.fingerprint(), indexed.chunks());
        }
        embeddingIndexKey = embeddingIndexKey(indexed);
    }

    private void rebuildSupportIndexes(SchemaMetadataSnapshot snapshot, IndexedSchemaChunks indexed) {
        if (!indexed.fingerprint().equals(vocabularyFingerprint)) {
            rebuildVocabulary(snapshot, indexed.chunks());
        }
        if (!embeddingIndexKey(indexed).equals(embeddingIndexKey)) {
            rebuildEmbeddings(indexed);
        }
    }

    private String embeddingIndexKey(IndexedSchemaChunks indexed) {
        return embeddingIndexService == null ? indexed.fingerprint() : embeddingIndexService.indexKey(indexed.fingerprint());
    }
}
