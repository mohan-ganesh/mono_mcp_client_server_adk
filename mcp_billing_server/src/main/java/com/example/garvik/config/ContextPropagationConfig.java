package com.example.garvik.config;

import io.micrometer.context.ContextRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

@Configuration
public class ContextPropagationConfig {

  @Bean
  public ContextRegistry contextRegistry() {
    // This hook is essential for automatic context propagation to work with Project Reactor.
    Hooks.enableAutomaticContextPropagation();
    ContextRegistry registry = ContextRegistry.getInstance();
    registry.registerThreadLocalAccessor(
        "tokenContext", TokenContextHolder::getToken, TokenContextHolder::setToken, TokenContextHolder::clear);
    registry.registerThreadLocalAccessor(
        "sessionIdContext", SessionIdContextHolder::getSessionId, SessionIdContextHolder::setSessionId, SessionIdContextHolder::clear);
    return registry;
  }
}