package com.example.garvik.controller;

import com.example.garvik.agent.OrchestratorAgent;
import com.example.garvik.config.Constants;
import com.example.garvik.pojo.voice.AudioMessage;
import com.example.garvik.pojo.voice.ConversationTurn;
import com.example.garvik.runner.FirestoreDatabaseRunner;
import com.example.garvik.service.GoogleSttService;
import com.example.garvik.service.GoogleTtsService;
import com.example.garvik.service.UserPreferences;
import com.example.garvik.util.OcrCall;
import com.google.adk.agents.RunConfig;
import com.google.adk.artifacts.GcsArtifactService;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.SessionNotFoundException;
import com.google.cloud.firestore.Firestore;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Maybe;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** OrchestrationController handles HTTP requests related to orchestration tasks, */
@RestController
public class OrchestrationController extends PublicAbstractSecureController {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(OrchestrationController.class);

  private final FirestoreDatabaseRunner firestoreDatabaseRunner;
  private final GcsArtifactService gcsArtifactService;

  @Autowired private GoogleSttService sttService;

  @Autowired private GoogleTtsService ttsService;

  @Autowired private UserPreferences userPreferences;

  private static final String APP_NAME = "orchestrator-app";

  // Use constructor injection for dependencies
  @Autowired
  public OrchestrationController(
      Firestore firestore,
      OrchestratorAgent multiToolAgent,
      GoogleSttService sttService,
      GoogleTtsService ttsService) {
    // Initialize the FirestoreDatabaseRunner with all required services,
    // including the GcsArtifactService for file handling.
    firestoreDatabaseRunner =
        new FirestoreDatabaseRunner(
            multiToolAgent.ROOT_AGENT,
            APP_NAME,
            new ArrayList<>(), // No plugins needed for this agent
            firestore);
    this.gcsArtifactService =
        new GcsArtifactService(
            FirestoreDatabaseRunner.BUCKET_NAME, FirestoreDatabaseRunner.storage);

    this.ttsService = ttsService;
    this.sttService = sttService;
  }

  @PostMapping(
      value = "/v1/firestore/chat",
      consumes = {"application/json"})
  public CompletableFuture<Map<String, Object>> chat(
      @RequestParam String message,
      @RequestParam(required = true) String sessionId,
      @RequestHeader(value = Constants.AUTHORIZATION_KEY, required = true) String authorization) {
    logger.info("chat() - received message: " + message + " for sessionId: " + sessionId);
    Map<String, Object> userMap = getUserMapFromToken(authorization);
    String userId = userMap.get(Constants.UID_KEY).toString();
    return doChat(userId, message, sessionId, null, userMap);
  }

  /*
   * @PostMapping(value = "/v1/firestore/chat", consumes = { "multipart/form-data"
   * })
   * public CompletableFuture<Map<String, Object>> chatWithFiles(@RequestParam
   * String message,
   *
   * @RequestParam(required = true) String sessionId,
   *
   * @RequestPart(value = "documents", required = false) MultipartFile
   * documents[], @RequestHeader(value = Constants.AUTHORIZATION_KEY, required =
   * true) String authorization) {
   * logger.info("chatWithFiles() - received message: " + message +
   * " for sessionId: " + sessionId);
   * Map<String, Object> userMap = getUserMapFromToken(authorization);
   * String userId = userMap.get(Constants.UID_KEY).toString();
   * return doChat(userId,message, sessionId, documents);
   * }
   */
  @PostMapping(
      value = "/v1/firestore/chat",
      consumes = {"application/octet-stream", "application/json"})
  public CompletableFuture<Map<String, Object>> chatWithDataUrl(
      @RequestParam String message,
      @RequestParam(required = true) String sessionId,
      @RequestBody(required = false) byte[] documents,
      @RequestHeader(value = Constants.AUTHORIZATION_KEY, required = true) String authorization) {
    logger.info(
        "chatWithDataUrl() - received message: " + message + " for sessionId: " + sessionId);
    Map<String, Object> userMap = getUserMapFromToken(authorization);
    String userId = userMap.get(Constants.UID_KEY).toString();
    return doChatForFormPost(userId, message, sessionId, documents, userMap);
  }

  @PostMapping(
      value = "/v1/firestore/chat/local",
      consumes = {"application/octet-stream", "application/json"})
  public CompletableFuture<Map<String, Object>> chatWithDataUrllocal(
      @RequestParam String message,
      @RequestParam(required = true) String sessionId,
      @RequestBody(required = false) byte[] documents,
      @RequestHeader(value = Constants.AUTHORIZATION_KEY, required = true) String authorization) {
    logger.info(
        "chatWithDataUrl() - received message: " + message + " for sessionId: " + sessionId);
    Map<String, Object> userMap = getUserMapFromToken(authorization);
    String userId = userMap.get(Constants.UID_KEY).toString();
    return doChatTestLocal(userId, message, sessionId, documents, userMap);
  }

  @MessageMapping("/conversation/process")
  @SendTo("/topic/conversation")
  public ConversationTurn handleConversationTurn(
      @org.springframework.messaging.handler.annotation.Payload AudioMessage audioMessage,
      @Header("Authorization") String authorization) {
    logger.info("handleConversationTurn --- Received request for conversation processing.");

    try {
      // You can now use the authorization header for validation
      logger.info("Authorization Header received: {}", authorization);
      Map<String, Object> userMap = getUserMapFromToken(authorization);
      int dataLength =
          audioMessage.getAudioData() != null ? audioMessage.getAudioData().length() : 0;
      logger.info("Received audio message of size: {}", dataLength);

      // 1. Decode and Transcribe User's Audio
      byte[] audioBytes = Base64.getDecoder().decode(audioMessage.getAudioData());
      logger.info("Decoded audio data. Size: {} bytes.", audioBytes.length);
      Optional<Map<String, Object>> userVoice =
          userPreferences.getUserPreferences(userMap.get(Constants.UID_KEY).toString());
      String userTranscript = sttService.transcribe(audioBytes, userVoice);
      logger.info("User transcript: '{}'", userTranscript);

      // 2. Pass the transcript to the agent via doVoiceOver
      String userId = userMap.get(Constants.UID_KEY).toString();
      String sessionId = audioMessage.getSessionId(); // Use the sessionId from the client
      CompletableFuture<List<Map<String, String>>> agentResponseFuture =
          doVoiceOver(userId, userTranscript, sessionId, null, userMap);

      // Block and wait for the agent's response.
      List<Map<String, String>> agentResponses = agentResponseFuture.get();

      // Concatenate all text parts from the agent's response.
      String geminiResponseText =
          agentResponses.stream()
              .filter(response -> "text".equals(response.get("type")))
              .map(response -> response.get("content"))
              .reduce((acc, text) -> acc + " " + text)
              .orElse("I don't have a response for that.");

      logger.info("Gemini final response: '{}'", geminiResponseText);

      // 3. Synthesize Gemini's Response to Audio
      String geminiAudioBase64 = ttsService.generateSpeechWithGemini(geminiResponseText, userVoice);

      return new ConversationTurn(userTranscript, geminiResponseText, geminiAudioBase64);
    } catch (Exception e) {
      logger.error("Error in conversation turn: ", e);
      // In case of error, send back an error message to be played.
      try {
        String errorAudio = ttsService.generateSpeechWithGemini("Sorry, an error occurred.", null);
        return new ConversationTurn("Error", "Sorry, an error occurred.", errorAudio);
      } catch (Exception ex) {
        return new ConversationTurn("Error", "A critical error occurred.", null);
      }
    }
  }

  @MessageMapping("/conversation/text")
  @SendTo("/topic/conversation")
  public ConversationTurn handleTextMessage(
      @org.springframework.messaging.handler.annotation.Payload AudioMessage textMessage,
      @Header("Authorization") String authorization) {
    logger.info("handleTextMessage --- Received request for text message processing.");

    try {
      Map<String, Object> userMap = getUserMapFromToken(authorization);
      String userTranscript = textMessage.getTextData();
      logger.info("User transcript from text: '{}'", userTranscript);

      String userId = userMap.get(Constants.UID_KEY).toString();
      String sessionId = textMessage.getSessionId();
      CompletableFuture<List<Map<String, String>>> agentResponseFuture =
          doVoiceOver(userId, userTranscript, sessionId, null, userMap);

      List<Map<String, String>> agentResponses = agentResponseFuture.get();
      Optional<Map<String, Object>> userVoice =
          userPreferences.getUserPreferences(userMap.get(Constants.UID_KEY).toString());
      String geminiResponseText =
          agentResponses.stream()
              .filter(response -> "text".equals(response.get("type")))
              .map(response -> response.get("content"))
              .reduce((acc, text) -> acc + " " + text)
              .orElse("I don't have a response for that.");

      logger.info("Gemini final response: '{}'", geminiResponseText);

      String geminiAudioBase64 = ttsService.generateSpeechWithGemini(geminiResponseText, userVoice);

      return new ConversationTurn(userTranscript, geminiResponseText, geminiAudioBase64);
    } catch (Exception e) {
      logger.error("Error in text message turn: ", e);
      return new ConversationTurn("Error", "Sorry, an error occurred processing your text.", null);
    }
  }

  private CompletableFuture<Map<String, Object>> doChatTestLocal(
      String userId,
      String message,
      String sessionId,
      byte[] documents,
      Map<String, Object> userMap) {
    if (documents != null && documents.length > 0) {

      MultipartFile multipartFile =
          new MockMultipartFile("file", "uploaded-file", "application/octet-stream", documents);
      return doChat(userId, message, sessionId, new MultipartFile[] {multipartFile}, userMap);
    } else {
      return doChat(userId, message, sessionId, (MultipartFile[]) null, userMap);
    }
  }

  /*
   * Common method to handle chat logic with or without file uploads
   */
  private CompletableFuture<Map<String, Object>> doChat(
      String userId,
      String message,
      String sessionId,
      MultipartFile[] documents,
      Map<String, Object> userMap) {

    logger.info("doChat() - processing message: " + message + " for sessionId: " + sessionId);

    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
    List<Map<String, String>> responses = new ArrayList<>();

    // Get session or create a new one if it doesn't exist.
    // We create a config to ensure all previous events are loaded with the session.
    GetSessionConfig config = GetSessionConfig.builder().build();

    firestoreDatabaseRunner
        .sessionService()
        .getSession(APP_NAME, userId, sessionId, Optional.of(config))
        .onErrorResumeNext(
            throwable -> {
              if (throwable instanceof SessionNotFoundException) {
                logger.warn("Session not found for {}, creating a new one.", sessionId);
                ConcurrentMap<String, Object> sessionData = new ConcurrentHashMap<>();
                sessionData.put(Constants.USER_ID_KEY, userId);
                sessionData.put(
                    Constants.GIVEN_NAME_KEY,
                    userMap.getOrDefault(Constants.GIVEN_NAME_KEY, "unknown-first-name"));
                sessionData.put(
                    Constants.SIR_NAME_KEY,
                    userMap.getOrDefault(Constants.SIR_NAME_KEY, "unknown-last-name"));
                // add a condition check first to make sure email exist and then add to the
                // session
                if (userMap.containsKey(Constants.EMAIL_KEY)) {
                  logger.debug("email " + userMap.get(Constants.EMAIL_KEY));
                  sessionData.put(Constants.EMAIL_KEY, userMap.get(Constants.EMAIL_KEY));
                }
                return firestoreDatabaseRunner
                    .sessionService()
                    .createSession(APP_NAME, userId, sessionData, sessionId)
                    .toMaybe();
              } else {
                return Maybe.error(throwable);
              }
            })
        .toSingle()
        .subscribe(
            session -> {
              logger.info("Using session with ID: " + session.id());

              // Log the conversation history being sent to the agent
              logger.info("--- Conversation History (Session: {}) ---", session.id());
              session
                  .events()
                  .forEach(
                      event -> {
                        String authorForDisplay =
                            event.author().equalsIgnoreCase(session.userId())
                                ? event.author()
                                : "model";
                        logger.info("[{}] {}", authorForDisplay, event.stringifyContent());
                      });
              logger.info("------------------------------------------");

              // Construct the message with file parts if they exist.
              // This now returns both the enriched message and the content for the agent.
              Map<String, Object> constructedMessage =
                  constructUserMessage(message, documents, session.id());
              Content finalUserMessage = (Content) constructedMessage.get("content");
              String ocrMessage = (String) constructedMessage.get("ocrResponse");

              logger.info("doChat() - sending message to the agent...");

              firestoreDatabaseRunner
                  .runAsync(session, finalUserMessage, RunConfig.builder().build())
                  .doOnComplete(
                      () -> {
                        logger.info(
                            "doChat() - end - for session id is {} , and user id is {}.",
                            sessionId,
                            userId);
                        Map<String, Object> result =
                            Map.of(
                                "sessionId", sessionId,
                                "userId", userId,
                                "responses", responses);
                        future.complete(result);
                      })
                  .subscribe(
                      event -> { // onNext
                        logger.info("Event: " + event.stringifyContent());
                        event
                            .content()
                            .ifPresent(
                                content ->
                                    content
                                        .parts()
                                        .ifPresent(
                                            parts -> {
                                              // Do not add our specific error messages to the
                                              // response list or history.
                                              String responseText =
                                                  parts.stream()
                                                      .findFirst()
                                                      .flatMap(Part::text)
                                                      .orElse("");
                                              if (responseText.contains(
                                                  "The model did not return a response")) {
                                                return; // Skip this event
                                              }
                                              for (Part part : parts) {
                                                part.text()
                                                    .ifPresent(
                                                        text -> {
                                                          if (!text.isEmpty()) {
                                                            logger.info(
                                                                "Agent response part: " + text);
                                                            if (ocrMessage != null
                                                                && !ocrMessage.isEmpty()) {

                                                              responses.add(
                                                                  Map.of(
                                                                      "type",
                                                                      "text",
                                                                      "content",
                                                                      ocrMessage + "\n\n" + text));
                                                            } else {
                                                              responses.add(
                                                                  Map.of(
                                                                      "type", "text", "content",
                                                                      text));
                                                            }
                                                          }
                                                        });
                                              }
                                            }));
                      },
                      error -> { // onError
                        logger.error(
                            "Error during agent execution for session {}", sessionId, error);
                        future.completeExceptionally(error);
                      });
            },
            future::completeExceptionally);

    logger.info("doChat() - returning response future.");
    return future;
  }

  /*
   * Common method to handle chat logic with or without file uploads
   */
  private CompletableFuture<List<Map<String, String>>> doVoiceOver(
      String userId,
      String message,
      String sessionId,
      MultipartFile[] documents,
      Map<String, Object> userMap) {

    logger.info("doVoiceOver() - processing message: " + message + " for sessionId: " + sessionId);

    CompletableFuture<List<Map<String, String>>> future = new CompletableFuture<>();
    List<Map<String, String>> responses = new ArrayList<>();

    // Get session or create a new one if it doesn't exist.
    // We create a config to ensure all previous events are loaded with the session.
    GetSessionConfig config = GetSessionConfig.builder().build();

    firestoreDatabaseRunner
        .sessionService()
        .getSession(APP_NAME, userId, sessionId, Optional.of(config))
        .onErrorResumeNext(
            throwable -> {
              if (throwable instanceof SessionNotFoundException) {
                logger.warn("Session not found for {}, creating a new one.", sessionId);
                ConcurrentMap<String, Object> sessionData = new ConcurrentHashMap<>();
                sessionData.put(Constants.USER_ID_KEY, userId);
                sessionData.put(
                    Constants.GIVEN_NAME_KEY,
                    userMap.getOrDefault(Constants.GIVEN_NAME_KEY, "unknown-first-name"));
                sessionData.put(
                    Constants.SIR_NAME_KEY,
                    userMap.getOrDefault(Constants.SIR_NAME_KEY, "unknown-last-name"));
                // add a condition check first to make sure email exist and then add to the
                // session
                if (userMap.containsKey(Constants.EMAIL_KEY)) {
                  logger.debug("email " + userMap.get(Constants.EMAIL_KEY));
                  sessionData.put(Constants.EMAIL_KEY, userMap.get(Constants.EMAIL_KEY));
                }
                return firestoreDatabaseRunner
                    .sessionService()
                    .createSession(APP_NAME, userId, sessionData, sessionId)
                    .toMaybe();
              } else {
                return Maybe.error(throwable);
              }
            })
        .toSingle()
        .subscribe(
            session -> {
              logger.info("Using session with ID: " + session.id());

              // Log the conversation history being sent to the agent
              logger.info("--- Conversation History (Session: {}) ---", session.id());
              session
                  .events()
                  .forEach(
                      event -> {
                        String authorForDisplay =
                            event.author().equalsIgnoreCase(session.userId())
                                ? event.author()
                                : "model";
                        logger.info("[{}] {}", authorForDisplay, event.stringifyContent());
                      });
              logger.info("------------------------------------------");

              // Construct the message with file parts if they exist.
              // This now returns both the enriched message and the content for the agent.
              Map<String, Object> constructedMessage =
                  constructUserMessage(message, documents, session.id());
              Content finalUserMessage = (Content) constructedMessage.get("content");
              String ocrMessage = (String) constructedMessage.get("ocrResponse");

              logger.info("doChat() - sending message to the agent...");

              firestoreDatabaseRunner
                  .runAsync(session, finalUserMessage, RunConfig.builder().build())
                  .doOnComplete(
                      () -> {
                        logger.info(
                            "doChat() - end - for session id is {} , and user id is {}.",
                            sessionId,
                            userId);
                        future.complete(responses);
                      })
                  .subscribe(
                      event -> { // onNext
                        logger.info("Event: " + event.stringifyContent());
                        event
                            .content()
                            .ifPresent(
                                content ->
                                    content
                                        .parts()
                                        .ifPresent(
                                            parts -> {
                                              // Do not add our specific error messages to the
                                              // response list or history.
                                              String responseText =
                                                  parts.stream()
                                                      .findFirst()
                                                      .flatMap(Part::text)
                                                      .orElse("");
                                              if (responseText.contains(
                                                  "The model did not return a response")) {
                                                return; // Skip this event
                                              }
                                              for (Part part : parts) {
                                                part.text()
                                                    .ifPresent(
                                                        text -> {
                                                          if (!text.isEmpty()) {
                                                            logger.info(
                                                                "Agent response part: " + text);
                                                            if (ocrMessage != null
                                                                && !ocrMessage.isEmpty()) {

                                                              responses.add(
                                                                  Map.of(
                                                                      "type",
                                                                      "text",
                                                                      "content",
                                                                      ocrMessage + "\n\n" + text));
                                                            } else {
                                                              responses.add(
                                                                  Map.of(
                                                                      "type", "text", "content",
                                                                      text));
                                                            }
                                                          }
                                                        });
                                              }
                                            }));
                      },
                      error -> { // onError
                        logger.error(
                            "Error during agent execution for session {}", sessionId, error);
                        future.completeExceptionally(error);
                      });
            },
            future::completeExceptionally);

    logger.info("doVoiceOver() - returning response future.");
    return future;
  }

  private Map<String, Object> constructUserMessage(
      String message, MultipartFile[] documents, String sessionId) {
    List<Part> messageParts = new ArrayList<>();
    StringBuilder enrichedMessage = new StringBuilder(message);
    StringBuilder ocrResponse = new StringBuilder();
    boolean wasEnriched = false;

    if (documents != null && documents.length > 0) {
      logger.info("doChat() - processing " + documents.length + " uploaded documents.");
      for (MultipartFile file : documents) {
        try {
          byte[] fileBytes = file.getBytes();
          // Call the OCR service directly from the controller
          Map<String, String> ocrDetails = OcrCall.callOcrService(fileBytes);
          if ("success".equals(ocrDetails.get("status"))) {
            // Enrich the user's message with the extracted text
            enrichedMessage.append("\n--- user provided information ---");
            ocrDetails.forEach(
                (key, value) -> {
                  if (!"status".equals(key) && value != null && !value.isEmpty()) {
                    enrichedMessage.append(String.format("\n%s: %s", key, value));
                    ocrResponse.append(String.format("\n%s: %s", key, value));
                  }
                });
            wasEnriched = true;
          }
        } catch (Exception e) {
          logger.error("Error processing uploaded file: " + file.getOriginalFilename(), e);
        }
      }
    }
    messageParts.add(Part.fromText(enrichedMessage.toString()));
    logger.info("doChat() - constructed message with " + messageParts.size() + " parts.");
    Content content = Content.builder().role("user").parts(messageParts).build();

    // Return both the content for the agent and the enriched message string.
    // If no OCR was done, the enriched message is just the original message.
    return Map.of("content", content, "ocrResponse", wasEnriched ? ocrResponse.toString() : "");
  }

  /**
   * @param userId
   * @param message
   * @param sessionId
   * @param documents
   * @return
   */
  private CompletableFuture<Map<String, Object>> doChatForFormPost(
      String userId,
      String message,
      String sessionId,
      byte[] documents,
      Map<String, Object> userMap) {
    if (documents != null) {
      // Assuming the 'documents' string is a base64 data URL
      try {

        // Create a mock MultipartFile to pass to the existing doChat logic
        MultipartFile multipartFile =
            new MockMultipartFile("file", "payment-card.jpg", "image/jpeg", documents);
        return doChat(userId, message, sessionId, new MultipartFile[] {multipartFile}, userMap);
      } catch (Exception e) {
        logger.error("Error decoding base64 document string", e);
        return doChat(userId, message, sessionId, (MultipartFile[]) null, userMap);
      }
    } else {
      return doChat(userId, message, sessionId, (MultipartFile[]) null, userMap);
    }
  }
}
