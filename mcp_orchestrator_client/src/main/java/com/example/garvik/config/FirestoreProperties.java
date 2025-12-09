package com.example.garvik.config;

// Or your appropriate config package

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gcp.firestore")
public class FirestoreProperties {
  public static final Logger logger = LoggerFactory.getLogger(FirestoreProperties.class);

  /** The ID of the Firestore database to use (e.g., "(default)" or "prod-garvik"). */
  private String databaseId = "(default)"; // A safe default value

  // Standard getter and setter
  public String getDatabaseId() {
    logger.info("Database ID: {}", databaseId);
    return databaseId;
  }

  public void setDatabaseId(String databaseId) {
    this.databaseId = databaseId;
  }
}
