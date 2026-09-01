package com.jcode.jcomercy.inventory.infra.graphql;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "graphql.security.rate-limit-backend", havingValue = "redis")
public class RedisGraphQlRateLimiter implements GraphQlRateLimiter {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
        return count
        """, Long.class);

    private final StringRedisTemplate redis;

    public RedisGraphQlRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, int limit, long windowSeconds) {
        Long count = redis.execute(SCRIPT, List.of("jcomercy:graphql:rate:" + key),
                Long.toString(windowSeconds));
        return count != null && count <= limit;
    }
}
