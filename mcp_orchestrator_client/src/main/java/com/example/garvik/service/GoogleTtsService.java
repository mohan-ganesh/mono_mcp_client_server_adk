package com.example.garvik.service;

import com.example.garvik.config.Constants;
import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GoogleTtsService {
  private static final Logger logger = LoggerFactory.getLogger(GoogleTtsService.class);

  // Configurable properties for voice selection and prosody

  // en-US-Wavenet-D. This is a high-quality male WaveNet voice. You can
  // experiment with others:
  // en-US-Wavenet-C (female, natural)
  // en-US-Wavenet-E (female, natural, slightly higher pitch)
  // en-US-Wavenet-F (male, natural, slightly lower pitch)
  @Value("${gcp.tts.voice.name:en-US-Wavenet-C}") // Default to a natural male WaveNet voice
  private String defaultVoiceName;

  @Value(
      "${gcp.tts.speaking.rate:1.0}") // Slightly slower than default (1.0) for more natural pacing
  private double defaultSpeakingRate;

  @Value("${gcp.tts.pitch:-1.0}") // Slightly lower pitch for a more mature tone
  private double defaultPitch;

  private TextToSpeechClient textToSpeechClient;

  private Map<String, VoiceSelectionParams> voiceSelectionParams;

  @PostConstruct
  public void init() {
    try {
      // Initialize the client using Application Default Credentials
      textToSpeechClient = TextToSpeechClient.create();
      logger.info(
          "Google TextToSpeechClient initialized successfully using Application Default"
              + " Credentials.");

      // Pre-build voice selection configurations for supported voices
      voiceSelectionParams = new HashMap<>();
      EnumSet.allOf(Constants.TtsVoice.class)
          .forEach(
              voice -> {
                VoiceSelectionParams params =
                    VoiceSelectionParams.newBuilder()
                        .setLanguageCode("en-US")
                        .setName(voice.getVoiceName())
                        .setSsmlGender(voice.getGender())
                        .build();
                voiceSelectionParams.put(voice.getVoiceName(), params);
              });
      logger.info(
          "Pre-built TTS voice configurations for voices: {}", voiceSelectionParams.keySet());

    } catch (IOException e) {
      logger.error("Failed to initialize Google TextToSpeechClient: " + e.getMessage());

      throw new RuntimeException("Could not initialize Google TextToSpeechClient", e);
    }
  }

  @PreDestroy
  public void cleanup() {
    if (textToSpeechClient != null) {
      textToSpeechClient.close();
      logger.info("Google TextToSpeechClient closed successfully.");
    } else {
      logger.warn("Google TextToSpeechClient did not close.");
    }
  }

  /**
   * Synthesizes speech from the given text using the specified voice.
   *
   * @param text The text to synthesize.
   * @return Base64 encoded audio content (MP3 format).
   * @throws IOException If an API call fails.
   */
  public String synthesizeTextToMp3Base64(String text, Optional<Map<String, Object>> userVoice)
      throws IOException {
    // Set the text input to be synthesized
    logger.info("Synthesizing text to speech: " + text);
    // Build the synthesis input
    SynthesisInput input = SynthesisInput.newBuilder().setText(text).build();

    String voiceToUse = defaultVoiceName;
    if (userVoice != null && userVoice.isPresent()) {

      if (userVoice.get().get(Constants.responseVoiceName) != null)
        voiceToUse = (String) userVoice.get().get(Constants.responseVoiceName);

      try {
        Object pitchValue = userVoice.get().get(Constants.responsePitch);
        if (pitchValue != null) {
          defaultPitch = Double.parseDouble(pitchValue.toString());
        }

        Object rateValue = userVoice.get().get(Constants.responseSpeakingRate);
        if (rateValue != null) {
          defaultSpeakingRate = Double.parseDouble(rateValue.toString());
        }
      } catch (NumberFormatException e) {
        logger.error(
            "Could not parse pitch or speaking rate from user preferences. Using default values.",
            e);
        // Defaults will be used if parsing fails.
      }
    }

    // Fetch the pre-built voice config, or use the default if the user's choice is not supported.
    VoiceSelectionParams voice =
        voiceSelectionParams.getOrDefault(voiceToUse, voiceSelectionParams.get(defaultVoiceName));
    logger.info(
        "Using TTS voice: {}, rate: {}, pitch: {}",
        voice.getName(),
        defaultSpeakingRate,
        defaultPitch);

    // Select the type of audio file you want returned
    AudioConfig audioConfig =
        AudioConfig.newBuilder()
            .setAudioEncoding(AudioEncoding.MP3)
            .setSpeakingRate(defaultSpeakingRate) // Apply configured speaking rate
            .setPitch(defaultPitch) // Apply configured pitch
            .build();

    // Perform the text-to-speech request
    SynthesizeSpeechResponse response =
        textToSpeechClient.synthesizeSpeech(input, voice, audioConfig);

    // Get the audio contents as bytes
    ByteString audioContents = response.getAudioContent();

    logger.info("Text-to-speech synthesis completed successfully.");
    // Encode to Base64
    return java.util.Base64.getEncoder().encodeToString(audioContents.toByteArray());
  }

  /**
   * This method would be used to interact with the actual Gemini API if there was a direct
   * Text-to-Speech endpoint for the Gemini model itself. Currently, 'gemini-2.5-pro-preview-tts' is
   * more conceptual for models offering TTS, and Google Cloud Text-to-Speech API is the way to get
   * high-quality TTS. We are essentially using the Google Cloud TTS service *as if* it were the TTS
   * part of Gemini.
   *
   * <p>In a future where 'gemini-2.5-pro-preview-tts' directly exposes a different TTS API, this is
   * where you'd put that specific client integration.
   *
   * @param userVoice
   */
  public String generateSpeechWithGemini(String text, Optional<Map<String, Object>> userVoice)
      throws IOException {
    logger.info("Using Google Cloud TTS as 'Gemini' TTS for text: " + text);
    return synthesizeTextToMp3Base64(text, userVoice);
  }
}
