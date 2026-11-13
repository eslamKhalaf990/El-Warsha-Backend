package com.warsha.erp.services;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-minute caps on POST /events, so a broken or hostile client can't flood the events
 * table. The store sends about one batch every 10 seconds, far below the per-visitor cap;
 * the per-IP cap is loose because many phones share one carrier IP.
 * In memory only: a restart simply starts a new window.
 */
@Component
public class TrackingRateLimiter {

    static final int MAX_PER_VISITOR = 30;
    static final int MAX_PER_IP = 600;

    private final Clock clock;
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private volatile long window = -1;

    public TrackingRateLimiter() {
        this(Clock.systemUTC());
    }

    TrackingRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Counts one request and says whether it is still within both caps. */
    public boolean allow(String visitorId, String ip) {
        long minute = clock.millis() / 60_000;
        if (minute != window) {
            synchronized (this) {
                if (minute != window) {
                    counts.clear();
                    window = minute;
                }
            }
        }
        boolean visitorOk = count("v:" + visitorId) <= MAX_PER_VISITOR;
        boolean ipOk = ip == null || count("ip:" + ip) <= MAX_PER_IP;
        return visitorOk && ipOk;
    }

    private int count(String key) {
        return counts.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet();
    }
}
