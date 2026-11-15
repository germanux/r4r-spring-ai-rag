package com.riansares.r4r.usage;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AssistantUsageTest {
    @Test void exactOriginsOnly() {
        assertEquals("RIANSARES4R", AssistantUsage.origin("https://www.riansares4r.com"));
        assertEquals("ATHAGON", AssistantUsage.origin("https://athagon.tech"));
        assertEquals("UNKNOWN", AssistantUsage.origin("https://riansares4r.com.evil.test"));
        assertEquals("UNKNOWN", AssistantUsage.origin(null));
    }
    @Test void oneTerminalEventAndDatabaseFailureDoesNotEscape() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        CountDownLatch called = new CountDownLatch(1);
        doAnswer(invocation -> { called.countDown(); throw new IllegalStateException("database unavailable"); })
                .when(jdbc).execute(any(ConnectionCallback.class));
        var request = new MockHttpServletRequest(); request.addHeader("Origin", "https://riansares4r.com");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var usage = new AssistantUsage(jdbc, true);
        try {
            var ticket = usage.begin();
            ticket.finish("PARTIAL"); ticket.finish("ERROR");
            assertTrue(called.await(2, TimeUnit.SECONDS));
            verify(jdbc, times(1)).execute(any(ConnectionCallback.class));
        } finally { usage.close(); RequestContextHolder.resetRequestAttributes(); }
    }
    @Test void disabledByDefaultDoesNotTouchDatabase() {
        var jdbc = mock(JdbcTemplate.class);
        var usage = new AssistantUsage(jdbc, false);
        usage.view("ATHAGON"); usage.begin().finish("ERROR"); usage.close();
        verifyNoInteractions(jdbc);
    }
    @Test void viewsRejectUnknownOriginAndHaveABoundedQuota() {
        var usage = mock(AssistantUsage.class);
        var controller = new AssistantViewController(usage);
        var request = new MockHttpServletRequest();
        assertEquals(403, controller.view(request).getStatusCode().value());
        request.addHeader("Origin", "https://riansares4r.com");
        for (int i=0;i<120;i++) assertEquals(202, controller.view(request).getStatusCode().value());
        assertEquals(429, controller.view(request).getStatusCode().value());
        verify(usage, times(120)).view(request);
    }

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
    private MockHttpServletRequest consentRequest() {
        var r = new MockHttpServletRequest(); r.addHeader("Origin","https://athagon.tech");
        r.addHeader("X-R4R-Visitor","00000000-0000-4000-8000-000000000001");
        r.addHeader("X-R4R-Session","00000000-0000-4000-8000-000000000002");
        return r;
    }
    @Test void noConsentStripsIdentifiersAndTextsEvenIfClientSendsThem() throws Exception {
        var db = new RecordingJdbc(); var usage = new AssistantUsage(db.jdbc,true); usage.privacy(true,true);
        try {
            var ticket=usage.begin(consentRequest()); ticket.question("private question");
            ticket.response(new com.riansares.r4r.rag.api.RagQueryResponse("private answer",false,java.util.List.of()),12.0);
            var row=db.next(); assertNull(row.get(6)); assertNull(row.get(7)); assertNull(row.get(11)); assertNull(row.get(12));
        } finally { usage.close(); }
    }
    @Test void separateConsentsPersistFullHistoryAndBrowserWithCorrelation() throws Exception {
        var db = new RecordingJdbc(); var usage = new AssistantUsage(db.jdbc,true); usage.privacy(true,true);
        var r=consentRequest(); r.addHeader("X-R4R-Analytics-Consent",AssistantUsage.CONSENT_VERSION);
        r.addHeader("X-R4R-History-Consent",AssistantUsage.CONSENT_VERSION);
        r.addHeader("X-R4R-Request-Id","00000000-0000-4000-8000-000000000003");
        try {
            var ticket=usage.begin(r); ticket.question("full question");
            ticket.response(new com.riansares.r4r.rag.api.RagQueryResponse("full answer",false,java.util.List.of()),12.0);
            ticket.finish("ERROR"); var row=db.next();
            assertEquals(java.util.UUID.fromString("00000000-0000-4000-8000-000000000003"),row.get(1));
            assertNotNull(row.get(6)); assertEquals("full question",row.get(11)); assertEquals("full answer",row.get(12));
            assertEquals(12.0,row.get(15)); assertEquals("ANSWERED",row.get(17)); assertTrue(db.writes.isEmpty());
        } finally { usage.close(); }
    }
    @Test void historyAloneNeverCreatesTrackingAndActivationGateOverridesConsent() throws Exception {
        var db=new RecordingJdbc(); var usage=new AssistantUsage(db.jdbc,true); usage.privacy(true,true);
        var r=consentRequest(); r.addHeader("X-R4R-History-Consent",AssistantUsage.CONSENT_VERSION);
        try {
            var ticket=usage.begin(r);ticket.question("optional history");ticket.finish("ERROR");
            var row=db.next();assertNull(row.get(6));assertEquals("optional history",row.get(11));
            usage.privacy(false,true);ticket=usage.begin(r);ticket.question("not approved");ticket.finish("ERROR");
            row=db.next();assertNull(row.get(11));assertNull(row.get(9));
        } finally { usage.close(); }
    }
    @Test void auditUpdatesExistingRowRatherThanInsertingAnotherQuery() throws Exception {
        var db=new RecordingJdbc();var usage=new AssistantUsage(db.jdbc,true);
        try {
            usage.audit("00000000-0000-4000-8000-000000000004",new com.riansares.r4r.rag.RagAuditResult(
                com.riansares.r4r.rag.AnswerStatus.ANSWERED,com.riansares.r4r.rag.AnswerStatus.PARTIAL,
                com.riansares.r4r.rag.AnswerStatus.PARTIAL,true,"review note"));
            var row=db.next();assertEquals("PARTIAL",row.get(3));
            assertTrue(((String)row.get(1)).contains("review note"));assertFalse(((String)row.get(2)).contains("review note"));
        } finally {usage.close();}
    }

    @Test void networkSecurityIsIndependentOfAnalyticsConsent() throws Exception {
        var db=new RecordingJdbc();var usage=new AssistantUsage(db.jdbc,true);usage.privacy(true,true);
        usage.network(new com.riansares.r4r.config.ClientNetwork("10.0.0.2",true),true);
        var r=consentRequest();r.setRemoteAddr("10.0.0.2");r.addHeader("CF-Connecting-IP","8.8.8.8");r.addHeader("CF-IPCountry","US");
        try {
            usage.begin(r).finish("ERROR");var row=db.next();
            assertNull(row.get(6));assertNull(row.get(11));assertEquals("8.8.8.8",row.get(19));
            assertEquals("US",row.get(20));assertEquals("availability-abuse-v1",row.get(21));
            usage.network(new com.riansares.r4r.config.ClientNetwork("10.0.0.2",true),false);
            usage.begin(r).finish("ERROR");row=db.next();assertNull(row.get(19));assertNull(row.get(20));
        } finally {usage.close();}
    }

    @Test void withdrawalStillRestrictsHistoryWhenCollectionIsDisabled() throws Exception {
        var db=new RecordingJdbc();var usage=new AssistantUsage(db.jdbc,false);
        var r=consentRequest();
        try {
            usage.withdraw(r,java.util.List.of("00000000-0000-4000-8000-000000000003"),true,true);
            var marker=db.next();assertEquals("ATHAGON",marker.get(2));
            var restriction=db.next();assertEquals("ATHAGON",restriction.get(1));
            var unlink=db.next();assertEquals("ATHAGON",unlink.get(1));
        } finally {usage.close();}
    }
    @Test void withdrawalEndpointNeverAcceptsUnknownOriginsOrUnboundedReferences() {
        var usage=mock(AssistantUsage.class);var controller=new AssistantViewController(usage);
        var request=new MockHttpServletRequest();
        assertEquals(403,controller.withdraw(request,new AssistantViewController.Withdrawal(java.util.List.of(),true,true)).getStatusCode().value());
        request.addHeader("Origin","https://athagon.tech");
        assertEquals(400,controller.withdraw(request,new AssistantViewController.Withdrawal(java.util.List.of("not-a-reference"),true,true)).getStatusCode().value());
        verifyNoInteractions(usage);
    }
}
