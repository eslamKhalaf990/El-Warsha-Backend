package com.warsha.erp.services;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingRateLimiterTest {

    private final AtomicLong now = new AtomicLong(Instant.parse("2026-10-31T10:00:00Z").toEpochMilli());
    private final TrackingRateLimiter limiter = new TrackingRateLimiter(
            new Clock() {
                @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(java.time.ZoneId zone) { return this; }
                @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
            });

    @Test
    void capsEachVisitorPerMinuteAndResetsNextMinute() {
        for (int i = 0; i < TrackingRateLimiter.MAX_PER_VISITOR; i++) {
            assertThat(limiter.allow("visitor-a", "1.1.1.1")).isTrue();
        }
        assertThat(limiter.allow("visitor-a", "1.1.1.1")).isFalse();
        assertThat(limiter.allow("visitor-b", "1.1.1.1")).isTrue();

        now.addAndGet(60_000);
        assertThat(limiter.allow("visitor-a", "1.1.1.1")).isTrue();
    }

    @Test
    void capsOneIpAcrossManyVisitors() {
        for (int i = 0; i < TrackingRateLimiter.MAX_PER_IP; i++) {
            assertThat(limiter.allow("visitor-" + i, "2.2.2.2")).isTrue();
        }
        assertThat(limiter.allow("visitor-new", "2.2.2.2")).isFalse();
        assertThat(limiter.allow("visitor-new", "3.3.3.3")).isTrue();
    }
}
