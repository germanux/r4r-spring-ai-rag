package com.riansares.r4r.rag;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OrderedProviderFailoverTest {
    @Test void usesPreferredWhenHealthy() {
        var result = OrderedProviderFailover.execute(
                List.of(new OrderedProviderFailover.Target<>("desktop", "ok"),
                        new OrderedProviderFailover.Target<>("remote", "backup")),
                target -> true, client -> client, error -> true, target -> fail("unexpected fallback"));
        assertEquals("desktop", result.providerId());
        assertEquals("ok", result.value());
    }

    @Test void fallsBackOnRecoverableFailureOnly() {
        List<String> visited = new ArrayList<>();
        var result = OrderedProviderFailover.execute(
                List.of(new OrderedProviderFailover.Target<>("desktop", "offline"),
                        new OrderedProviderFailover.Target<>("remote", "ok")),
                target -> true,
                client -> { if (client.equals("offline")) throw new IllegalStateException("offline"); return client; },
                error -> error instanceof IllegalStateException,
                target -> visited.add(target.id()));
        assertEquals("remote", result.providerId());
        assertEquals(List.of("remote"), visited);
    }

    @Test void doesNotHideNonRecoverableFailure() {
        var error = assertThrows(IllegalArgumentException.class, () -> OrderedProviderFailover.execute(
                List.of(new OrderedProviderFailover.Target<>("local", "bad"),
                        new OrderedProviderFailover.Target<>("remote", "ok")),
                target -> true,
                client -> { if (client.equals("bad")) throw new IllegalArgumentException("bad config"); return client; },
                exception -> false,
                target -> fail("must not fallback")));
        assertEquals("bad config", error.getMessage());
    }

    @Test void eligibleFilterSkipsUnavailableProviders() {
        var result = OrderedProviderFailover.execute(
                List.of(new OrderedProviderFailover.Target<>("desktop", "off"),
                        new OrderedProviderFailover.Target<>("laptop", "on")),
                target -> !target.client().equals("off"), client -> client,
                error -> true, target -> fail("ineligible is not an attempted failure"));
        assertEquals("laptop", result.providerId());
    }

    @Test void exhaustionPreservesCause() {
        var error = assertThrows(OrderedProviderFailover.Exhausted.class, () -> OrderedProviderFailover.execute(
                List.of(new OrderedProviderFailover.Target<>("desktop", "fail")),
                target -> true, client -> { throw new IllegalStateException("offline"); },
                cause -> true, target -> { }));
        assertInstanceOf(IllegalStateException.class, error.getCause());
    }
}
