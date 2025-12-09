package com.example.garvik.runner;

import com.google.adk.agents.BaseAgent;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.runner.Runner;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

public class FirestoreDatabaseRunner extends Runner {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(FirestoreDatabaseRunner.class);

  // read the project id and bucket name from properties file && add the code to
  // read from properties file
  public static final String BUCKET_NAME = "mg-garvik-data";
  public static final String PROJECT_ID = "mohan-ganesh";
  public static final Storage storage =
      StorageOptions.newBuilder().setProjectId(PROJECT_ID).build().getService();

  public FirestoreDatabaseRunner(BaseAgent agent, Firestore db) {
    this(agent, agent.name(), new java.util.ArrayList<>(), db);
  }

  public FirestoreDatabaseRunner(BaseAgent agent, String appName, Firestore db) {
    this(agent, appName, new java.util.ArrayList<>(), db);
  }

  public FirestoreDatabaseRunner(
      BaseAgent agent, String appName, java.util.List<BasePlugin> plugins, Firestore db) {
    super(
        agent,
        appName,
        new com.google.adk.artifacts.GcsArtifactService(BUCKET_NAME, storage),
        new FirestoreSessionService(db),
        new FirestoreMemoryService(db),
        plugins);
  }
}
