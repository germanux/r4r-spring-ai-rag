package com.riansares.r4r.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiFallbackTest {
    @Test void englishSearchAndAnswerUseRemoteWhenMyMemoryAndBothLocalsAreUnavailable() {
        var store = mock(com.riansares.r4r.vector.port.KnowledgeVectorStore.class);
        String english = "Does Riansares 4R renovate offices?";
        String spanish = "¿Riansares 4R reforma oficinas?";
        when(store.search(eq(spanish), eq(3), eq(.63)))
                .thenReturn(List.of(new com.riansares.r4r.chunking.MarkdownChunk(
                        "services.md", List.of("Services"), 0, "Se reforman pequeñas oficinas.")));
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var localCalls = new java.util.concurrent.atomic.AtomicInteger();
        ChatModel failingLocal = prompt -> {
            localCalls.incrementAndGet();
            throw new IllegalStateException("simulated local generation failure");
        };
        ChatModel remote = prompt -> {
            boolean translating = prompt.getContents().contains("Translate the user's question into Spanish");
            calls.incrementAndGet();
            return new ChatResponse(List.of(new Generation(new AssistantMessage(
                    translating ? spanish : "Yes, the company renovates small offices [S1]."))));
        };
        var down = new GenerationRouter.Target(
                new GenerationRouter.Provider("offline", "http://offline", "offline"), failingLocal, () -> true);
        var targets = List.of(down, down, new GenerationRouter.Target(
                new GenerationRouter.Provider("OpenAI", "https://api.openai.com/v1", "gpt-6-luna"), remote, () -> true));
        var translator = new LocalRetrievalQueryTranslator(new GenerationRouter(targets));
        var service = new CitedRagService(store, new GenerationRouter(targets), 3, .63, translator, null, com.riansares.r4r.config.RetrievalTranslationMode.ALWAYS);

        var answer = service.answer(english, null, "en");
        assertThat(answer.abstention()).isFalse();
        assertThat(answer.citations()).hasSize(1);
        assertThat(answer.answer()).contains("offices", "[S1]");
        assertThat(calls.get()).isEqualTo(2); // remote translation + remote answer
        assertThat(localCalls.get()).isEqualTo(2); // do not retry these hosts during generation
        verify(store).search(spanish, 3, .63);
    }

    @Test void remoteDailyBudgetRejectsTheNextCallWithoutAnotherHttpRequest() {
        var builder = RestClient.builder().baseUrl("https://api.openai.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"OK [S1]\"}]}]}", MediaType.APPLICATION_JSON));
        var model = new OpenAiResponsesModel(builder.build(), "TEST_ONLY", "gpt-6-luna", 1);
        assertThat(model.call(new Prompt("Question")).getResult().getOutput().getText()).isEqualTo("OK [S1]");
        assertThatThrownBy(() -> model.call(new Prompt("Question")))
                .hasMessage("Remote generation unavailable");
        server.verify();
    }

    @Test void orderFailureDisableAndAbstentionNeverSpendUnnecessarily() {
        for (int scenario=0;scenario<7;scenario++) {
            var pc=mock(ChatModel.class);var lp=mock(ChatModel.class);var remote=mock(ChatModel.class);
            var prompt=new Prompt("same evidence [S1], original question and language");
            var answer=new ChatResponse(List.of(new Generation(new AssistantMessage("Answer [S1]"))));
            when(pc.call(prompt)).thenReturn(scenario==6?new ChatResponse(List.of(new Generation(new AssistantMessage("__R4R_ABSTAIN__")))):answer);
            when(lp.call(prompt)).thenReturn(answer);when(remote.call(prompt)).thenReturn(answer);
            if(scenario>=1 && scenario!=6) when(pc.call(prompt)).thenThrow(new IllegalStateException());
            if(scenario>=2 && scenario!=6) when(lp.call(prompt)).thenThrow(new IllegalStateException());
            if(scenario==3) when(remote.call(prompt)).thenThrow(new IllegalStateException());
            var targets=new ArrayList<GenerationRouter.Target>();
            targets.add(new GenerationRouter.Target(new GenerationRouter.Provider("PC","http://pc","qwen3:8b"),pc,()->true));
            targets.add(new GenerationRouter.Target(new GenerationRouter.Provider("LP","http://lp","qwen2.5:7b"),lp,()->true));
            if(scenario!=4)targets.add(new GenerationRouter.Target(new GenerationRouter.Provider("OpenAI","https://api.openai.com/v1","gpt-6-luna"),remote,()->true));
            var router=new GenerationRouter(targets);
            if(scenario==3||scenario==4)assertThatThrownBy(()->router.call(prompt)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            else { router.call(prompt);assertThat(router.lastUsed().name()).isEqualTo(scenario==0||scenario==6?"PC":scenario==1?"LP":"OpenAI"); }
            if(scenario==0||scenario==1||scenario==4||scenario==6)verifyNoInteractions(remote);
            else verify(remote,times(1)).call(same(prompt));
        }
    }
    @Test void boundedResponsesRequestAndSanitizedErrors() {
        for(String status:List.of("completed","incomplete","failed","empty","http","timeout")){
            var builder=RestClient.builder().baseUrl("https://api.openai.com/v1");
            var server=MockRestServiceServer.bindTo(builder).build();
            var expect=server.expect(requestTo("https://api.openai.com/v1/responses"))
                    .andExpect(header("Authorization","Bearer TEST_ONLY"))
                    .andExpect(jsonPath("$.model").value("gpt-6-luna"))
                    .andExpect(jsonPath("$.store").value(false))
                    .andExpect(jsonPath("$.max_output_tokens").value(2048))
                    .andExpect(jsonPath("$.input[0].content").value("Evidence and question"));
            if(status.equals("timeout"))expect.andRespond(withException(new java.net.SocketTimeoutException("TEST_ONLY")));
            else if(status.equals("http"))expect.andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS).body("TEST_ONLY secret"));
            else expect.andRespond(withSuccess("{\"status\":\""+status+"\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\""+(status.equals("empty")?"":"Answer [S1]")+"\"}]}],\"usage\":{\"input_tokens\":10,\"output_tokens\":4}}",MediaType.APPLICATION_JSON));
            var model=new OpenAiResponsesModel(builder.build(),"TEST_ONLY","gpt-6-luna");
            if(status.equals("completed")){assertThat(model.call(new Prompt("Evidence and question")).getResult().getOutput().getText()).isEqualTo("Answer [S1]");assertThat(model.lastUsage().inputTokens()).isEqualTo(10);}
            else assertThatThrownBy(()->model.call(new Prompt("Evidence and question"))).hasMessage("Remote generation unavailable").hasNoCause();
            server.verify();
        }
    }
    @Test void remoteRetainsRagLanguageContextAndCitations() {
        for(String lang:List.of("es","en")) {
            var store=mock(com.riansares.r4r.vector.port.KnowledgeVectorStore.class);
            when(store.search(anyString(),eq(3),eq(.63))).thenReturn(List.of(new com.riansares.r4r.chunking.MarkdownChunk("services.md",List.of("Services"),0,"Renovates offices.")));
            ChatModel remote = prompt -> {
                assertThat(prompt.getContents()).contains("Renovates offices.","[S1]","Original question",lang.equals("es")?"Respond in Spanish.":"Respond in English.");
                return new ChatResponse(List.of(new Generation(new AssistantMessage(lang.equals("es")?"Reforma oficinas [S1].":"It renovates offices [S1]."))));
            };
            var down=new GenerationRouter.Target(new GenerationRouter.Provider("local","http://local","local"),remote,()->false);
            var router=new GenerationRouter(List.of(down,down,new GenerationRouter.Target(new GenerationRouter.Provider("OpenAI","https://api.openai.com/v1","gpt-6-luna"),remote,()->true)));
            var result=new CitedRagService(store,router,3,.63).answer("Original question",null,lang);
            assertThat(result.abstention()).isFalse();assertThat(result.citations()).hasSize(1);
        }
    }

}
