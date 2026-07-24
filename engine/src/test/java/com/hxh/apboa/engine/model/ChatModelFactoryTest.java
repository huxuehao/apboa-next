package com.hxh.apboa.engine.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatModelFactoryTest {

    @Test
    void shouldPreferNodeThinkingOverrideAndRespectModelCapability() {
        assertThat(ChatModelFactory.resolveThinkingEnabled(true, false, true)).isFalse();
        assertThat(ChatModelFactory.resolveThinkingEnabled(true, true, false)).isTrue();
        assertThat(ChatModelFactory.resolveThinkingEnabled(true, null, false)).isFalse();
        assertThat(ChatModelFactory.resolveThinkingEnabled(true, null, null)).isTrue();
        assertThat(ChatModelFactory.resolveThinkingEnabled(false, true, true)).isFalse();
    }
}
