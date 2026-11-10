package com.riansares.r4r.evaluation;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import java.util.*;

/** Records precisely the final evidence returned to CitedRagService, without changing its ranking. */
final class RecordingKnowledgeStore implements KnowledgeVectorStore {
    private final KnowledgeVectorStore delegate;
    final List<Map<String,Object>> searches=new ArrayList<>();
    RecordingKnowledgeStore(KnowledgeVectorStore delegate){this.delegate=delegate;}
    void reset(){searches.clear();}
    @Override public List<MarkdownChunk> search(String query,int topK,double minScore){
        var entry=new LinkedHashMap<String,Object>();entry.put("query",query);entry.put("topK",topK);entry.put("minScore",minScore);searches.add(entry);
        try{
            var chunks=delegate.search(query,topK,minScore);var evidence=new ArrayList<Map<String,Object>>();
            for(int i=0;i<chunks.size();i++)evidence.add(Map.of("label","[S"+(i+1)+"]","chunk",chunks.get(i)));
            entry.put("fragments",evidence);return chunks;
        }catch(RuntimeException failure){entry.put("errorType",failure.getClass().getSimpleName());throw failure;}
    }
    @Override public void index(List<MarkdownChunk> chunks){throw new UnsupportedOperationException("Evaluation is read-only");}
    @Override public void replaceSource(String source,List<MarkdownChunk> chunks){throw new UnsupportedOperationException("Evaluation is read-only");}
}
