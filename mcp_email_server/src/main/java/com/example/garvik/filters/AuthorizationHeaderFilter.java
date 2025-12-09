package com.example.garvik.filters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


/**
 * 
 * 
 */
@Component
public class AuthorizationHeaderFilter implements WebFilter {

  private static final Logger logger = LoggerFactory.getLogger(AuthorizationHeaderFilter.class);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String authorizationHeader =
        exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    String sessionId = exchange.getRequest().getQueryParams().getFirst("sessionId");

    return chain
        .filter(exchange)
        .contextWrite(
            ctx -> {
              if (authorizationHeader != null) {
                logger.info("Storing Authorization header in context: {}", authorizationHeader);
                ctx = ctx.put("tokenContext", authorizationHeader);
              }
              if (sessionId != null) {
                logger.info("Storing sessionId in context: {}", sessionId);
                ctx = ctx.put("sessionIdContext", sessionId);
              }
              return ctx;
            })
        .contextCapture();
  }
}
