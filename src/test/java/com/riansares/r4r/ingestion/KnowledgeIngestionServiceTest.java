package com.riansares.r4r.ingestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeIngestionServiceTest {

    @Test
    void sha256IsDeterministicAndUsesProductionMethod() {
        byte[] first = KnowledgeIngestionService.sha256(
                "same".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        byte[] second = KnowledgeIngestionService.sha256(
                "same".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        byte[] different = KnowledgeIngestionService.sha256(
                "different".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(first).containsExactly(second);
        assertThat(first).isNotEqualTo(different);
        assertThat(first).hasSize(32);
    }
}
