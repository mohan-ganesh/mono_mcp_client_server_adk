package com.example.garvik.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserPreferences {

  private static final Logger logger = LoggerFactory.getLogger(UserPreferences.class);
  private static final String USER_ROLES_COLLECTION = "user_roles";

  private final Firestore db;

  // In-memory cache for user preferences
  private final Map<String, Optional<Map<String, Object>>> preferencesCache =
      new ConcurrentHashMap<>();

  public UserPreferences(Firestore db) {
    this.db = db;
  }

  /**
   * Fetches user preferences from the 'user_roles' collection in Firestore. The document ID within
   * this collection is expected to be the user's ID.
   *
   * @param userId The ID of the user whose preferences are to be fetched.
   * @return An Optional containing a map of the user's preferences if the document exists,
   *     otherwise an empty Optional.
   */
  public Optional<Map<String, Object>> getUserPreferences(String userId) {
    // First, try to get the preferences from the cache
    if (preferencesCache.containsKey(userId)) {
      logger.info("Found preferences for user {} in cache.", userId);
      return preferencesCache.get(userId);
    }

    // If not in cache, fetch from Firestore
    logger.info("Fetching preferences for userId: {} from Firestore.", userId);
    ApiFuture<DocumentSnapshot> future =
        db.collection(USER_ROLES_COLLECTION).document(userId).get();

    try {
      DocumentSnapshot document = future.get();
      Optional<Map<String, Object>> preferences;
      if (document.exists()) {
        logger.info("Found preferences for user {}. Caching result.", userId);
        preferences = Optional.ofNullable(document.getData());
      } else {
        logger.warn("No preferences document found for userId: {}. Caching empty result.", userId);
        preferences = Optional.empty();
      }
      // Store the result (even if empty) in the cache to prevent future lookups for non-existent
      // users
      preferencesCache.put(userId, preferences);
      return preferences;
    } catch (InterruptedException | ExecutionException e) {
      logger.error("Error fetching user preferences for userId: " + userId, e);
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt(); // Restore the interrupted status
      }
      return Optional.empty();
    }
  }

  /**
   * @param userId
   * @return
   */
  public boolean invlidateUserPreferences(String userId) {
    logger.info("Invalidating preferences for userId: {}.", userId);
    boolean clear = false;
    if (preferencesCache.containsKey(userId)) {
      logger.info("Invalidating preferences for userId: {} from cache.", userId);
      preferencesCache.remove(userId);
      clear = true;
    } else {
      logger.warn("Nothing is in the cache for userId: {}.", userId);
    }

    return clear;
  }
}
