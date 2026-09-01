package com.jcode.jcomercy.inventory.infra.graphql;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(name = "graphql.security.rate-limit-backend", havingValue = "memory", matchIfMissing = true)
public class InMemoryGraphQlRateLimiter implements GraphQlRateLimiter {
    private final Clock clock = Clock.systemUTC();
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int limit, long windowSeconds) {
        var now = Instant.now(clock);
        var window = windows.compute(key, (ignored, current) -> {
            if (current == null || now.isAfter(current.expiresAt())) {
                return new Window(now.plusSeconds(windowSeconds), new AtomicInteger(1));
            }
            current.requests().incrementAndGet();
            return current;
        });
        return window.requests().get() <= limit;
    }

    private record Window(Instant expiresAt, AtomicInteger requests) { }
}
