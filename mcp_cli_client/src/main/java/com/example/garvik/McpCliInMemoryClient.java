package com.example.garvik;

import com.google.adk.agents.RunConfig;
import com.google.adk.events.Event;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import com.google.adk.agents.BaseAgent;
import java.util.Scanner;
import java.util.Arrays;
import java.util.List;
import com.google.adk.runner.InMemoryRunner;
import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * An example ADK client that uses InMemoryRunner to run an agent with MCP integration.
 */
public class McpCliInMemoryClient extends AdkClientBase {

    public static void main(String[] args) {
        RunConfig runConfig = RunConfig.builder().build();
        String appName = "hello-time-agent";
        //pass the mcp urls
        String mcpUrl = "https://mcp-appointment-server-880624566657.us-central1.run.app/appointment-domain-tools/sse,https://mcp-benefits-server-880624566657.us-central1.run.app/benefits-domain-tools/sse";

        List<String> mcpServerUrls = Arrays.asList(mcpUrl.split(","));
        AdkClientBase client = new AdkClientBase();
        client.trustAllCertificates();
        BaseAgent timeAgent = client.initAgent(mcpServerUrls);
     
          

        InMemoryRunner runner = new InMemoryRunner(timeAgent);
         Session session = runner
                .sessionService()
                .createSession(appName, "user-1234")
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
        } finally {
           // Clean up resources if needed
        }
    }
}