package com.riansares.r4r.rag.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InferenceHostTest {
    @Test void nasUsesDeclaredCpuAndRamWithoutInventingAGpu() {
        var nas = InferenceHost.identify("http://192.168.1.203:11434", "qwen3-embedding:0.6b");
        assertThat(nas.cpu()).isEqualTo("Dual-core · 2 GHz");
        assertThat(nas.ram()).isEqualTo("6 GB");
        assertThat(nas.gpu()).isNull();
    }
    @Test
    void endpointTakesPriorityOverModel() {
        assertThat(InferenceHost.identify("http://192.168.1.209:11434", "qwen3:8b").type()).isEqualTo("laptop");
        assertThat(InferenceHost.identify("http://192.168.1.207:11434", "qwen2.5:7b").type()).isEqualTo("desktop");
        assertThat(InferenceHost.identify("http://192.168.1.209:11434", "qwen3-embedding:0.6b").type()).isEqualTo("laptop");
        assertThat(InferenceHost.identify("http://192.168.1.203:11434", "qwen3-embedding:0.6b").type()).isEqualTo("nas");
        assertThat(InferenceHost.identify("http://ollama-embeddings:11434", "qwen3-embedding:0.6b").type()).isEqualTo("nas");
        assertThat(InferenceHost.identify("http://unidentified:11434", "qwen3-embedding:0.6b").type()).isEqualTo("unknown");
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
