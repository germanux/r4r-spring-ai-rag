package com.riansares.r4r.rag;

import com.riansares.r4r.vector.pgvector.PgVectorKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** Known Spanish documents and deterministic vectors; tests filtering and citations without a live LLM. */
class CitedRagMultilingualRetrievalTest {
    private static final String ES = "¿Qué tipos de reformas realiza esta empresa? ¿Trabaja en viviendas y oficinas?";
    private static final String EN = "What kinds of renovation work does this company do? Does it work on houses and offices?";
    private final List<String> embedded = new ArrayList<>();
    private final ChatModel generation = mock(ChatModel.class);
    private final SimpleVectorStore vectors = SimpleVectorStore.builder(new EmbeddingModel() {
        public EmbeddingResponse call(EmbeddingRequest request) {
            return new EmbeddingResponse(request.getInstructions().stream().map(text -> {
                embedded.add(text);
                float[] vector = new float[1024];
                vector[text.contains("photosynthesis") || text.contains("fotosíntesis") ? 1 : 0] = 1;
                return new Embedding(vector, 0);
            }).toList());
        }
        public float[] embed(Document document) { return embed(document.getText()); }
    }).build();

    private CitedRagService service() {
        vectors.add(List.of(new Document("La empresa reforma baños, cocinas, viviendas y pequeñas oficinas.",
                Map.of("source", "servicios.md", "headingPath", List.of("Servicios"), "ordinal", 0))));
        return new CitedRagService(new PgVectorKnowledgeStore(vectors, mock(JdbcTemplate.class)),
                generation, 3, .63);
    }

    @Test
    void englishAndSpanishRecoverTheSameEvidenceAtTheSameThresholdAndKeepTheAnswerLanguage() {
        var service = service();
        assertThat(vectors.similaritySearch(SearchRequest.builder().query(EN).topK(3).similarityThreshold(.63).build())).hasSize(1);
        when(generation.call(any(Prompt.class)))
                .thenReturn(response("Reforma viviendas y oficinas [S1]."), response("It renovates houses and offices [S1]."));
        var spanish = service.answer(ES, null, "es");
        var english = service.answer(EN, null, "en");
        assertThat(spanish.abstention()).isFalse();
        assertThat(english.abstention()).isFalse();
        assertThat(english.citations()).isEqualTo(spanish.citations());
        assertThat(english.citations()).extracting(Citation::source).containsExactly("servicios.md");
        assertThat(english.answer()).startsWith("It renovates");
        assertThat(embedded.getLast()).isEqualTo(com.riansares.r4r.rag.ObservedEmbeddingModel.retrievalQuery(EN));
        var prompts = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(generation, times(2)).call(prompts.capture());
        assertThat(prompts.getAllValues().get(0).getContents()).contains("Respond in Spanish.");
        assertThat(prompts.getAllValues().get(1).getContents()).contains(EN, "Respond in English.", "La empresa reforma");
        assertThat(vectors.similaritySearch(SearchRequest.builder().query(ES).topK(3).similarityThreshold(.63).build()))
                .singleElement().satisfies(document -> assertThat(document.getScore()).isEqualTo(1.0));
    }

    @Test
    void explicitRetrievalTextIsRespectedAndDoesNotTriggerAnotherTranslation() {
        var service = service();
        when(generation.call(any(Prompt.class))).thenReturn(response("It renovates homes [S1]."));
        assertThat(service.answer(EN, ES, "en").abstention()).isFalse();
        assertThat(embedded.getLast()).isEqualTo(com.riansares.r4r.rag.ObservedEmbeddingModel.retrievalQuery(ES)); // Only Spanish is embedded, never a concatenation.
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(generation).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).contains(EN, "Respond in English.");
    }

    @Test
    void canonicalizesExternalConstructionTerminologyWithoutReplacingTheOriginalQuestion() {
        var service = service();
        when(generation.call(any(Prompt.class))).thenReturn(response("It renovates homes and offices [S1]."));
        String external = "¿Qué tipo de trabajos de renovación realiza esta empresa? ¿Funciona en casas y oficinas?";
        assertThat(service.answer(EN, external, "en").abstention()).isFalse();
        assertThat(embedded.getLast()).isEqualTo(com.riansares.r4r.rag.ObservedEmbeddingModel.retrievalQuery("¿Qué tipo de reformas realiza esta empresa? ¿Funciona en casas y oficinas?"));
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(generation).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).contains(EN, "Respond in English.");
    }

    @Test
    void outOfDomainEnglishDirectlyAbstainsWithoutGenerationOrTranslation() {
        var service = service();
        assertThat(service.answer("Explain photosynthesis.", null, "en").abstention()).isTrue();
        assertThat(embedded.getLast()).isEqualTo(com.riansares.r4r.rag.ObservedEmbeddingModel.retrievalQuery("Explain photosynthesis."));
        verifyNoInteractions(generation);
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
