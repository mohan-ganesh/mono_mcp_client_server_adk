package com.example.garvik.util;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class OcrCall {

  private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(OcrCall.class);

  private static final String OCR_API_URL =
      "https://ocr-api-880624566657.us-central1.run.app/ocr-services/document/v2/extract?prompt=extract%20the%20details&outputFormat=natural";

  /**
   * @param documentData
   * @return
   */
  public static Map<String, String> callOcrService(byte[] documentData) {
    logger.info("Calling external OCR service for document of size: {} bytes", documentData.length);
    try {
      // Create a trust manager that does not validate certificate chains
      // WARNING: This is insecure and should only be used for development/testing.
      TrustManager[] trustAllCerts =
          new TrustManager[] {
            new X509TrustManager() {
              public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
              }

              public void checkClientTrusted(X509Certificate[] certs, String authType) {}

              public void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
          };

      SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(OCR_API_URL))
              .header("Content-Type", "application/octet-stream")
              .POST(HttpRequest.BodyPublishers.ofByteArray(documentData))
              .build();

      HttpClient httpClient =
          HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).sslContext(sslContext).build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() == 200) {
        logger.info("Successfully received OCR response: {}", response.body());
        // Assuming the OCR service returns a JSON with keys like "cardNumber",
        String responseData = response.body();
        return Map.of("status", "success", "report", responseData);

      } else {
        logger.error(
            "Failed to call OCR service. Status: {}, Body: {}",
            response.statusCode(),
            response.body());
        return Map.of(
            "status",
            "error",
            "report",
            "Failed to extract credit card details. OCR service returned status "
                + response.statusCode());
      }
    } catch (IOException | InterruptedException e) {
      logger.error("Error calling OCR service", e);
      Thread.currentThread().interrupt();
      return Map.of(
          "status",
          "error",
          "report",
          "An error occurred while trying to extract credit card details: " + e.getMessage());
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      logger.error("Error calling OCR service (block 2)", e);
      Thread.currentThread().interrupt();
      return Map.of(
          "status",
          "error",
          "report",
          "An error occurred while trying to extract credit card details (block 2): "
              + e.getMessage());
    }
  }
}
