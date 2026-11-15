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
        verify(usage, times(120)).view("RIANSARES4R");
    }
}
