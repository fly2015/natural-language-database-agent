package com.metajpa.nlda.ui;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatPageControllerTest {

    @Test
    void returnsChatTemplateName() {
        ChatPageController controller = new ChatPageController();

        assertThat(controller.chat()).isEqualTo("chat");
    }
}
