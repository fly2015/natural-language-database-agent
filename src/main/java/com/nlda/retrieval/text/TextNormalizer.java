package com.nlda.retrieval.text;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class TextNormalizer {

    private final Analyzer analyzer = new EnglishAnalyzer();

    public String normalize(String value) {
        if (value == null) {
            return "";
        }
        String ascii = Normalizer.normalize(value, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "");
        return ascii.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    public List<String> tokens(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        return List.of(normalized.split(" "));
    }

    public Set<String> tokenSet(String value) {
        return new LinkedHashSet<>(tokens(value));
    }

    public Set<String> analyzedTerms(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return Set.of();
        }
        try (TokenStream stream = analyzer.tokenStream("text", normalized)) {
            Set<String> terms = new LinkedHashSet<>();
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) {
                String text = term.toString();
                if (text.length() >= 2) {
                    terms.add(text);
                }
            }
            stream.end();
            return terms;
        } catch (IOException ex) {
            throw new UncheckedIOException("Unable to analyze text.", ex);
        }
    }

    public Set<String> retrievalTerms(String value) {
        Set<String> terms = new LinkedHashSet<>();
        for (String token : tokens(value)) {
            if (token.length() >= 2) {
                terms.add(token);
            }
        }
        terms.addAll(analyzedTerms(value));
        return terms;
    }
}
