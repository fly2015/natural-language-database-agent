package com.nlda.retrieval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SchemaIndexService {

    private static final Logger log = LoggerFactory.getLogger(SchemaIndexService.class);

    private final JdbcSchemaMetadataExtractor metadataExtractor;
    private final SchemaChunkBuilder chunkBuilder;
    private final SchemaChunkRepository chunkRepository;

    public SchemaIndexService(
            JdbcSchemaMetadataExtractor metadataExtractor,
            SchemaChunkBuilder chunkBuilder,
            SchemaChunkRepository chunkRepository
    ) {
        this.metadataExtractor = metadataExtractor;
        this.chunkBuilder = chunkBuilder;
        this.chunkRepository = chunkRepository;
    }

    public List<RetrievedChunk> currentChunks() {
        return ensureIndexed().chunks();
    }

    public List<RetrievedChunk> fallbackChunks() {
        return chunkRepository.fallbackChunks();
    }

    public IndexedSchemaChunks refresh() {
        SchemaMetadataSnapshot snapshot = metadataExtractor.extract();
        List<RetrievedChunk> chunks = chunkBuilder.build(snapshot);
        IndexedSchemaChunks indexed = new IndexedSchemaChunks(snapshot.fingerprint(), chunks);
        chunkRepository.replace(indexed);
        log.info("schemaIndexRefresh fingerprint={} chunkCount={}", indexed.fingerprint(), indexed.chunks().size());
        return indexed;
    }

    private IndexedSchemaChunks ensureIndexed() {
        SchemaMetadataSnapshot snapshot = metadataExtractor.extract();
        return chunkRepository.current()
                .filter(existing -> existing.fingerprint().equals(snapshot.fingerprint()))
                .orElseGet(() -> {
                    List<RetrievedChunk> chunks = chunkBuilder.build(snapshot);
                    IndexedSchemaChunks indexed = new IndexedSchemaChunks(snapshot.fingerprint(), chunks);
                    chunkRepository.replace(indexed);
                    log.info("schemaIndexBuild fingerprint={} chunkCount={}", indexed.fingerprint(),
                            indexed.chunks().size());
                    return indexed;
                });
    }
}
