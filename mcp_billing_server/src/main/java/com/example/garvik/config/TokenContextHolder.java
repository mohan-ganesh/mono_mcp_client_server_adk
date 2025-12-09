package com.example.garvik.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenContextHolder {

    public static Logger logger = LoggerFactory.getLogger(TokenContextHolder.class);

    private static final ThreadLocal<String> contextHolder = new ThreadLocal<>();

    /**
     * 
     * @param token
     */
    public static void setToken(String token) {
        logger.debug("Setting token in ThreadLocal: {}", token);
        contextHolder.set(token);
    }

    /**
     * 
     * @return
     */
    public static String getToken() {
        logger.debug("Getting token from ThreadLocal: {}", contextHolder.get());
        return contextHolder.get();
    }

    /**
     * 
     * @return
     */
    public static void clear() {
        logger.debug("Clearing token from ThreadLocal");
        contextHolder.remove();
    }
}