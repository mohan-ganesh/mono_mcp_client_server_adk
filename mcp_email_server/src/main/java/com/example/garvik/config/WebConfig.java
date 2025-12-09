package com.example.garvik.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * 
 * 
 */
@Configuration
public class WebConfig implements WebFluxConfigurer {

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry
        .addMapping("/**") // Apply to all endpoints
        .allowedOrigins("*") // Allow all origins
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Allowed methods
        .allowedHeaders("*") // Allow all headers
        .maxAge(3600); // Cache preflight response for 1 hour
  }
}
