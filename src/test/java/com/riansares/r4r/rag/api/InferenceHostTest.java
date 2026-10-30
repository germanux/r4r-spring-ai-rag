package com.riansares.r4r.rag.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InferenceHostTest {
    @Test
    void endpointTakesPriorityOverModel() {
        assertThat(InferenceHost.identify("http://192.168.1.209:11434", "qwen3:8b").type()).isEqualTo("laptop");
        assertThat(InferenceHost.identify("http://192.168.1.207:11434", "qwen2.5:7b").type()).isEqualTo("desktop");
    }

    @Test
    void fallsBackToKnownModelsWithoutAssumingUnknownHosts() {
        assertThat(InferenceHost.identify("http://ollama:11434", "qwen2.5:7b").type()).isEqualTo("laptop");
        assertThat(InferenceHost.identify("http://ollama:11434", "qwen3:8b").type()).isEqualTo("desktop");
        assertThat(InferenceHost.identify("http://ollama:11434", "qwen3-coder-next-80b").type()).isEqualTo("desktop");
        assertThat(InferenceHost.identify("http://ollama:11434", "qwen3:4b").type()).isEqualTo("unknown");
        assertThat(InferenceHost.identify("invalid", "other").type()).isEqualTo("unknown");
    }
}
