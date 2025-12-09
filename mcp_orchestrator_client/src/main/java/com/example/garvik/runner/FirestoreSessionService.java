package com.example.garvik.runner;

import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.ListEventsResponse;
import com.google.adk.sessions.ListSessionsResponse;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.SessionNotFoundException;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FirestoreSessionService implements BaseSessionService {
  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(FirestoreSessionService.class);
  private final Firestore db;
  private static final String ROOT_COLLECTION_NAME = "adk-sessions";
  private static final String EVENTS_SUBCOLLECTION_NAME = "user-events";
  private static final String APP_STATE_COLLECTION = "app_state";
  private static final String USER_STATE_COLLECTION = "user_state";

  private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z]+");
  private static final Set<String> STOP_WORDS =
      new HashSet<>(
          Arrays.asList(
              "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into",
              "is", "it", "i", "no", "not", "of", "on", "or", "such", "that", "the", "their",
              "then", "there", "these", "they", "this", "to", "was", "will", "with", "what",
              "where", "when", "why", "how", "help", "need", "like", "make", "got", "would",
              "could", "should"));

  public FirestoreSessionService(Firestore db) {
    this.db = db;
  }

  private CollectionReference getSessionsCollection(String userId) {
    return db.collection(ROOT_COLLECTION_NAME).document(userId).collection("sessions");
  }

  @Override
  public Single<Session> createSession(
      String appName, String userId, ConcurrentMap<String, Object> state, String sessionId) {
    return Single.fromCallable(
        () -> {
          Objects.requireNonNull(appName, "appName cannot be null");
          Objects.requireNonNull(userId, "userId cannot be null");

          String resolvedSessionId =
              Optional.ofNullable(sessionId)
                  .map(String::trim)
                  .filter(s -> !s.isEmpty())
                  .orElseGet(() -> UUID.randomUUID().toString());

          ConcurrentMap<String, Object> initialState =
              (state == null) ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(state);
          logger.info(
              "Creating session for userId: {} with sessionId: {} and initial state: {}",
              userId,
              resolvedSessionId,
              initialState);
          List<Event> initialEvents = new ArrayList<>();
          Instant now = Instant.now();
          Session newSession =
              Session.builder(resolvedSessionId)
                  .appName(appName)
                  .userId(userId)
                  .state(initialState)
                  .events(initialEvents)
                  .lastUpdateTime(now)
                  .build();

          // Convert Session to a Map for Firestore
          Map<String, Object> sessionData = new HashMap<>();
          sessionData.put("id", newSession.id());
          sessionData.put("appName", newSession.appName());
          sessionData.put("userId", newSession.userId());
          sessionData.put("updateTime", newSession.lastUpdateTime().toString());
          sessionData.put("state", newSession.state());

          // Asynchronously write to Firestore and wait for the result
          ApiFuture<WriteResult> future =
              getSessionsCollection(userId).document(resolvedSessionId).set(sessionData);
          future.get(); // Block until the write is complete

          return newSession;
        });
  }

  @Override
  public Maybe<Session> getSession(
      String appName, String userId, String sessionId, Optional<GetSessionConfig> configOpt) {
    Objects.requireNonNull(appName, "appName cannot be null");
    Objects.requireNonNull(userId, "userId cannot be null");
    Objects.requireNonNull(sessionId, "sessionId cannot be null");
    Objects.requireNonNull(configOpt, "configOpt cannot be null");

    logger.info("Getting session for userId: {} with sessionId: {}", userId, sessionId);
    ApiFuture<DocumentSnapshot> future = getSessionsCollection(userId).document(sessionId).get();

    return ApiFutureUtils.toMaybe(future)
        .flatMap(
            document -> {
              if (!document.exists()) {
                logger.warn("Session not found for sessionId: {}", sessionId);
                return Maybe.error(new SessionNotFoundException("Session not found: " + sessionId));
              }

              Map<String, Object> data = document.getData();
              if (data == null) {
                logger.warn("Session data is null for sessionId: {}", sessionId);
                return Maybe.empty();
              }

              // Fetch events based on config
              GetSessionConfig config = configOpt.orElse(GetSessionConfig.builder().build());
              CollectionReference eventsCollection =
                  document.getReference().collection(EVENTS_SUBCOLLECTION_NAME);
              Query eventsQuery = eventsCollection.orderBy("timestamp");

              if (config.afterTimestamp().isPresent()) {
                eventsQuery =
                    eventsQuery.whereGreaterThan(
                        "timestamp", config.afterTimestamp().get().toString());
              }

              if (config.numRecentEvents().isPresent()) {
                eventsQuery = eventsQuery.limitToLast(config.numRecentEvents().get());
              }

              ApiFuture<List<QueryDocumentSnapshot>> eventsFuture =
                  ApiFutures.transform(
                      eventsQuery.get(),
                      com.google.cloud.firestore.QuerySnapshot::getDocuments,
                      Executors.newSingleThreadExecutor());

              return ApiFutureUtils.toSingle(eventsFuture)
                  .map(
                      eventDocs -> {
                        List<Event> events = new ArrayList<>();
                        for (DocumentSnapshot eventDoc : eventDocs) {
                          Event event = eventFromMap(eventDoc.getData(), userId);
                          if (event != null) {
                            events.add(event);
                          }
                        }
                        return events;
                      })
                  .map(
                      events -> {
                        ConcurrentMap<String, Object> state =
                            new ConcurrentHashMap<>((Map<String, Object>) data.get("state"));
                        return Session.builder((String) data.get("id"))
                            .appName((String) data.get("appName"))
                            .userId((String) data.get("userId"))
                            .lastUpdateTime(Instant.parse((String) data.get("updateTime")))
                            .state(state)
                            .events(events)
                            .build();
                      })
                  .toMaybe();
            });
  }

  /**
   * Reconstructs an Event object from a Map retrieved from Firestore.
   *
   * @param data The map representation of the event.
   * @return An Event object, or null if the data is malformed.
   */
  @SuppressWarnings("unchecked")
  private Event eventFromMap(Map<String, Object> data, String sessionUserId) {
    if (data == null) {
      return null;
    }
    try {
      String author = (String) data.get("author");
      String timestampStr = (String) data.get("timestamp");
      Map<String, Object> contentMap = (Map<String, Object>) data.get("content");

      if (author == null || timestampStr == null || contentMap == null) {
        logger.warn("Skipping malformed event data: {}", data);
        return null;
      }

      Instant timestamp = Instant.parse(timestampStr);

      // Reconstruct Content object
      List<Map<String, Object>> partsList = (List<Map<String, Object>>) contentMap.get("parts");
      List<Part> parts = new ArrayList<>();
      if (partsList != null) {
        for (Map<String, Object> partMap : partsList) {
          Part part = null;
          if (partMap.containsKey("text")) {
            part = Part.fromText((String) partMap.get("text"));
          } else if (partMap.containsKey("functionCall")) {
            part = functionCallPartFromMap((Map<String, Object>) partMap.get("functionCall"));
          } else if (partMap.containsKey("functionResponse")) {
            part =
                functionResponsePartFromMap((Map<String, Object>) partMap.get("functionResponse"));
          } else if (partMap.containsKey("fileData")) {
            // Reconstruct file data part from URI
            part = fileDataPartFromMap((Map<String, Object>) partMap.get("fileData"));
          }
          if (part != null) {
            parts.add(part);
          }
        }
      }

      // The role of the content should be 'user' or 'model'.
      // An agent's turn is 'model'. A user's turn is 'user'.
      // A special case is a function response, which is authored by the 'user'
      // but represents a response to a model's function call.
      String role;
      boolean hasFunctionResponse = parts.stream().anyMatch(p -> p.functionResponse().isPresent());

      if (hasFunctionResponse) {
        role = "user"; // Function responses are sent with the 'user' role.
      } else {
        // If the author is the user, the role is 'user'. Otherwise, it's 'model'.
        role = author.equalsIgnoreCase(sessionUserId) ? "user" : "model";
      }
      logger.debug("Reconstructed event role: {}", role);
      Content content = Content.builder().role(role).parts(parts).build();

      return Event.builder()
          .author(author)
          .content(content)
          .timestamp(timestamp.toEpochMilli())
          .build();
    } catch (Exception e) {
      logger.error("Failed to parse event from Firestore data: " + data, e);
      return null;
    }
  }

  /**
   * Constructs a FunctionCall Part from a map representation.
   *
   * @param fcMap The map containing the function call 'name' and 'args'.
   * @return A Part containing the FunctionCall.
   */
  private Part functionCallPartFromMap(Map<String, Object> fcMap) {
    if (fcMap == null) {
      return null;
    }
    String name = (String) fcMap.get("name");
    @SuppressWarnings("unchecked")
    Map<String, Object> args = (Map<String, Object>) fcMap.get("args");
    return Part.fromFunctionCall(name, args);
  }

  /**
   * Constructs a FunctionResponse Part from a map representation.
   *
   * @param frMap The map containing the function response 'name' and 'response'.
   * @return A Part containing the FunctionResponse.
   */
  private Part functionResponsePartFromMap(Map<String, Object> frMap) {
    if (frMap == null) {
      return null;
    }
    String name = (String) frMap.get("name");
    @SuppressWarnings("unchecked")
    Map<String, Object> response = (Map<String, Object>) frMap.get("response");
    return Part.fromFunctionResponse(name, response);
  }

  /**
   * Constructs a fileData Part from a map representation.
   *
   * @param fdMap The map containing the file data 'fileUri' and 'mimeType'.
   * @return A Part containing the file data.
   */
  private Part fileDataPartFromMap(Map<String, Object> fdMap) {
    if (fdMap == null) return null;
    String fileUri = (String) fdMap.get("fileUri");
    String mimeType = (String) fdMap.get("mimeType");
    return Part.fromUri(fileUri, mimeType);
  }

  private Map<String, Object> eventToMap(Session session, Event event) {
    Map<String, Object> data = new HashMap<>();
    // For user-generated events, the author should be the user's ID.
    // The ADK runner sets the author to "user" for the user's turn.
    if ("user".equalsIgnoreCase(event.author())) {
      data.put("author", session.userId());
    } else {
      data.put("author", event.author());
    }
    data.put("timestamp", Instant.ofEpochMilli(event.timestamp()).toString());
    data.put("appName", session.appName()); // Persist appName with the event

    Map<String, Object> contentData = new HashMap<>();
    List<Map<String, Object>> partsData = new ArrayList<>();
    Set<String> keywords = new HashSet<>();

    event
        .content()
        .flatMap(Content::parts)
        .ifPresent(
            parts -> {
              for (Part part : parts) {
                Map<String, Object> partData = new HashMap<>();
                part.text()
                    .ifPresent(
                        text -> {
                          partData.put("text", text);
                          // Extract keywords only if there is text
                          if (!text.isEmpty()) {

                            Matcher matcher = WORD_PATTERN.matcher(text);
                            while (matcher.find()) {
                              String word = matcher.group().toLowerCase(Locale.ROOT);
                              if (!STOP_WORDS.contains(word)) {
                                keywords.add(word);
                              }
                            }
                          }
                        });
                part.functionCall()
                    .ifPresent(
                        fc -> {
                          Map<String, Object> fcMap = new HashMap<>();
                          fc.name().ifPresent(name -> fcMap.put("name", name));
                          fc.args().ifPresent(args -> fcMap.put("args", args));
                          if (!fcMap.isEmpty()) {
                            partData.put("functionCall", fcMap);
                          }
                        });
                part.functionResponse()
                    .ifPresent(
                        fr -> {
                          Map<String, Object> frMap = new HashMap<>();
                          fr.name().ifPresent(name -> frMap.put("name", name));
                          fr.response().ifPresent(response -> frMap.put("response", response));
                          if (!frMap.isEmpty()) {
                            partData.put("functionResponse", frMap);
                          }
                        });
                part.fileData()
                    .ifPresent(
                        fd -> {
                          Map<String, Object> fdMap = new HashMap<>();
                          // When serializing, we assume the artifact service has already converted
                          // the
                          // bytes to a GCS URI.
                          fd.fileUri().ifPresent(uri -> fdMap.put("fileUri", uri));
                          fd.mimeType().ifPresent(mime -> fdMap.put("mimeType", mime));
                          if (!fdMap.isEmpty()) {
                            partData.put("fileData", fdMap);
                          }
                        });

                // Add other part types if necessary
                partsData.add(partData);
              }
            });

    logger.info("Serialized parts data before saving: {}", partsData);
    contentData.put("parts", partsData);
    data.put("content", contentData);
    if (!keywords.isEmpty()) {
      data.put("keywords", new ArrayList<>(keywords)); // Firestore works well with Lists
    }

    return data;
  }

  @Override
  public Single<ListSessionsResponse> listSessions(String appName, String userId) {
    return Single.fromCallable(
        () -> {
          Objects.requireNonNull(appName, "appName cannot be null");
          Objects.requireNonNull(userId, "userId cannot be null");

          logger.info("Listing sessions for userId: {}", userId);

          Query query = getSessionsCollection(userId).whereEqualTo("appName", appName);

          ApiFuture<List<QueryDocumentSnapshot>> querySnapshot =
              ApiFutures.transform(
                  query.get(), // Query is already scoped to the user
                  snapshot -> snapshot.getDocuments(),
                  Executors.newSingleThreadExecutor());

          List<Session> sessions = new ArrayList<>();
          for (DocumentSnapshot document : querySnapshot.get()) {
            Map<String, Object> data = document.getData();
            if (data != null) {
              // Create a session object with empty events and state, as per
              // InMemorySessionService
              Session session =
                  Session.builder((String) data.get("id"))
                      .appName((String) data.get("appName"))
                      .userId((String) data.get("userId"))
                      .lastUpdateTime(Instant.parse((String) data.get("updateTime")))
                      .state(new ConcurrentHashMap<>()) // Empty state
                      .events(new ArrayList<>()) // Empty events
                      .build();
              sessions.add(session);
            }
          }

          return ListSessionsResponse.builder().sessions(sessions).build();
        });
  }

  @Override
  public Completable deleteSession(String appName, String userId, String sessionId) {
    return Completable.fromAction(
        () -> {
          Objects.requireNonNull(appName, "appName cannot be null");
          Objects.requireNonNull(userId, "userId cannot be null");
          Objects.requireNonNull(sessionId, "sessionId cannot be null");

          logger.info("Deleting session for userId: {} with sessionId: {}", userId, sessionId);

          // Reference to the session document
          com.google.cloud.firestore.DocumentReference sessionRef =
              getSessionsCollection(userId).document(sessionId);

          // 1. Delete all events in the subcollection
          CollectionReference eventsRef = sessionRef.collection(EVENTS_SUBCOLLECTION_NAME);
          com.google.api.core.ApiFuture<com.google.cloud.firestore.QuerySnapshot> eventsQuery =
              eventsRef.get();
          List<QueryDocumentSnapshot> eventDocuments = eventsQuery.get().getDocuments();
          List<ApiFuture<WriteResult>> deleteFutures = new ArrayList<>();
          for (QueryDocumentSnapshot doc : eventDocuments) {
            deleteFutures.add(doc.getReference().delete());
          }
          // Wait for all event deletions to complete
          if (!deleteFutures.isEmpty()) {
            ApiFutures.allAsList(deleteFutures).get();
          }

          // 2. Delete the session document itself
          ApiFuture<WriteResult> deleteSessionFuture = sessionRef.delete();
          deleteSessionFuture.get(); // Block until deletion is complete

          logger.info("Successfully deleted session: {}", sessionId);
        });
  }

  @Override
  public Single<ListEventsResponse> listEvents(String appName, String userId, String sessionId) {
    return Single.fromCallable(
        () -> {
          Objects.requireNonNull(appName, "appName cannot be null");
          Objects.requireNonNull(userId, "userId cannot be null");
          Objects.requireNonNull(sessionId, "sessionId cannot be null");

          logger.info("Listing events for userId: {} with sessionId: {}", userId, sessionId);

          // First, check if the session document exists.
          ApiFuture<DocumentSnapshot> sessionFuture =
              getSessionsCollection(userId).document(sessionId).get();
          DocumentSnapshot sessionDocument = sessionFuture.get(); // Block for the result

          if (!sessionDocument.exists()) {
            logger.warn(
                "Session not found for sessionId: {}. Returning empty list of events.", sessionId);
            throw new SessionNotFoundException(appName + "," + userId + "," + sessionId);
          }

          // Session exists, now fetch the events.
          CollectionReference eventsCollection =
              sessionDocument.getReference().collection(EVENTS_SUBCOLLECTION_NAME);
          Query eventsQuery = eventsCollection.orderBy("timestamp");

          ApiFuture<List<QueryDocumentSnapshot>> eventsFuture =
              ApiFutures.transform(
                  eventsQuery.get(),
                  querySnapshot -> querySnapshot.getDocuments(),
                  Executors.newSingleThreadExecutor());

          List<Event> events = new ArrayList<>();
          for (DocumentSnapshot eventDoc : eventsFuture.get()) {
            Event event = eventFromMap(eventDoc.getData(), userId);
            if (event != null) {
              events.add(event);
            }
          }
          logger.info("Returning {} events for sessionId: {}", events.size(), sessionId);
          return ListEventsResponse.builder().events(events).build();
        });
  }

  @CanIgnoreReturnValue
  @Override
  public Single<Event> appendEvent(Session session, Event event) {
    return Single.fromCallable(
        () -> {
          Objects.requireNonNull(session, "session cannot be null");
          Objects.requireNonNull(session.appName(), "session.appName cannot be null");
          Objects.requireNonNull(session.userId(), "session.userId cannot be null");
          Objects.requireNonNull(session.id(), "session.id cannot be null");
          logger.info("appendEvent(S,E) - appending event to sessionId: {}", session.id());
          String appName = session.appName();
          String userId = session.userId();
          String sessionId = session.id();

          List<ApiFuture<WriteResult>> futures = new ArrayList<>();

          // --- Update User/App State ---
          EventActions actions = event.actions();
          if (actions != null) {
            Map<String, Object> stateDelta = actions.stateDelta();
            if (stateDelta != null && !stateDelta.isEmpty()) {
              Map<String, Object> appStateUpdates = new HashMap<>();
              Map<String, Object> userStateUpdates = new HashMap<>();

              stateDelta.forEach(
                  (key, value) -> {
                    if (key.startsWith("_app_")) {
                      appStateUpdates.put(key.substring("_app_".length()), value);
                    } else if (key.startsWith("_user_")) {
                      userStateUpdates.put(key.substring("_user_".length()), value);
                    } else {
                      // Regular session state
                      if (value == null) {
                        session.state().remove(key);
                      } else {
                        session.state().put(key, value);
                      }
                    }
                  });

              if (!appStateUpdates.isEmpty()) {
                futures.add(
                    db.collection(APP_STATE_COLLECTION)
                        .document(appName)
                        .set(appStateUpdates, com.google.cloud.firestore.SetOptions.merge()));
              }
              if (!userStateUpdates.isEmpty()) {
                futures.add(
                    db.collection(USER_STATE_COLLECTION)
                        .document(appName)
                        .collection("users")
                        .document(userId)
                        .set(userStateUpdates, com.google.cloud.firestore.SetOptions.merge()));
              }
            }
          }

          // This adds the event to the session's internal list.
          BaseSessionService.super.appendEvent(session, event);
          session.lastUpdateTime(getInstantFromEvent(event));

          // --- Persist event to Firestore ---
          Map<String, Object> eventData = eventToMap(session, event);
          eventData.put("userId", userId);
          eventData.put("appName", appName);
          // Generate a new ID for the event document
          String eventId =
              getSessionsCollection(userId)
                  .document(sessionId)
                  .collection(EVENTS_SUBCOLLECTION_NAME)
                  .document()
                  .getId();
          futures.add(
              getSessionsCollection(userId)
                  .document(sessionId)
                  .collection(EVENTS_SUBCOLLECTION_NAME)
                  .document(eventId)
                  .set(eventData));

          // --- Update the session document in Firestore ---
          Map<String, Object> sessionUpdates = new HashMap<>();
          sessionUpdates.put("updateTime", session.lastUpdateTime().toString());
          sessionUpdates.put("state", session.state());
          futures.add(getSessionsCollection(userId).document(sessionId).update(sessionUpdates));

          // Block and wait for all async Firestore operations to complete.
          // This makes the method effectively synchronous within the reactive chain,
          // ensuring the database is consistent before the runner proceeds.
          ApiFutures.allAsList(futures).get();

          logger.info("Event appended successfully to sessionId: {}", sessionId);
          logger.info("Returning appended event: {}", event.stringifyContent());

          return event;
        });
  }

  /** Converts an event's timestamp to an Instant. Adapt based on actual Event structure. */
  private Instant getInstantFromEvent(Event event) {
    // The event timestamp is in milliseconds since the epoch.
    return Instant.ofEpochMilli(event.timestamp());
  }
}
