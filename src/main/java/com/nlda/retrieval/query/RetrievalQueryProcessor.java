package com.nlda.retrieval.query;

import com.nlda.retrieval.contract.VocabularyCorrectionService;
import com.nlda.retrieval.text.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class RetrievalQueryProcessor {

    private final TextNormalizer textNormalizer;
    private final VocabularyCorrectionService correctionService;

    public RetrievalQueryProcessor(TextNormalizer textNormalizer, VocabularyCorrectionService correctionService) {
        this.textNormalizer = textNormalizer;
        this.correctionService = correctionService;
    }

    public ProcessedQuery process(String question) {
        String normalized = textNormalizer.normalize(question);
        List<String> tokens = textNormalizer.tokens(normalized);
        List<TermCorrection> corrections = new ArrayList<>();
        Set<String> expanded = new LinkedHashSet<>(tokens);
        Set<String> aliases = new LinkedHashSet<>();

        for (CorrectionCandidate candidate : correctionService.correct(tokens)) {
            corrections.add(new TermCorrection(
                    candidate.original(),
                    candidate.corrected(),
                    candidate.score(),
                    candidate.ambiguous()
            ));
            expanded.add(candidate.corrected());
            if (candidate.sourceType() == com.nlda.retrieval.model.VocabularySourceType.ALIAS) {
                aliases.add(candidate.corrected());
            }
        }
        expanded.addAll(aliases);
        Set<String> analyzed = new LinkedHashSet<>();
        for (String term : expanded) {
            analyzed.addAll(textNormalizer.analyzedTerms(term));
        }
        expanded.addAll(analyzed);

        boolean ambiguous = corrections.stream().anyMatch(TermCorrection::ambiguous);
        double confidence = corrections.isEmpty()
                ? 1.0
                : corrections.stream().mapToDouble(TermCorrection::confidence).min().orElse(1.0);
        return new ProcessedQuery(
                question,
                normalized,
                List.copyOf(tokens),
                List.copyOf(corrections),
                aliases,
                String.join(" ", expanded),
                ambiguous,
                Math.round(confidence * 100.0) / 100.0
        );
    }
}
