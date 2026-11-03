package com.jcode.jcomercy.companymanagement.infra.graphql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryGraphQlRateLimiterTest {
    @Test
    void rejectsRequestsAfterConfiguredLimit() {
        var limiter = new InMemoryGraphQlRateLimiter();

        assertThat(limiter.tryAcquire("client-a", 2, 60)).isTrue();
        assertThat(limiter.tryAcquire("client-a", 2, 60)).isTrue();
        assertThat(limiter.tryAcquire("client-a", 2, 60)).isFalse();
    }

    @Test
    void isolatesCountersByClientKey() {
        var limiter = new InMemoryGraphQlRateLimiter();

        assertThat(limiter.tryAcquire("client-a", 1, 60)).isTrue();
        assertThat(limiter.tryAcquire("client-a", 1, 60)).isFalse();
        assertThat(limiter.tryAcquire("client-b", 1, 60)).isTrue();
    }
}
