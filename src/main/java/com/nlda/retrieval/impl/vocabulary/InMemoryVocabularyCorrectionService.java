package com.nlda.retrieval.impl.vocabulary;

import com.nlda.retrieval.contract.RetrievalVocabularyIndexService;
import com.nlda.retrieval.contract.VocabularyCorrectionService;
import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.model.VocabularySourceType;
import com.nlda.retrieval.model.schema.SchemaMetadataSnapshot;
import com.nlda.retrieval.query.CorrectionCandidate;
import com.nlda.retrieval.query.SchemaVocabularyMatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@ConditionalOnProperty(prefix = "agent.retrieval.vocabulary", name = "provider", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryVocabularyCorrectionService implements VocabularyCorrectionService, RetrievalVocabularyIndexService {

    private final SchemaVocabularyMatcher vocabularyMatcher;
    private volatile Set<String> vocabulary = Set.of();

    public InMemoryVocabularyCorrectionService(SchemaVocabularyMatcher vocabularyMatcher) {
        this.vocabularyMatcher = vocabularyMatcher;
    }

    @Override
    public void rebuild(SchemaMetadataSnapshot snapshot, List<RetrievedChunk> chunks) {
        Set<String> updatedVocabulary = new LinkedHashSet<>(vocabularyMatcher.vocabulary(chunks));
        this.vocabulary = Set.copyOf(updatedVocabulary);
    }

    @Override
    public List<CorrectionCandidate> correct(List<String> tokens) {
        List<CorrectionCandidate> candidates = new ArrayList<>();
        for (String token : tokens) {
            Optional<com.nlda.retrieval.query.TermCorrection> correction = vocabularyMatcher.bestCorrection(token, vocabulary);
            correction.ifPresent(term -> candidates.add(new CorrectionCandidate(
                    term.original(),
                    term.corrected(),
                    VocabularySourceType.SCHEMA,
                    "in-memory",
                    term.confidence(),
                    false,
                    term.ambiguous()
            )));
        }
        return List.copyOf(candidates);
    }
}
