package com.example.garvik.agent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import io.reactivex.rxjava3.core.Maybe;
import java.util.List;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** OrchestratorAgent is a placeholder class for future implementation of an orchestrator agent. */
@Component
public class OrchestratorAgent extends AdkClientBase {

  // No content needed for now.
  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(OrchestratorAgent.class);

  @Value("${mcp.server.urls:}")
  private List<String> mcpServerUrls;

  public BaseAgent ROOT_AGENT;

  @PostConstruct
  public void init() {
    trustAllCertificates();

    Callbacks.AfterModelCallback tokenLoggerCallback =
        (callbackContext, llmResponse) -> {
          try {
            Optional<GenerateContentResponseUsageMetadata> usageMetadata =
                llmResponse.usageMetadata();
            usageMetadata.ifPresent(
                metadata -> {
                  logger.info("LLM Request Token Count: {}", metadata.promptTokenCount());
                });
          } catch (Exception e) {
            logger.warn("Failed to log token usage from LlmResponse.", e);
          }
          return Maybe.empty();
        };

    ROOT_AGENT = initAgent(tokenLoggerCallback, mcpServerUrls);
  }
}
