package com.riansares.r4r.vector.s3vectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import software.amazon.awssdk.services.lambda.*;
import software.amazon.awssdk.services.lambda.model.*;
import software.amazon.awssdk.services.s3vectors.model.*;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import com.riansares.r4r.vector.config.RagVectorProperties;
import com.riansares.r4r.config.NomicRerankProperties;
import org.springframework.ai.embedding.EmbeddingModel;
import java.time.Duration;
import java.util.*;
class LambdaS3TransportTest {
 LambdaClient client=mock(LambdaClient.class);LambdaS3Transport transport=new LambdaS3Transport(client,"r4r-s3-vectors-retrieval");
 String json="""
 {"schemaVersion":1,"bucket":"amzn-r4r-vectors","index":"amzn-r4r-vector-index","dimensions":1024,"distanceMetric":"cosine","vectorSearchMs":1,"vectors":[{"key":"a","distance":0.2,"metadata":{"source":"a.md","source_text":"Evidence a","ordinal":0}}]}
 """;
 QueryVectorsRequest request(){return QueryVectorsRequest.builder().queryVector(VectorData.fromFloat32(Collections.nCopies(1024,1f))).topK(4).build();}
 void response(String body){when(client.invoke(any(InvokeRequest.class))).thenReturn(InvokeResponse.builder().statusCode(200).payload(SdkBytes.fromUtf8String(body)).build());}
 @Test void equivalenceRankingAndCitations(){response(json);var direct=transport.apply(request());var embeddings=mock(EmbeddingModel.class);float[] vector=new float[1024];vector[0]=1;when(embeddings.embed(anyString())).thenReturn(vector);
 var config=new RagVectorProperties.S3Vectors("eu-north-1","amzn-r4r-vectors","amzn-r4r-vector-index",Duration.ofSeconds(1),Duration.ofSeconds(3),Duration.ofSeconds(4));
 var a=new S3VectorsKnowledgeStore(r->direct,embeddings,config,new S3VectorMetadataMapper(),new S3VectorScoreMapper(),NomicRerankProperties.defaults());
 var b=new S3VectorsKnowledgeStore(transport,embeddings,config,new S3VectorMetadataMapper(),new S3VectorScoreMapper(),NomicRerankProperties.defaults());
 assertEquals(a.search("Evidence",1,.63),b.search("Evidence",1,.63));assertEquals(a.search("Evidence",1,.99),b.search("Evidence",1,.99));}
 @Test void strictIdentity(){response(json.replace("1024","768"));assertThrows(IllegalStateException.class,()->transport.apply(request()));}
 @Test void functionError(){when(client.invoke(any(InvokeRequest.class))).thenReturn(InvokeResponse.builder().statusCode(200).functionError("Unhandled").build());assertThrows(com.riansares.r4r.vector.VectorStoreUnavailableException.class,()->transport.apply(request()));}
 @Test void timeout(){when(client.invoke(any(InvokeRequest.class))).thenThrow(ApiCallTimeoutException.builder().build());assertThrows(ApiCallTimeoutException.class,()->transport.apply(request()));}
 @Test void denied(){when(client.invoke(any(InvokeRequest.class))).thenThrow(software.amazon.awssdk.services.lambda.model.ResourceNotFoundException.builder().build());assertThrows(software.amazon.awssdk.core.exception.SdkException.class,()->transport.apply(request()));}
 @Test void cancellation(){Thread.currentThread().interrupt();try{assertThrows(java.util.concurrent.CancellationException.class,()->transport.apply(request()));verifyNoInteractions(client);}finally{Thread.interrupted();}}
}
