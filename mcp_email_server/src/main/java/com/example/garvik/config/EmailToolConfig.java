package com.example.garvik.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.garvik.service.EmailToolService;

@Configuration
public class EmailToolConfig {

  public Logger logger = LoggerFactory.getLogger(EmailToolConfig.class);

  @Bean
  public ToolCallbackProvider emailTools(EmailToolService benefitsToolService) {
    logger.debug("emailToolService called...");
    return MethodToolCallbackProvider.builder().toolObjects(benefitsToolService).build();
  }
}
