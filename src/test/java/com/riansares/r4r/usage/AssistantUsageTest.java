package com.riansares.r4r.usage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AssistantUsageTest {
    private static final class RecordingJdbc {
        final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        final java.util.concurrent.BlockingQueue<java.util.Map<Integer,Object>> writes = new java.util.concurrent.LinkedBlockingQueue<>();
        RecordingJdbc() throws Exception {
            doAnswer(inv -> {
                var connection = mock(java.sql.Connection.class);
                when(connection.prepareStatement(anyString())).thenAnswer(sql -> {
                    var values = new java.util.HashMap<Integer,Object>();
                    var statement = mock(java.sql.PreparedStatement.class);
                    doAnswer(set -> { values.put(set.getArgument(0),set.getArgument(1)); return null; })
                            .when(statement).setObject(anyInt(),any());
                    when(statement.executeUpdate()).thenAnswer(execute -> { writes.add(values); return 1; });
                    return statement;
                });
                return ((ConnectionCallback<?>)inv.getArgument(0)).doInConnection(connection);
            }).when(jdbc).execute(any(ConnectionCallback.class));
        }
        java.util.Map<Integer,Object> next() throws Exception {
            var values = writes.poll(2,TimeUnit.SECONDS); assertNotNull(values); return values;
        }
    }

    private MockHttpServletRequest request() {
        var r=new MockHttpServletRequest();r.addHeader("Origin","https://athagon.tech");return r;
    }
    @Test void exactOriginsOnly() {
        assertEquals("RIANSARES4R",AssistantUsage.origin("https://www.riansares4r.com"));
        assertEquals("ATHAGON",AssistantUsage.origin("https://athagon.tech"));
        assertEquals("UNKNOWN",AssistantUsage.origin("https://athagon.tech.evil.test"));
        assertEquals("UNKNOWN",AssistantUsage.origin(null));
    }
    @Test void oneTerminalEventAndDatabaseFailureDoesNotEscape() throws Exception {
        var jdbc=mock(JdbcTemplate.class);var called=new CountDownLatch(1);
        doAnswer(i->{called.countDown();throw new IllegalStateException("database unavailable");}).when(jdbc).execute(any(ConnectionCallback.class));
        var usage=new AssistantUsage(jdbc,true);
        try {var ticket=usage.begin(request());ticket.finish("PARTIAL");ticket.finish("ERROR");
            assertTrue(called.await(2,TimeUnit.SECONDS));verify(jdbc,times(1)).execute(any(ConnectionCallback.class));
        } finally {usage.close();}
    }
    @Test void disabledCollectionDoesNotTouchDatabase() {
        var jdbc=mock(JdbcTemplate.class);var usage=new AssistantUsage(jdbc,false);
        usage.view("ATHAGON");usage.begin(request()).finish("ERROR");usage.close();verifyNoInteractions(jdbc);
    }
    @Test void ordinaryTechnicalHistoryNeedsNoAnalyticsOrHistoryConsent() throws Exception {
        var db=new RecordingJdbc();var usage=new AssistantUsage(db.jdbc,true);usage.privacy(true,true);
        var r=request();r.addHeader("X-R4R-View-Id","00000000-0000-4000-8000-000000000001");
        r.addHeader("X-R4R-Request-Id","00000000-0000-4000-8000-000000000002");
        try {var ticket=usage.begin(r);ticket.question("How can I compare flooring materials?");
            ticket.response(new com.riansares.r4r.rag.api.RagQueryResponse("Compare the documented properties.",false,java.util.List.of()),12.0);
            var row=db.next();assertEquals("legitimate-interest-v1",row.get(18));
            assertEquals("How can I compare flooring materials?",row.get(7));assertEquals("Compare the documented properties.",row.get(8));
            assertEquals(12.0,row.get(11));assertNull(row.get(19));assertNotEquals(row.get(1),row.get(6));
        } finally {usage.close();}
    }
    @Test void objectionOmitsTextAndIpButKeepsCountryAndOperationalOutcome() throws Exception {
        var db=new RecordingJdbc();var usage=new AssistantUsage(db.jdbc,true);usage.privacy(true,true);
        usage.network(new com.riansares.r4r.config.ClientNetwork("10.0.0.2",true),true);
        var r=request();r.setRemoteAddr("10.0.0.2");r.addHeader("CF-Connecting-IP","8.8.8.8");r.addHeader("CF-IPCountry","US");
        r.addHeader("X-R4R-Usage-Opt-Out","1");
        try {var ticket=usage.begin(r);ticket.question("Do not retain this question");ticket.error("timeout");
            var row=db.next();assertNull(row.get(7));assertNull(row.get(15));assertEquals("US",row.get(16));
            assertEquals("ERROR",row.get(4));assertEquals("timeout",row.get(21));
        } finally {usage.close();}
    }
    @Test void countryStatisticsDoNotRequirePersistentIp() throws Exception {
        var db=new RecordingJdbc();var usage=new AssistantUsage(db.jdbc,true);usage.privacy(true,true);
        usage.network(new com.riansares.r4r.config.ClientNetwork("10.0.0.2",true),false);
        var r=request();r.setRemoteAddr("10.0.0.2");r.addHeader("CF-Connecting-IP","8.8.8.8");r.addHeader("CF-IPCountry","US");
        try {usage.begin(r).finish("ANSWERED");var row=db.next();assertNull(row.get(15));assertEquals("US",row.get(16));assertEquals("technical-geography-v1",row.get(17));}
        finally {usage.close();}
    }
    @Test void excludesObviousPersonalAndSensitiveText() throws Exception {
        var db=new RecordingJdbc();var usage=new AssistantUsage(db.jdbc,true);usage.privacy(true,true);
        try {var ticket=usage.begin(request());ticket.question("Email me at person@example.com");ticket.finish("ERROR");
            var row=db.next();assertNull(row.get(7));assertEquals("personal_identifier",row.get(19));
            ticket=usage.begin(request());ticket.question("Mi diagnóstico requiere tratamiento");ticket.finish("ERROR");
            row=db.next();assertNull(row.get(7));assertEquals("sensitive_content",row.get(19));
        } finally {usage.close();}
    }
    @Test void activationGateOverridesContentCollection() throws Exception {
        var db=new RecordingJdbc();var usage=new AssistantUsage(db.jdbc,true);usage.privacy(false,true);
        try {var ticket=usage.begin(request());ticket.question("ordinary technical question");ticket.finish("ERROR");
            var row=db.next();assertNull(row.get(7));assertNull(row.get(18));
        } finally {usage.close();}
    }
    @Test void auditUpdatesSameRowWithEffectiveStatus() throws Exception {
        var db=new RecordingJdbc();var usage=new AssistantUsage(db.jdbc,true);
        try {usage.audit("00000000-0000-4000-8000-000000000004",new com.riansares.r4r.rag.RagAuditResult(
            com.riansares.r4r.rag.AnswerStatus.ANSWERED,com.riansares.r4r.rag.AnswerStatus.PARTIAL,
            com.riansares.r4r.rag.AnswerStatus.PARTIAL,true,"Review qualification"));
            var row=db.next();assertEquals("PARTIAL",row.get(4));assertTrue((Boolean)row.get(1));
        } finally {usage.close();}
    }
    @Test void objectionWorksWithCollectionDisabled() throws Exception {
        var db=new RecordingJdbc();var usage=new AssistantUsage(db.jdbc,false);
        try {usage.object(request(),java.util.List.of("00000000-0000-4000-8000-000000000003"));
            var row=db.next();assertEquals("ATHAGON",row.get(2));assertTrue(db.writes.isEmpty());
        } finally {usage.close();}
    }
    @Test void viewsAreOriginBoundAndRateLimited() {
        var usage=mock(AssistantUsage.class);var controller=new AssistantViewController(usage);var r=new MockHttpServletRequest();
        assertEquals(403,controller.view(r).getStatusCode().value());r.addHeader("Origin","https://athagon.tech");
        for(int i=0;i<120;i++)assertEquals(202,controller.view(r).getStatusCode().value());
        assertEquals(429,controller.view(r).getStatusCode().value());verify(usage,times(120)).view(r);
    }
    @Test void objectionsRejectUnknownOriginAndMalformedReferences() {
        var usage=mock(AssistantUsage.class);var c=new AssistantViewController(usage);var r=new MockHttpServletRequest();
        assertEquals(403,c.object(r,new AssistantViewController.Objection(java.util.List.of())).getStatusCode().value());
        r.addHeader("Origin","https://athagon.tech");
        assertEquals(400,c.object(r,new AssistantViewController.Objection(java.util.List.of("bad"))).getStatusCode().value());
        verifyNoInteractions(usage);
    }
}
