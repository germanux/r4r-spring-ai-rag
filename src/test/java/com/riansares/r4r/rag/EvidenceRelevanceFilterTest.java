package com.riansares.r4r.rag;

import com.riansares.r4r.chunking.MarkdownChunk;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class EvidenceRelevanceFilterTest {
    ChatModel model=mock(ChatModel.class);
    List<MarkdownChunk> chunks=List.of(
            new MarkdownChunk("drains.md",List.of("Drainage"),0,"Drain cleaning"),
            new MarkdownChunk("walls.md",List.of("Cracks"),3,"Document cracks and seek assessment"));
    void reply(String text){when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));}
    @Test void onlySelectsExistingEvidenceAndPreservesOriginalOrder(){
        reply("{\"sources\":[2],\"coverage\":\"partial\"}");
        var result=new EvidenceRelevanceFilter(model).select("Are wall cracks structural?",chunks,false);
        assertThat(result.chunks()).containsExactly(chunks.getLast());assertThat(result.partial()).isTrue();
    }
    @Test void unrelatedEvidenceCanBeRejectedWithoutGeneratingAnAnswer(){
        reply("{\"sources\":[],\"coverage\":\"none\"}");
        var filter=new EvidenceRelevanceFilter(model);
        var service=new CitedRagService(new com.riansares.r4r.vector.port.KnowledgeVectorStore(){
            public void index(List<MarkdownChunk>x){throw new UnsupportedOperationException();}
            public void replaceSource(String s,List<MarkdownChunk>x){throw new UnsupportedOperationException();}
            public List<MarkdownChunk> search(String q,int k,double score){return chunks;}
        },model,5,.63,null,filter);
        assertThat(service.answer("How do I light a corridor?",null,"en").abstention()).isTrue();
        verify(model,times(1)).call(any(Prompt.class));
    }
    @Test void malformedOrInventedEvidenceIsAnErrorNotSuccessfulAbstention(){
        for(String text:List.of("not JSON","{\"sources\":[3],\"coverage\":\"full\"}","{\"sources\":[],\"coverage\":\"full\"}","{\"sources\":[1],\"coverage\":\"none\"}")){
            reply(text);assertThatThrownBy(()->new EvidenceRelevanceFilter(model).select("Question",chunks,false)).isInstanceOf(IllegalStateException.class);
        }
    }
    @Test void emptyRetrievalDoesNotCallTheModel(){
        assertThat(new EvidenceRelevanceFilter(model).select("Question",List.of(),false).chunks()).isEmpty();verifyNoInteractions(model);
    }
}
