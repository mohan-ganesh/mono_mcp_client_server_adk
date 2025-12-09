package com.example.garvik.runner;

import com.google.adk.memory.BaseMemoryService;
import com.google.adk.memory.MemoryEntry;
import com.google.adk.memory.SearchMemoryResponse;
import com.google.adk.sessions.Session;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FirestoreMemoryService implements BaseMemoryService {

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(FirestoreMemoryService.class);
  private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z]+");
  private static final String EVENTS_SUBCOLLECTION_NAME = "user-events";
  private static final Set<String> STOP_WORDS =
      new HashSet<>(
          Arrays.asList(
              "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "if", "in", "into",
              "is", "it", "i", "no", "not", "of", "on", "or", "such", "that", "the", "their",
              "then", "there", "these", "they", "this", "to", "was", "will", "with", "what",
              "where", "when", "why", "how", "help", "need", "like", "make", "got", "would",
              "could", "should"));

  private final Firestore db;

  public FirestoreMemoryService(Firestore db) {
    this.db = db;
  }

  @Override
  public Completable addSessionToMemory(Session session) {
    // No-op. Keywords are indexed when events are appended in
    // FirestoreSessionService.
    return Completable.complete();
  }

  @Override
  public Single<SearchMemoryResponse> searchMemory(String appName, String userId, String query) {
    return Single.fromCallable(
        () -> {
          Objects.requireNonNull(appName, "appName cannot be null");
          Objects.requireNonNull(userId, "userId cannot be null");
          Objects.requireNonNull(query, "query cannot be null");

          Set<String> queryKeywords = extractKeywords(query);

          if (queryKeywords.isEmpty()) {
            return SearchMemoryResponse.builder().build();
          }

          List<String> queryKeywordsList = new ArrayList<>(queryKeywords);
          List<List<String>> chunks = Lists.partition(queryKeywordsList, 10);

          List<ApiFuture<List<QueryDocumentSnapshot>>> futures = new ArrayList<>();
          for (List<String> chunk : chunks) {
            Query eventsQuery =
                db.collectionGroup(EVENTS_SUBCOLLECTION_NAME)
                    .whereEqualTo("appName", appName)
                    .whereEqualTo("userId", userId)
                    .whereArrayContainsAny("keywords", chunk);
            futures.add(
                ApiFutures.transform(
                    eventsQuery.get(),
                    com.google.cloud.firestore.QuerySnapshot::getDocuments,
                    Executors.newSingleThreadExecutor()));
          }

          Set<String> seenEventIds = new HashSet<>();
          List<MemoryEntry> matchingMemories = new ArrayList<>();

          for (QueryDocumentSnapshot eventDoc :
              ApiFutures.allAsList(futures).get().stream()
                  .flatMap(List::stream)
                  .collect(Collectors.toList())) {
            if (seenEventIds.add(eventDoc.getId())) {
              MemoryEntry entry = memoryEntryFromDoc(eventDoc);
              if (entry != null) {
                matchingMemories.add(entry);
              }
            }
          }

          return SearchMemoryResponse.builder()
              .setMemories(ImmutableList.copyOf(matchingMemories))
              .build();
        });
  }

  private Set<String> extractKeywords(String text) {
    Set<String> keywords = new HashSet<>();
    if (text != null && !text.isEmpty()) {
      Matcher matcher = WORD_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
      while (matcher.find()) {
        String word = matcher.group();
        if (!STOP_WORDS.contains(word)) {
          keywords.add(word);
        }
      }
    }
    return keywords;
  }

  @SuppressWarnings("unchecked")
  private MemoryEntry memoryEntryFromDoc(QueryDocumentSnapshot doc) {
    Map<String, Object> data = doc.getData();
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

      List<Map<String, Object>> partsList = (List<Map<String, Object>>) contentMap.get("parts");
      List<Part> parts = new ArrayList<>();
      if (partsList != null) {
        for (Map<String, Object> partMap : partsList) {
          if (partMap.containsKey("text")) {
            parts.add(Part.fromText((String) partMap.get("text")));
          }
        }
      }

      return MemoryEntry.builder()
          .author(author)
          .content(Content.fromParts(parts.toArray(new Part[0])))
          .timestamp(timestampStr)
          .build();
    } catch (Exception e) {
      logger.error("Failed to parse memory entry from Firestore data: " + data, e);
      return null;
    }
  }
}
