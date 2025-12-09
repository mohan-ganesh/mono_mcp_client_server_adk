package com.example.garvik.controller;

import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.example.garvik.exception.InvalidTokenException;

public abstract class PublicAbstractSecureController {

  protected final Log logger = LogFactory.getLog(getClass());

  private final RestTemplate restTemplate = new RestTemplate();

  @Value("${auth.token.info.url}")
  private String authTokenInfoUrl;

  @Value("${auth.token.identity.domain}")
  private String authTokenIdentityDomain;

  /**
   * @param token
   * @return
   */
  protected Map<String, Object> verifyAndGetTokenDetails(String token) {
    logger.info("verifyAndGetTokenDetails(token) - Verifying token...");
    if (token == null || token.isEmpty()) {
      throw new InvalidTokenException("Authorization token is missing or empty.");
    }

    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", token);
    HttpEntity<String> entity = new HttpEntity<>(headers);

    try {
      ResponseEntity<Map> response =
          restTemplate.exchange(
              authTokenInfoUrl + "?identityDomain=" + authTokenIdentityDomain,
              HttpMethod.GET,
              entity,
              Map.class);

      if (response.getStatusCode() == HttpStatus.OK) {
        logger.info("verifyAndGetTokenDetails(token) - Token is valid.");
        return response.getBody();
      } else {
        throw new InvalidTokenException(
            "Token validation failed with status: " + response.getStatusCode());
      }
    } catch (HttpClientErrorException e) {
      logger.error(
          "verifyAndGetTokenDetails(token) - Token validation failed: "
              + e.getResponseBodyAsString(),
          e);
      throw new InvalidTokenException("Token validation failed. Status: " + e.getStatusCode(), e);
    } catch (Exception e) {
      logger.error(
          "verifyAndGetTokenDetails(token) - An unexpected error occurred during token validation.",
          e);
      throw new InvalidTokenException(
          "verifyAndGetTokenDetails(token) - An unexpected error occurred during token validation.",
          e);
    }
  }

  /**
   * @param token
   * @return
   */
  protected Map<String, Object> getUserMapFromToken(String token) {
    Map<String, Object> tokenDetails = verifyAndGetTokenDetails(token);
    // tokenDetails.forEach((key, value) -> logger.info(key + ": " + value));
    return tokenDetails;
  }

  protected String getUserIdFromToken(String token) {
    Map<String, Object> tokenDetails = verifyAndGetTokenDetails(token);
    if (tokenDetails != null && tokenDetails.containsKey("uid")) {
      return tokenDetails.get("uid").toString().toLowerCase();
    } else {
      throw new InvalidTokenException("User ID ('uid') not found in token.");
    }
  }
}
