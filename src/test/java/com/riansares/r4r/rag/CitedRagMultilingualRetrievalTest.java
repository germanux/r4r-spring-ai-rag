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
    private final ChatModel translation = mock(ChatModel.class), generation = mock(ChatModel.class);
    private final SimpleVectorStore vectors = SimpleVectorStore.builder(new EmbeddingModel() {
        public EmbeddingResponse call(EmbeddingRequest request) {
            return new EmbeddingResponse(request.getInstructions().stream().map(text -> {
                embedded.add(text);
                float[] vector = new float[768];
                vector[text.equals(EN) || text.contains("fotosíntesis") ? 1 : 0] = 1;
                return new Embedding(vector, 0);
            }).toList());
        }
        public float[] embed(Document document) { return embed(document.getText()); }
    }).build();

    private CitedRagService service() {
        vectors.add(List.of(new Document("La empresa reforma baños, cocinas, viviendas y pequeñas oficinas.",
                Map.of("source", "servicios.md", "headingPath", List.of("Servicios"), "ordinal", 0))));
        return new CitedRagService(new PgVectorKnowledgeStore(vectors, mock(JdbcTemplate.class)),
                generation, 3, .63, new LocalRetrievalQueryTranslator(translation));
    }

    @Test
    void englishAndSpanishRecoverTheSameEvidenceAtTheSameThresholdAndKeepTheAnswerLanguage() {
        var service = service();
        assertThat(vectors.similaritySearch(SearchRequest.builder().query(EN).topK(3).similarityThreshold(.63).build())).isEmpty();
        when(translation.call(any(Prompt.class))).thenReturn(response(ES));
        when(generation.call(any(Prompt.class)))
                .thenReturn(response("Reforma viviendas y oficinas [S1]."), response("It renovates houses and offices [S1]."));
        var spanish = service.answer(ES, null, "es");
        var english = service.answer(EN, null, "en");
        assertThat(spanish.abstention()).isFalse();
        assertThat(english.abstention()).isFalse();
        assertThat(english.citations()).isEqualTo(spanish.citations());
        assertThat(english.citations()).extracting(Citation::source).containsExactly("servicios.md");
        assertThat(english.answer()).startsWith("It renovates");
        assertThat(embedded.getLast()).isEqualTo(ES);
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
        verifyNoInteractions(translation);
        assertThat(embedded.getLast()).isEqualTo(ES); // Only Spanish is embedded, never a concatenation.
        var prompt = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(generation).call(prompt.capture());
        assertThat(prompt.getValue().getContents()).contains(EN, "Respond in English.");
    }

    @Test
    void translatedOutOfDomainQuestionStillAbstainsWithoutCallingAnswerGeneration() {
        var service = service();
        when(translation.call(any(Prompt.class))).thenReturn(response("Explica la fotosíntesis."));
        assertThat(service.answer("Explain photosynthesis.", null, "en").abstention()).isTrue();
        verifyNoInteractions(generation);
    }

    @Test
    void failedTranslationIsAnErrorAndNeverSilentlyFallsBackToRawEnglishOrClaimsNoEvidence() {
        var service = service();
        when(translation.call(any(Prompt.class))).thenReturn(response(""));
        int before = embedded.size();
        assertThatThrownBy(() -> service.answer(EN, null, "en")).isInstanceOf(ResponseStatusException.class);
        assertThat(embedded).hasSize(before);
        verifyNoInteractions(generation);
    }

    @Test
    void unavailableTranslationProviderIsAnErrorNotAnEvidenceAbstention() {
        var service = service();
        when(translation.call(any(Prompt.class))).thenThrow(new ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "inference unavailable"));
        int before = embedded.size();

        assertThatThrownBy(() -> service.answer(EN, null, "en"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode().value()).isEqualTo(503));
        assertThat(embedded).hasSize(before);
        verifyNoInteractions(generation);
    }

    private static ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
