package com.nlda.retrieval.index;

import com.nlda.retrieval.model.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class ChunkCanonicalizer {

    public String canonicalText(RetrievedChunk chunk) {
        return "kind=" + chunk.kind()
                + "\nid=" + chunk.id()
                + "\nschemaRefs=" + sorted(chunk.schemaRefs())
                + "\naliases=" + sorted(chunk.aliases())
                + "\ntext=" + chunk.text();
    }

    public String contentHash(RetrievedChunk chunk) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(canonicalText(chunk).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available.", ex);
        }
    }

    private String sorted(java.util.Set<String> values) {
        return values.stream().sorted().toList().toString();
    }
}
