package com.example.garvik.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionIdContextHolder {

    private static final Logger logger = LoggerFactory.getLogger(SessionIdContextHolder.class);
    private static final ThreadLocal<String> contextHolder = new ThreadLocal<>();

    public static void setSessionId(String sessionId) {
        logger.debug("Setting sessionId in ThreadLocal: {}", sessionId);
        contextHolder.set(sessionId);
    }

    public static String getSessionId() {
        return contextHolder.get();
    }

    public static void clear() {
        contextHolder.remove();
    }
}