package com.riansares.r4r.vector;

import com.riansares.r4r.chunking.MarkdownChunk;
import com.riansares.r4r.rag.EmbeddingUnavailableException;
import com.riansares.r4r.rag.EmbeddingConfigurationException;
import com.riansares.r4r.vector.port.KnowledgeVectorStore;
import java.sql.*;
import java.util.List;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.*;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class FailoverKnowledgeVectorStoreTest {
    final KnowledgeVectorStore pg=mock(KnowledgeVectorStore.class), s3=mock(KnowledgeVectorStore.class);
    final List<MarkdownChunk> chunks=List.of(new MarkdownChunk("faq.md",List.of("FAQ"),2,"Documentary evidence [S1]"));
    FailoverKnowledgeVectorStore router(String primary,boolean enabled,boolean readOnly) {
        return new FailoverKnowledgeVectorStore(() -> pg,s3,primary,enabled,readOnly);
    }
    @AfterEach void clearInterrupt() { Thread.interrupted(); }
    @Test void healthyAndEmptyResultsNeverCallS3() {
        when(pg.search("question",5,.63)).thenReturn(chunks,List.of());
        var router=router("pgvector",true,false);
        assertThat(router.search("question",5,.63)).isSameAs(chunks);
        assertThat(router.search("question",5,.63)).isEmpty(); verifyNoInteractions(s3);
    }
    @ParameterizedTest @ValueSource(strings={"08000","08001","08006","57P01","57P02","57P03","53300"})
    void technicalStatesFallbackAndNextQueryRetriesPg(String state) {
        var original=new DataAccessResourceFailureException("redacted",new SQLException("redacted",state));
        when(pg.search("question",5,.63)).thenThrow(original).thenReturn(chunks);
        when(s3.search("question",5,.63)).thenReturn(chunks);
        var router=router("pgvector",true,false);
        assertThat(router.search("question",5,.63)).isSameAs(chunks);
        assertThat(router.search("question",5,.63)).isSameAs(chunks);
        verify(pg,times(2)).search("question",5,.63); verify(s3).search("question",5,.63);
    }
    @Test void jdbcConnectionPoolAndInfrastructureTimeoutsRecover() {
        for (RuntimeException failure:List.of(new CannotGetJdbcConnectionException("pool exhausted"),
                new DataAccessResourceFailureException("pool",new SQLTransientConnectionException()),
                new QueryTimeoutException("infrastructure",new SQLTimeoutException("timeout","HYT00")))) {
            reset(pg,s3); when(pg.search("q",3,.6)).thenThrow(failure); when(s3.search("q",3,.6)).thenReturn(chunks);
            assertThat(router("pgvector",true,false).search("q",3,.6)).isSameAs(chunks);
        }
    }
    @ParameterizedTest @ValueSource(strings={"28000","28P01","42601","42P01","22000","3F000","57014"})
    void authenticationSqlDataSchemaAndCancellationDoNotFallback(String state) {
        var failure=new CannotGetJdbcConnectionException("outer",new SQLException("excluded",state));
        when(pg.search("q",3,.6)).thenThrow(failure);
        assertThatThrownBy(() -> router("pgvector",true,false).search("q",3,.6)).isSameAs(failure);
        verifyNoInteractions(s3);
    }
    @Test void embeddingAndNonJdbcErrorsNeverFallbackEvenWithNestedTransport() {
        for (RuntimeException failure:List.of(
                new EmbeddingUnavailableException("embedding",new SQLException("nested","08006")),
                new EmbeddingConfigurationException("embedding",new SQLException("nested","08006")),
                new RuntimeException(new CancellationException()),new RuntimeException(new InterruptedException()),
                new IllegalArgumentException("invalid",new SQLException("nested","08006")),
                new DataAccessResourceFailureException("not JDBC",new java.net.ConnectException()),
                new QueryTimeoutException("no JDBC evidence"))) {
            reset(pg,s3); when(pg.search("q",3,.6)).thenThrow(failure);
            assertThatThrownBy(() -> router("pgvector",true,false).search("q",3,.6)).isSameAs(failure);
            verifyNoInteractions(s3); Thread.interrupted();
        }
    }
    @Test void nestedAndNextSqlExceptionsAreExaminedWithExclusionsTakingPriority() {
        SQLException sql=new SQLException("outer"); sql.setNextException(new SQLException("connection","08006"));
        when(pg.search("q",3,.6)).thenThrow(new RuntimeException(new DataAccessResourceFailureException("db",sql)));
        when(s3.search("q",3,.6)).thenReturn(chunks); router("pgvector",true,false).search("q",3,.6); verify(s3).search("q",3,.6);
        reset(pg,s3); sql=new SQLException("connection","08006"); sql.setNextException(new SQLException("auth","28P01"));
        var failure=new DataAccessResourceFailureException("db",sql); when(pg.search("q",3,.6)).thenThrow(failure);
        assertThatThrownBy(() -> router("pgvector",true,false).search("q",3,.6)).isSameAs(failure); verifyNoInteractions(s3);
    }
    @Test void disabledFallbackPropagatesAndS3PrimaryDoesNotTouchPg() {
        var failure=new CannotGetJdbcConnectionException("down"); when(pg.search("q",3,.6)).thenThrow(failure);
        assertThatThrownBy(() -> router("pgvector",false,false).search("q",3,.6)).isSameAs(failure); verifyNoInteractions(s3);
        reset(pg); when(s3.search("q",3,.6)).thenReturn(chunks);
        assertThat(router("s3vectors",false,false).search("q",3,.6)).isSameAs(chunks); verifyNoInteractions(pg);
    }
    @Test void bothFailuresRetainCausesAndUseSafeMessage() {
        var original=new CannotGetJdbcConnectionException("private"); var secondary=new IllegalStateException("private");
        when(pg.search("q",3,.6)).thenThrow(original); when(s3.search("q",3,.6)).thenThrow(secondary);
        assertThatThrownBy(() -> router("pgvector",true,false).search("q",3,.6))
                .isInstanceOf(VectorStoreUnavailableException.class).hasMessage("Vector retrieval unavailable")
                .hasCause(original).satisfies(e -> assertThat(e.getSuppressed()).containsExactly(secondary));
    }
    @Test void writesOnlyUsePgEvenWhenS3IsPrimaryAndReadOnlyModeRejectsThem() {
        var router=router("s3vectors",false,false); router.index(chunks); router.replaceSource("faq.md",chunks);
        verify(pg).index(chunks); verify(pg).replaceSource("faq.md",chunks); verifyNoInteractions(s3);
        reset(pg); var readOnly=router("s3vectors",false,true);
        assertThatThrownBy(() -> readOnly.index(chunks)).isInstanceOf(VectorStoreUnavailableException.class);
        assertThatThrownBy(() -> readOnly.replaceSource("faq.md",chunks)).isInstanceOf(VectorStoreUnavailableException.class);
        verifyNoInteractions(pg,s3);
    }
    @Test void fallbackPreservesServiceCitationsSseStagesAndHonestMetrics() {
        when(pg.search("consulta",5,.63)).thenThrow(new CannotGetJdbcConnectionException("offline"));
        when(s3.search("consulta",5,.63)).thenReturn(chunks);
        var chat=mock(org.springframework.ai.chat.model.ChatModel.class);
        when(chat.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(
                new org.springframework.ai.chat.model.ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(
                        new org.springframework.ai.chat.messages.AssistantMessage("Respuesta documental. [S1]")))));
        var service=new com.riansares.r4r.rag.CitedRagService(router("pgvector",true,false),chat,5,.63);
        var events=new java.util.ArrayList<java.util.Map<String,Object>>();
        var progress=new com.riansares.r4r.rag.RagProgress(events::add);
        var result=service.answer("consulta",null,"es",progress);
        assertThat(result.abstention()).isFalse();
        assertThat(result.citations()).singleElement().satisfies(c -> {
            assertThat(c.source()).isEqualTo("faq.md"); assertThat(c.ordinal()).isEqualTo(2);
            assertThat(c.headingPath()).containsExactly("FAQ");
        });
        assertThat(events).extracting(e -> e.get("stage")).containsExactly("RETRIEVAL_STARTED","RETRIEVAL_COMPLETED",
                "GENERATION_STARTED","GENERATION_COMPLETED","VALIDATION_COMPLETED");
        assertThat(progress.metrics().get("embeddingMs")).isNull(); // Mocked port does not execute an embedding.
        assertThat((Double)progress.metrics().get("retrievalIncludingEmbeddingMs")).isGreaterThanOrEqualTo(0);
        verify(chat).call(any(org.springframework.ai.chat.prompt.Prompt.class));
        verify(s3).search("consulta",5,.63);
    }
    @Test void logsProviderDurationAndReasonWithoutQueryOrExceptionMessage() {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(FailoverKnowledgeVectorStore.class);
        var appender=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            when(pg.search("sensitive question",5,.63)).thenThrow(new CannotGetJdbcConnectionException("sensitive exception"));
            when(s3.search("sensitive question",5,.63)).thenReturn(chunks);
            router("pgvector",true,false).search("sensitive question",5,.63);
            var logs=appender.list.stream().map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
            assertThat(logs).anyMatch(m -> m.contains("from=pgvector to=s3vectors reason=jdbc_connection"));
            assertThat(logs).anyMatch(m -> m.contains("provider=pgvector") && m.contains("durationMs="));
            assertThat(logs).anyMatch(m -> m.contains("provider=s3vectors") && m.contains("durationMs="));
            assertThat(logs).noneMatch(m -> m.contains("sensitive") || m.contains("Documentary evidence"));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
    @Test void secondaryCancellationIsNotWrappedAsBothProvidersUnavailable() {
        var cancel=new RuntimeException(new CancellationException());
        when(pg.search("q",3,.6)).thenThrow(new CannotGetJdbcConnectionException("offline"));
        when(s3.search("q",3,.6)).thenThrow(cancel);
        assertThatThrownBy(() -> router("pgvector",true,false).search("q",3,.6)).isSameAs(cancel);
    }
    @Test void invalidRoutingIsRejected() {
        assertThatThrownBy(() -> router("unknown",false,false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> router("s3vectors",true,false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> router("pgvector",false,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FailoverKnowledgeVectorStore(() -> pg,null,"pgvector",true,false)).isInstanceOf(IllegalArgumentException.class);
    }
}
