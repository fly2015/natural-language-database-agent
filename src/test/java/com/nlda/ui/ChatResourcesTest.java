package com.nlda.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ChatResourcesTest {

    @Test
    void chatTemplateAndAssetsArePresent() throws IOException {
        String template = resource("/templates/chat.html");
        String css = resource("/static/css/chat.css");
        String js = resource("/static/js/chat.js");

        assertThat(template).contains("Natural Language Database Agent", "/js/chat.js");
        assertThat(css).contains(".workspace", ".composer", "@media");
        assertThat(js).contains("fetch(\"/api/query\"", "renderTable");
    }

    private String resource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).as("resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
