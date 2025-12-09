package com.example.garvik.service;

import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.example.garvik.pojo.voice.AudioMessage;

@Service
public class AudioProcessingService {

  private static final Logger logger = LoggerFactory.getLogger(AudioProcessingService.class);

  @Async
  public void processAudio(AudioMessage audioMessage) {
    logger.info("Starting asynchronous audio processing...");
    // Decode the Base64 data
    byte[] audioBytes = Base64.getDecoder().decode(audioMessage.getAudioData());
    logger.info("Decoded audio data. Size: {} bytes.", audioBytes.length);
  }
}
