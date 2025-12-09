package com.example.garvik.config;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.cloud.spring.core.GcpProjectIdProvider;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(
    FirestoreProperties.class) // Activate the properties class from Step 1
public class FirestoreConfig {

  private static final Logger logger = LoggerFactory.getLogger(FirestoreConfig.class);

  private final FirestoreProperties properties;
  private final GcpProjectIdProvider projectIdProvider;

  public FirestoreConfig(FirestoreProperties properties, GcpProjectIdProvider projectIdProvider) {
    this.properties = properties;
    this.projectIdProvider = projectIdProvider;
  }

  @Bean
  public Firestore firestore() throws IOException {
    String targetDatabase = properties.getDatabaseId();

    logger.info("=================================================================");
    logger.info("   Configuring Firestore with specific database ID.");
    logger.info("   Project ID: {}", projectIdProvider.getProjectId());
    logger.info("   Database ID: {}", targetDatabase);
    logger.info("=================================================================");

    // This is the core of the solution. We build FirestoreOptions manually.
    FirestoreOptions firestoreOptions =
        FirestoreOptions.newBuilder()
            .setProjectId(projectIdProvider.getProjectId())
            .setDatabaseId(targetDatabase) // <-- Set the specific database ID here
            .build();

    // Return the Firestore instance built with our custom options.
    // Spring Data Firestore will automatically pick up and use this bean.
    return firestoreOptions.getService();
  }
}
