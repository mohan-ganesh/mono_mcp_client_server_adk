package com.example.garvik.service;

import com.example.garvik.config.SessionIdContextHolder;
import com.example.garvik.config.TokenContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

/** */
@Service
public class EmailToolService {

  public static Logger logger = LoggerFactory.getLogger(EmailToolService.class);

  private static final HttpClient httpClient =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Tool(
      name = "send_email",
      description = "Send an email to the specified recepient with the given subject and body")
  public Map<String, Object> sendEmail(
      @ToolParam(description = "Recepient's email address") String recipient,
      @ToolParam(description = "The subject of the email") String subject,
      @ToolParam(description = "The body content of the email") String emailBody,
      ToolContext toolContext) {

    logger.info("sendEmail called...");
    String authToken = TokenContextHolder.getToken();
    String sessionId = SessionIdContextHolder.getSessionId();

    if (authToken != null) {
      logger.info("Successfully retrieved Authorization token: {}", authToken);
    } else {
      logger.warn("Authorization token not found in context.");
    }

    if (sessionId != null) {
      logger.info("Successfully retrieved sessionId: {}", sessionId);
    } else {
      logger.warn("SessionId not found in context.");
    }    

    
    logger.info("Sending email to: {}", recipient);

    Map<String, Object> requestBody = Map.of("email", recipient, "message", emailBody, "subject", subject);

    String json;

    try {
      json = objectMapper.writeValueAsString(requestBody);
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
          .header("Authorization", "Bearer " + authToken)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json))
          .build();

      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 202) {
        logger.info("Email sent successfully!");
        return Map.of("status", "success", "message", "Email sent successfully!");
      } else {
        logger.error("Failed to send email. Status code: {}", response.statusCode());
        return Map.of("status", "error", "message", "Failed to send email. Status code: " + response.statusCode());
      }

      
    } catch (Exception e) {
      throw new RuntimeException("Failed to send email", e  );
    }
  }

}
