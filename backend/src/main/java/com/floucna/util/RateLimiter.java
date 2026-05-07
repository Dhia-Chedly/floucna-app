package com.floucna.util;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {

    private final ConcurrentHashMap<String, ArrayDeque<Long>> windows = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long windowMs;

    public RateLimiter(int maxAttempts, long windowMs) {
        this.maxAttempts = maxAttempts;
        this.windowMs = windowMs;
    }

    public void check(String key) {
        long now = System.currentTimeMillis();
        ArrayDeque<Long> window = windows.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            long cutoff = now - windowMs;
            while (!window.isEmpty() && window.peekFirst() <= cutoff) {
                window.pollFirst();
            }
            if (window.size() >= maxAttempts) {
                throw new ApiException(429, "Too many attempts. Please try again later.");
            }
            window.addLast(now);
        }
    }

    public void reset(String key) {
        windows.remove(key);
    }
}
