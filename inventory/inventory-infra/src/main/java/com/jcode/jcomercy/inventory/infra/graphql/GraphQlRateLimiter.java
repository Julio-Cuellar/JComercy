package com.jcode.jcomercy.inventory.infra.graphql;

public interface GraphQlRateLimiter {
    boolean tryAcquire(String key, int limit, long windowSeconds);
}
