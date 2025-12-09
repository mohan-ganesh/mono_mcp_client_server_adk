package com.example.garvik.agent;

import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.Callbacks;
import com.google.adk.agents.LlmAgent;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.mcp.McpToolset;
import com.google.adk.tools.mcp.SseServerParameters;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import io.reactivex.rxjava3.core.Flowable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

public class AdkClientBase {

  public static final Logger logger = LoggerFactory.getLogger(AdkClientBase.class);

  @Value("${gemini.model.name:gemini-2.5-flash}")
  private String modelName;

  /**
   * Initializes the BaseAgent with tools from MCP servers.
   *
   * @return The initialized BaseAgent.
   */
  public BaseAgent initAgent() {
    return initAgent(null, new ArrayList<>());
  }

  /**
   * Initializes the BaseAgent with tools from MCP servers and an optional callback.
   *
   * @param afterModelCallback An optional callback to run after the model call.
   * @return The initialized BaseAgent.
   */
  public BaseAgent initAgent(Callbacks.AfterModelCallback afterModelCallback) {
    return initAgent(afterModelCallback, new ArrayList<>());
  }

  /**
   * Initializes the BaseAgent with tools from MCP servers and an optional callback.
   *
   * @param afterModelCallback An optional callback to run after the model call.
   * @param mcpServerUrls A list of MCP server URLs to load tools from.
   * @return The initialized BaseAgent.
   */
  public BaseAgent initAgent(
      Callbacks.AfterModelCallback afterModelCallback, List<String> mcpServerUrls) {
    List<BaseTool> tools = new ArrayList<>();
    if (mcpServerUrls != null && !mcpServerUrls.isEmpty()) {
      logger.info("Loading tools from MCP server URLs: " + mcpServerUrls);
      mcpServerUrls.forEach(url -> tools.addAll(getTools(url)));
    }

    logger.info("Total tools loaded: " + tools.size());

    LlmAgent.Builder agentBuilder =
        LlmAgent.builder()
            .name("member-agent")
            .description(
                "You are helping member to get medical or prescription benefits, appointments.")
            .instruction(
                """
                You are a helpful assistant makes an appointment for pateint by provided details.
                Use the 'Create_Appointment' tool for this purpose.
                After creating the appointment, use the 'send_email' tool to notify the patient with
                the appointment details including appointment ID, date, time, and description.
                Always ensure to confirm the appointment creation and notification to the user.
                User 'Prescription_Benefits' and 'Medical_Benefits' tools to get benefits information if the user asks about benefits.
                """)
            .model(modelName)
            .tools(tools);

    if (afterModelCallback != null) {
      agentBuilder.afterModelCallback(afterModelCallback);
    }
    return agentBuilder.build();
  }

  /**
   * Loads tools from the MCP server.
   *
   * @return The list of tools.
   */
  protected static List<BaseTool> getTools(String mcpServerUrl) {
    List<BaseTool> tools = ImmutableList.of();

    logger.info("MCP Server URL from env: " + mcpServerUrl);

    if (mcpServerUrl == null || mcpServerUrl.trim().isEmpty()) {
      logger.info(" environment variable not set. No remote tools will be loaded.");

    } else {
      logger.info("Attempting to load tools from MCP server: " + mcpServerUrl);

      try {
        SseServerParameters params =
            SseServerParameters.builder()
                .url(mcpServerUrl)
                .headers(ImmutableMap.of("Authorization", "Bearer hello"))
                .build();

        logger.debug("URL in SseServerParameters object: {}", params.url());

        McpToolset mcpToolset = new McpToolset(params);

        if (mcpToolset == null) {
          logger.warn(
              "Failed to load tools from MCP server at "
                  + mcpServerUrl
                  + ". Load method returned null.");
        } else {
          Flowable<BaseTool> toolset = mcpToolset.getTools(null);
          if (toolset != null) {
            tools = toolset.toList().blockingGet();
            // Sanitize tool names via reflection to be compliant with Gemini API requirements.
            for (BaseTool tool : tools) {
              try {
                Field nameField = BaseTool.class.getDeclaredField("name");
                nameField.setAccessible(true);
                String originalName = tool.name();
                String sanitizedName = originalName.replaceAll("[^a-zA-Z0-9_.:-]", "_");
                if (!originalName.equals(sanitizedName)) {
                  logger.info(
                      "Sanitizing tool name from '"
                          + originalName
                          + "' to '"
                          + sanitizedName
                          + "'");
                  nameField.set(tool, sanitizedName);
                }
                logger.info(
                    "Loaded Tool: Name='{}', Description='{}', declartion='{}'",
                    sanitizedName,
                    tool.description(),
                    tool.declaration());
              } catch (NoSuchFieldException | IllegalAccessException e) {
                logger.warn("Failed to sanitize tool name via reflection", e);
              }
            }
            logger.info("Loaded " + tools.size() + " tools.");
          } else {
            tools = ImmutableList.of();
            logger.warn(
                "Proceeding with an empty tool list due to previous errors or no tools loaded.");
          }

          if (tools.isEmpty()) {
            logger.warn(
                "MCP_SERVER_URL was set, but no tools were loaded. Agent will function without"
                    + " these tools.");
          }
        }
      } catch (Exception e) {
        logger.warn(
            "Failed to load tools from MCP server at "
                + mcpServerUrl
                + ". Ensure the server is running and accessible, and the URL is correct.",
            e);
      }
    }

    return tools;
  }

  /** */
  protected static void trustAllCertificates() {
    try {
      TrustManager[] trustAllCerts =
          new TrustManager[] {
            new X509TrustManager() {
              public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
              }

              public void checkClientTrusted(
                  java.security.cert.X509Certificate[] certs, String authType) {}

              public void checkServerTrusted(
                  java.security.cert.X509Certificate[] certs, String authType) {}
            }
          };
      // Use "TLS" which is the modern standard, instead of "SSL"
      SSLContext sc = SSLContext.getInstance("TLS");
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      // Set the default SSL socket factory for HttpsURLConnection
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
      // Also set the default SSL context for the entire JVM
      SSLContext.setDefault(sc);
      HostnameVerifier allHostsValid = (hostname, session) -> true;
      HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
    } catch (Exception e) {
      logger.error("Error trusting all certificates", e);
    }
  }
}
