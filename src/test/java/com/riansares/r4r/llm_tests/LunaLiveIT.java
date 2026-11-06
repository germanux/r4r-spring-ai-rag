package com.riansares.r4r.llm_tests;

import com.riansares.r4r.rag.*;
import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.PgVectorKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.util.*;
import java.nio.file.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="RAG_LUNA_LIVE_TEST",matches="true")
class LunaLiveIT {
 @Test void realRemoteAnswerPreservesEnglishAndCitationsAfterSimulatedLocalFailures() throws Exception {
  var props=new Properties();try(var in=Files.newBufferedReader(Path.of("docker-spring/.env"))){props.load(in);}
  String key=System.getenv().getOrDefault("OPENAI_API_KEY",props.getProperty("OPENAI_API_KEY",""));
  key=key.replaceAll("^[\"']|[\"']$","");assertThat(key.isBlank()).as("Existing API key available").isFalse();
  var http=new SimpleClientHttpRequestFactory();http.setConnectTimeout(3000);http.setReadTimeout(45000);
  var model=new OpenAiResponsesModel(RestClient.builder().baseUrl("https://api.openai.com/v1").requestFactory(http).build(),key,"gpt-6-luna");
  var down=new GenerationRouter.Target(new GenerationRouter.Provider("local","http://127.0.0.1:1","unused"),p->{throw new IllegalStateException();},()->false);
  var router=new GenerationRouter(List.of(down,down,new GenerationRouter.Target(new GenerationRouter.Provider("OpenAI","https://api.openai.com/v1","gpt-6-luna"),model,()->true)));
  var store=mock(PgVectorKnowledgeStore.class);
  when(store.search(anyString(),eq(3),eq(.63))).thenReturn(List.of(new MarkdownChunk("services.md",List.of("Services"),0,"Riansares 4R renovates houses and small offices.")));
  var result=new CitedRagService(store,router,3,.63).answer("Does Riansares 4R renovate offices?",null,"en");
  assertThat(result.abstention()).isFalse();assertThat(result.citations()).hasSize(1);
  assertThat(result.answer()).containsIgnoringCase("office");assertThat(result.answer()).contains("[S1]");
  assertThat(router.lastUsed().model()).isEqualTo("gpt-6-luna");
  System.out.println("LUNA_LIVE provider="+router.lastUsed().name()+" model="+router.lastUsed().model()+" answer="+result.answer()+" usage="+model.lastUsage());
 }
}
