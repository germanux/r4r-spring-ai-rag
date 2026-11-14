package com.riansares.r4r.vector.s3vectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riansares.r4r.config.NomicRerankProperties;
import com.riansares.r4r.vector.config.RagVectorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.embedding.EmbeddingModel;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.s3vectors.S3VectorsClient;
import software.amazon.awssdk.services.s3vectors.model.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Two queries only. Explicit opt-in, profile and endpoint; never runs during default verify. */
@EnabledIfSystemProperty(named="r4r.s3.lambda.live",matches="true")
class S3LambdaLiveIT {
 @Test void spanishAndEnglishAreEquivalent() throws Exception {
  String url=System.getenv("RAG_EMBEDDING_LIVE_URL");assertNotNull(url);
  int dimensions=Integer.parseInt(System.getenv().getOrDefault("RAG_EMBEDDING_DIMENSIONS","1024"));
  var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
  factory.setConnectTimeout(Duration.ofSeconds(2));factory.setReadTimeout(Duration.ofSeconds(30));
  var api=OllamaApi.builder().baseUrl(url).restClientBuilder(org.springframework.web.client.RestClient.builder().requestFactory(factory)).build();
  var json=new ObjectMapper();
  try(var credentials=ProfileCredentialsProvider.create("r4r-codex");
      var s3=S3VectorsClient.builder().region(Region.EU_NORTH_1).credentialsProvider(credentials).build();
      var lambda=LambdaClient.builder().region(Region.EU_NORTH_1).credentialsProvider(credentials).overrideConfiguration(c->c.apiCallTimeout(Duration.ofSeconds(30))).build()) {
   var transport=new LambdaS3Transport(lambda,"r4r-s3-vectors-retrieval");
   var config=new RagVectorProperties.S3Vectors("eu-north-1","amzn-r4r-vectors","amzn-r4r-vector-index",Duration.ofSeconds(1),Duration.ofSeconds(3),Duration.ofSeconds(4));
   for(String question:List.of("¿Qué información hace falta para una reforma?","What information is needed for a renovation?")) {
    var embedded=json.valueToTree(api.embed(new OllamaApi.EmbeddingsRequest("qwen3-embedding:0.6b",List.of(com.riansares.r4r.rag.ObservedEmbeddingModel.retrievalQuery(question)),null,Map.of(),null))).path("embeddings").get(0);
    assertEquals(dimensions,embedded.size());float[] vector=new float[dimensions];List<Float> values=new ArrayList<>();
    for(int i=0;i<dimensions;i++){vector[i]=embedded.get(i).floatValue();assertTrue(Float.isFinite(vector[i]));values.add(vector[i]);}
    var request=QueryVectorsRequest.builder().vectorBucketName(config.bucket()).indexName(config.index()).topK(40).returnDistance(true).returnMetadata(true).queryVector(VectorData.fromFloat32(values)).build();
    long started=System.nanoTime();var direct=s3.queryVectors(request);double directMs=(System.nanoTime()-started)/1e6;
    started=System.nanoTime();var remote=transport.apply(request);double lambdaMs=(System.nanoTime()-started)/1e6;
    assertFalse(remote.vectors().isEmpty());assertEquals(direct.vectors().size(),remote.vectors().size());
    var byKey=new HashMap<String,QueryOutputVector>();direct.vectors().forEach(v->byKey.put(v.key(),v));
    for(var hit:remote.vectors()) {var expected=byKey.remove(hit.key());assertNotNull(expected);assertEquals(expected.distance(),hit.distance(),1e-6);assertEquals(expected.metadata(),hit.metadata());new S3VectorMetadataMapper().fromDocument(hit.metadata());}
    assertTrue(byKey.isEmpty());
    // Freeze the one real query embedding so both adapters receive identical input.
    var cached=mock(EmbeddingModel.class);when(cached.embed(com.riansares.r4r.rag.ObservedEmbeddingModel.retrievalQuery(question))).thenReturn(vector);
    var ranking=new NomicRerankProperties(8,.10);
    var a=new S3VectorsKnowledgeStore(r->direct,cached,config,new S3VectorMetadataMapper(),new S3VectorScoreMapper(),ranking);
    var b=new S3VectorsKnowledgeStore(r->remote,cached,config,new S3VectorMetadataMapper(),new S3VectorScoreMapper(),ranking);
    var documents=a.search(question,5,.63);assertEquals(documents,b.search(question,5,.63));
    System.out.println("S3_LAMBDA_REAL "+json.writeValueAsString(Map.of("language",question.startsWith("¿")?"ES":"EN","dimensions",dimensions,"candidates",remote.vectors().size(),"documents",documents.size(),"directMs",directMs,"lambdaInvokeMs",lambdaMs,"sources",documents.stream().map(d->d.source()+":"+d.index()).toList(),"equivalent",true)));
   }
  }
 }
}
