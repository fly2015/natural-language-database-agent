package com.nlda.retrieval.query;

import com.nlda.retrieval.model.RetrievedChunk;
import com.nlda.retrieval.text.TextNormalizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class SchemaVocabularyMatcher {

    private static final int MIN_FUZZY_LENGTH = 4;
    private static final double MIN_CONFIDENCE = 0.74;

    private final TextNormalizer textNormalizer;

    public SchemaVocabularyMatcher(TextNormalizer textNormalizer) {
        this.textNormalizer = textNormalizer;
    }

    public Set<String> vocabulary(List<RetrievedChunk> chunks) {
        Set<String> values = new LinkedHashSet<>();
        for (RetrievedChunk chunk : chunks) {
            values.addAll(chunk.aliases());
            for (String schemaRef : chunk.schemaRefs()) {
                values.addAll(splitSchemaRef(schemaRef));
            }
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            for (String token : textNormalizer.tokens(value)) {
                if (token.length() >= 3) {
                    normalized.add(token);
                    normalized.add(singular(token));
                }
            }
        }
        return normalized;
    }

    public Optional<TermCorrection> bestCorrection(String token, Set<String> vocabulary) {
        if (token.length() < MIN_FUZZY_LENGTH || vocabulary.contains(token)) {
            return Optional.empty();
        }

        List<Candidate> candidates = new ArrayList<>();
        for (String candidate : vocabulary) {
            if (candidate.length() < MIN_FUZZY_LENGTH) {
                continue;
            }
            int distance = distance(token, candidate);
            int maxLength = Math.max(token.length(), candidate.length());
            double confidence = 1.0 - ((double) distance / maxLength);
            if (distance <= maxDistance(maxLength) && confidence >= MIN_CONFIDENCE) {
                candidates.add(new Candidate(candidate, distance, confidence));
            }
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        candidates.sort(Comparator.comparingInt(Candidate::distance)
                .thenComparing(Comparator.comparingDouble(Candidate::confidence).reversed())
                .thenComparing(Candidate::term));
        Candidate best = candidates.getFirst();
        boolean ambiguous = candidates.stream()
                .skip(1)
                .anyMatch(candidate -> candidate.distance == best.distance
                        && Math.abs(candidate.confidence - best.confidence) < 0.05);
        return Optional.of(new TermCorrection(token, best.term, best.confidence, ambiguous));
    }

    private Set<String> splitSchemaRef(String schemaRef) {
        Set<String> values = new LinkedHashSet<>();
        for (String part : schemaRef.split("\\.")) {
            values.add(part);
            values.add(part.replace('_', ' '));
        }
        values.add(schemaRef.replace('.', ' ').replace('_', ' '));
        return values;
    }

    private int maxDistance(int maxLength) {
        if (maxLength <= 5) {
            return 1;
        }
        if (maxLength <= 9) {
            return 2;
        }
        return 3;
    }

    private int distance(String first, String second) {
        int[][] costs = new int[first.length() + 1][second.length() + 1];
        for (int i = 0; i <= first.length(); i++) {
            costs[i][0] = i;
        }
        for (int j = 0; j <= second.length(); j++) {
            costs[0][j] = j;
        }
        for (int i = 1; i <= first.length(); i++) {
            for (int j = 1; j <= second.length(); j++) {
                int replacement = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                costs[i][j] = Math.min(
                        Math.min(costs[i - 1][j] + 1, costs[i][j - 1] + 1),
                        costs[i - 1][j - 1] + replacement
                );
                if (i > 1 && j > 1
                        && first.charAt(i - 1) == second.charAt(j - 2)
                        && first.charAt(i - 2) == second.charAt(j - 1)) {
                    costs[i][j] = Math.min(costs[i][j], costs[i - 2][j - 2] + 1);
                }
            }
        }
        return costs[first.length()][second.length()];
    }

    private String singular(String token) {
        if (token.endsWith("ies") && token.length() > 4) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.endsWith("s") && token.length() > 3) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private record Candidate(String term, int distance, double confidence) {
    }
}
