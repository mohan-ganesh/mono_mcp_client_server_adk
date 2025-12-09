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
public class BillingToolService {

  public static Logger logger = LoggerFactory.getLogger(BillingToolService.class);

  private static final HttpClient httpClient =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).build();

  private static final ObjectMapper objectMapper = new ObjectMapper();

  @Tool(
      name = "authorizeBilling",
      description = "Authorize the billing process for a customer")
  public Map<String, Object> authorizeBilling(
      @ToolParam(description = "Customer Billing Id") String billId, 
      ToolContext toolContext) {

    logger.info("authorizeBilling called...");
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

    //in real application one would write a process to validate the information and then generate the auth id.
   
    //generate the mock auth id for demo functional purpose
    String authId = UUID.randomUUID().toString();
    logger.info("Successfully generated authId: {}", authId);
    return Map.of("authId", "AUTH-"+authId);    
  }


  /**
   * 
   * @param name
   * @param cardNumber
   * @param cardExpiry
   * @param cardCvv
   * @param toolContext
   * @return
   */
  @Tool(
      name = "processPayment",
      description = "Process the payment for a customer")
  public Map<String, Object> processPayment(
      @ToolParam(description = "Customer Name") String name, 
      @ToolParam(description = "Card Number") String cardNumber,
      @ToolParam(description = "Card Expiry") String cardExpiry,
      @ToolParam(description = "Card CVV") String cardCvv,
      ToolContext toolContext) {

    logger.info("authorizeBilling called...");
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
    //in real application one would write a process to validate the information and then call the real API to process the paymnet and generate the confirmation.       
    //generate the mock for payment id confirmation.

    String paymentId = UUID.randomUUID().toString();
    logger.info("Successfully generated paymentId: {}", paymentId);
    

    return Map.of("paymentId", "PAYMENT-"+paymentId);    
  }


}
