package com.example.garvik.service;

import com.example.garvik.config.Constants;
import com.google.api.gax.rpc.ApiException;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleSttService {

  private static final Logger logger = LoggerFactory.getLogger(GoogleSttService.class);

  private SpeechClient speechClient;

  @Value("${speech.recognition.model:latest_long}")
  private String defaultSpeechRecognitionModel;

  private Map<String, RecognitionConfig> recognitionConfigs;

  @PostConstruct
  public void init() {
    try {
      speechClient = SpeechClient.create();
      logger.info("-------------");
      logger.info("init() -- Google SpeechClient initialized successfully.");

      // Pre-build recognition configurations for supported models
      recognitionConfigs = new HashMap<>();
      EnumSet.of(
              Constants.SpeechModel.LATEST_LONG,
              Constants.SpeechModel.LATEST_SHORT,
              Constants.SpeechModel.TELEPHONY,
              Constants.SpeechModel.MEDICAL_DICTATION)
          .forEach(
              model -> {
                RecognitionConfig config =
                    RecognitionConfig.newBuilder()
                        .setEncoding(RecognitionConfig.AudioEncoding.WEBM_OPUS)
                        .setModel(model.getModelName())
                        .setLanguageCode("en-US")
                        .build();
                recognitionConfigs.put(model.getModelName(), config);
              });
      logger.info(
          "Pre-built speech recognition configs for models: {}", recognitionConfigs.keySet());
      logger.info("-------------");
    } catch (IOException e) {
      logger.error("Failed to initialize Google SpeechClient", e);
      throw new RuntimeException("Could not initialize Google SpeechClient", e);
    }
  }

  /**
   * @param audioData
   * @param userVoice
   * @return
   * @throws IOException
   */
  public String transcribe(byte[] audioData, Optional<Map<String, Object>> userVoice)
      throws IOException {
    ByteString audioBytes = ByteString.copyFrom(audioData);

    String modelToUse = defaultSpeechRecognitionModel;
    if (userVoice != null
        && userVoice.isPresent()
        && userVoice.get().get(Constants.inputSpeechModel) != null) {
      modelToUse = (String) userVoice.get().get(Constants.inputSpeechModel);
    }

    // Fetch the pre-built config, or use the default if the user's choice is not supported.
    RecognitionConfig config =
        recognitionConfigs.getOrDefault(
            modelToUse, recognitionConfigs.get(defaultSpeechRecognitionModel));
    logger.info("Using speech recognition model: {}", config.getModel());

    RecognitionAudio audio = RecognitionAudio.newBuilder().setContent(audioBytes).build();

    try {
      RecognizeResponse response = speechClient.recognize(config, audio);

      // Check if the response has any results before trying to access them
      if (response.getResultsCount() > 0) {
        SpeechRecognitionResult result = response.getResultsList().get(0);
        if (result.getAlternativesCount() > 0) {
          SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
          String transcript = alternative.getTranscript();
          // Return transcript only if it's not empty
          return transcript.isEmpty() ? "[No speech detected]" : transcript;
        }
      }

      // This case handles when the API returns a response but with no results.
      logger.warn("Google STT API returned a response with no transcription results.");
      return "[No speech detected]";

    } catch (ApiException e) {
      // This catches API-level errors, like invalid arguments (e.g., empty audio).
      // The AsyncTaskException you saw is often a wrapper for this.
      logger.error(
          "Google STT API call failed with status: {} and message: {}",
          e.getStatusCode().getCode(),
          e.getMessage());
      return "[Speech recognition failed]";
    }
  }
}
