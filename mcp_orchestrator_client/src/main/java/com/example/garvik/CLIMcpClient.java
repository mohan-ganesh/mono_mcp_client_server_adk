package com.example.garvik;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.example.garvik.agent.AdkClientBase;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.runner.FirestoreDatabaseRunner;
import com.google.adk.sessions.FirestoreSessionService;
import com.google.adk.sessions.Session;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CLIMcpClient extends AdkClientBase {
  private static final Logger logger = Logger.getLogger(CLIMcpClient.class.getName());

  public static void main(String[] args) {
    // Trust all SSL certificates. This is for development/testing only and should not be used in
    // production.
    trustAllCertificates();
    AdkClientBase client = new AdkClientBase();

    RunConfig runConfig = RunConfig.builder().build();
    String appName = "orchestrator-app";


     List<String> mcpServerUrls = List.of(
       "https://mcp-appointment-server-880624566657.us-central1.run.app/appointment-domain-tools/sse",
       "https://mcp-benefits-server-880624566657.us-central1.run.app/benefits-domain-tools/sse"
    );
    BaseAgent timeAgent = client.initAgent(mcpServerUrls);

    // Initialize Firestore
    FirestoreOptions firestoreOptions = FirestoreOptions.getDefaultInstance();
    Firestore firestore = firestoreOptions.getService();

    // Use FirestoreDatabaseRunner to persist session state
    FirestoreDatabaseRunner runner = new FirestoreDatabaseRunner(timeAgent, appName, firestore);

    // generate a random number for session id
    String sessionId = String.valueOf(System.currentTimeMillis());
    String userId = "user_" + Double.toString(Math.random()).substring(2, 8);
    Session session =
        new FirestoreSessionService(firestore)
            .createSession(appName, userId, null, sessionId)
            .blockingGet();

    try (Scanner scanner = new Scanner(System.in, UTF_8)) {
      try {
        while (true) {
          System.out.print("\nYou > ");
          String userInput = scanner.nextLine();
          if ("quit".equalsIgnoreCase(userInput) || "exit".equalsIgnoreCase(userInput)) {
            break;
          }
          if (userInput.trim().isEmpty()) continue;
          Content userMsg = Content.fromParts(Part.fromText(userInput));
          Flowable<Event> events =
              runner.runAsync(session.userId(), session.id(), userMsg, runConfig);

          System.out.print("\nAgent > ");
          events.blockingForEach(
              event -> {
                if (event.finalResponse()) {
                  System.out.println(event.stringifyContent());
                }
              });
        }
      } catch (NoSuchElementException e) {
        logger.info("Input stream closed. Exiting.");
      }
    } finally {
      // Close the Firestore client to release resources and shut down background threads.
      if (firestore != null) {
        try {
          firestore.close();
          logger.info("Firestore client closed successfully.");
        } catch (Exception e) {
          logger.log(Level.SEVERE, "Error closing Firestore client.", e);
        }
      }
      // Explicitly exit the application to ensure all threads are terminated.
      System.exit(0);
    }
  }
}
