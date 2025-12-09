package com.example.garvik.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    logger.info("addCorsMappings() - start - ");
    registry
        .addMapping("/**")
        .allowedOriginPatterns("*") // Use allowedOriginPatterns instead of allowedOrigins
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*") // Allow all headers
        .allowCredentials(true); // Allow credentials
  }
}
