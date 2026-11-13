package com.riansares.r4r.rag;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.config.RetrievalTranslationMode;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RetrievalTranslationModeTest {
    final KnowledgeVectorStore store=mock(KnowledgeVectorStore.class);
    final ChatModel model=mock(ChatModel.class);
    final LocalRetrievalQueryTranslator translator=mock(LocalRetrievalQueryTranslator.class);
    final String english="When should I use ready-mix concrete?", spanish="¿Cuándo usar hormigón de camión?";
    final List<MarkdownChunk> evidence=List.of(new MarkdownChunk("en/guide.md",List.of("Concrete"),0,"Documented evidence"));
    @BeforeEach void setup(){
        when(store.search(anyString(),eq(5),eq(.63))).thenReturn(evidence);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Answer [S1]")))));
        when(translator.translate(english)).thenReturn(spanish);
    }
    CitedRagService service(RetrievalTranslationMode mode){return new CitedRagService(store,model,5,.63,translator,null,mode);}
    @Test void defaultNeverEmbedsOriginalWithoutTranslationEvents(){
        var events=new ArrayList<Map<String,Object>>();
        new CitedRagService(store,model,5,.63,translator).answer(english,null,"en",new RagProgress(events::add));
        verify(store).search(english,5,.63);verifyNoInteractions(translator);
        assertThat(events).noneMatch(e->e.get("stage").toString().startsWith("TRANSLATION_"));
    }
    @Test void alwaysKeepsThePreviousEnglishTranslationPath(){
        service(RetrievalTranslationMode.ALWAYS).answer(english,null,"en");
        verify(translator).translate(english);verify(store).search(spanish,5,.63);verify(store,never()).search(english,5,.63);
    }
    @Test void autoDoesNotTranslateWhenDirectRetrievalHasEvidence(){
        service(RetrievalTranslationMode.AUTO).answer(english,null,"en");
        verify(store).search(english,5,.63);verifyNoInteractions(translator);
    }
    @Test void autoRetriesOnlyOnceAfterEmptyRetrieval(){
        when(store.search(english,5,.63)).thenReturn(List.of());
        assertThat(service(RetrievalTranslationMode.AUTO).answer(english,null,"en").abstention()).isFalse();
        var order=inOrder(store,translator);order.verify(store).search(english,5,.63);order.verify(translator).translate(english);order.verify(store).search(spanish,5,.63);
        verify(translator,times(1)).translate(anyString());verify(model,times(1)).call(any(Prompt.class));
    }
    @Test void autoDoesNotTreatAnInfrastructureFailureAsMissingEvidence(){
        when(store.search(english,5,.63)).thenThrow(new IllegalStateException("offline"));
        assertThatThrownBy(()->service(RetrievalTranslationMode.AUTO).answer(english,null,"en")).hasMessage("offline");
        verifyNoInteractions(translator,model);
    }
    @Test void explicitRetrievalQueryPreventsDoubleTranslationInEveryMode(){
        for(var mode:RetrievalTranslationMode.values())service(mode).answer(english,spanish,"en");
        verify(store,times(3)).search(spanish,5,.63);verifyNoInteractions(translator);
    }
    @Test void spanishQuestionsRemainDirectInEveryMode(){
        for(var mode:RetrievalTranslationMode.values())service(mode).answer(spanish,null,"es");
        verify(store,times(3)).search(spanish,5,.63);verifyNoInteractions(translator);
    }
    @Test void neverAlsoAbstainsDirectlyWithoutTranslationIfNothingMatches(){
        when(store.search(english,5,.63)).thenReturn(List.of());
        assertThat(service(RetrievalTranslationMode.NEVER).answer(english,null,"en").abstention()).isTrue();
        verifyNoInteractions(translator,model);
    }
    @Test void modeConfigurationRejectsTyposAndAcceptsTheDocumentedValues(){
        assertThat(RetrievalTranslationMode.parse("never")).isEqualTo(RetrievalTranslationMode.NEVER);
        assertThat(RetrievalTranslationMode.parse("auto")).isEqualTo(RetrievalTranslationMode.AUTO);
        assertThat(RetrievalTranslationMode.parse("always")).isEqualTo(RetrievalTranslationMode.ALWAYS);
        assertThatThrownBy(()->RetrievalTranslationMode.parse("sometimes")).isInstanceOf(IllegalArgumentException.class);
    }
}
