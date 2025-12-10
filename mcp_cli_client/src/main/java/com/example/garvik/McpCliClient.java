package com.example.garvik;

import com.google.adk.agents.RunConfig;
import com.google.adk.sessions.FirestoreSessionService;
import com.google.adk.events.Event;
import com.google.adk.runner.FirestoreDatabaseRunner;
import com.google.adk.sessions.Session;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import com.google.adk.agents.BaseAgent;
import java.util.Scanner;
import java.util.random.RandomGenerator;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * An example ADK client that uses FirestoreDatabaseRunner to run an agent with MCP integration and persist session state in Firestore.
 */
public class McpCliClient extends AdkClientBase {

    public static void main(String[] args) {
        RunConfig runConfig = RunConfig.builder().build();
        String appName = "hello-time-agent";
        //pass the mcp urls
        String mcpUrl = "https://mcp-appointment-server-880624566657.us-central1.run.app/appointment-domain-tools/sse,https://mcp-benefits-server-880624566657.us-central1.run.app/benefits-domain-tools/sse";

        List<String> mcpServerUrls = Arrays.asList(mcpUrl.split(","));
        AdkClientBase client = new AdkClientBase();
        client.trustAllCertificates();
        BaseAgent timeAgent = client.initAgent(mcpServerUrls);
        // Initialize Firestore
        FirestoreOptions firestoreOptions = FirestoreOptions.getDefaultInstance();
        Firestore firestore = firestoreOptions.getService();

        
        // Use FirestoreDatabaseRunner to persist session state
       FirestoreDatabaseRunner runner = new FirestoreDatabaseRunner(
                timeAgent,
                appName,
                firestore);

        // Create a session
        String userId = "user-"+RandomGenerator.getDefault().nextInt();
        String sessionId = "session-"+RandomGenerator.getDefault().nextInt();
        Session session = new FirestoreSessionService(firestore)
                .createSession(appName,userId,null,sessionId)
                .blockingGet();

        try (Scanner scanner = new Scanner(System.in, UTF_8)) {
            while (true) {
                System.out.print("\nYou > ");
                String userInput = scanner.nextLine();
                if ("quit".equalsIgnoreCase(userInput) || "exit".equalsIgnoreCase(userInput)) {
                    break;
                }

                Content userMsg = Content.fromParts(Part.fromText(userInput));
                Flowable<Event> events = runner.runAsync(session.userId(), session.id(), userMsg, runConfig);

                System.out.print("\nAgent > ");
                events.blockingForEach(event -> {
                    if (event.finalResponse()) {
                        System.out.println(event.stringifyContent());
                    }
                });
            }
        }finally {
      // Close the Firestore client to release resources and shut down background threads.
      if (firestore != null) {
        try {
          firestore.close();
          logger.info("Firestore client closed successfully.");
        } catch (Exception e) {
          logger.error( "Error closing Firestore client.", e);
        }
      }
      // Explicitly exit the application to ensure all threads are terminated.
      System.exit(0);
    }
    }


     
    
}