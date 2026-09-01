package com.jcode.jcomercy.companymanagement.infra.graphql;

public interface GraphQlRateLimiter {
    boolean tryAcquire(String key, int limit, long windowSeconds);
}
